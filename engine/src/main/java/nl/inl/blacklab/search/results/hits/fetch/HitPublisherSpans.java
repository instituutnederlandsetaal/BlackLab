package nl.inl.blacklab.search.results.hits.fetch;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.queries.spans.SpanWeight;
import org.apache.lucene.queries.spans.Spans;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.util.Bits;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.search.lucene.BLSpanWeight;
import nl.inl.blacklab.search.lucene.BLSpans;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.results.hits.EphemeralHit;
import nl.inl.blacklab.search.results.hits.Hits;
import nl.inl.blacklab.search.results.hits.HitsMutable;
import nl.inl.blacklab.search.results.stats.ResultsStatsPassive;
import nl.inl.util.ThreadAborter;

/** Publishes hits from a single Spans object. */
public class HitPublisherSpans implements HitPublisher {

    /** Set until initialized (needed to get spans); null afterwards */
    BLSpanWeight weight;

    /** Set until initialized (needed to construct own hitQueryContext); null afterwards */
    HitQueryContext sourceHitQueryContext;

    /** Has initialize() been called? */
    boolean isInitialized = false;

    /** Our own hit query context */
    HitQueryContext hitQueryContext;

    /** How many match infos there are in the hitQueryContext */
    private int numMatchInfos;

    /** Spans object we read our hits from. Will be lazy-initialized in initialize() because
     * it can take a long time to set up and hold a large amount of memory, even if you never
     * fetch a hit from it.
     * After we finish, this is set to null.
     */
    BLSpans spans;

    /** Allows us to more efficiently step to the next potentially matching document */
    private DocIdSetIterator twoPhaseApproximation;

    /** Allows us to check that doc matched by approximation is an actual match */
    private TwoPhaseIterator twoPhaseIt;

    /** Which documents in the segment have (not) been deleted? */
    private Bits liveDocs;

    /** Does spans points to a valid hit we haven't fetched yet? */
    private boolean hasPrefetchedHit = false;

    /** Have we fetched all hits? */
    AtomicBoolean isDone = new AtomicBoolean();

    /** The first hit index we haven't published to our subscribers yet. */
    private long unpublishedIndex = 0;

    /** The hits we've fetched so far. LOCKING. */
    private final HitsMutable alreadyPublishedHits;

    /** The hits in the current (not yet published) batch. NONLOCKING. */
    private final HitsMutable currentBatchOfHits;

    /** The previous hit we've looked at. */
    private EphemeralHit prevHit = new EphemeralHit();

    /** The hit we're currently looking at. */
    private EphemeralHit hit = new EphemeralHit();

    /** How many hits we've processed (length of alreadyPublishedHits and currentBatchOfHits together) */
    private long hitsProcessed = 0;

    /** How many distinct documents are in alreadyPublishedHits */
    int docsProcessed = 0;

    /** How many distinct documents are in the current (unpublished) batch */
    int docsProcessedThisBatch = 0;

    /** Hits we've only counted (so this excludes the processed hits) */
    long hitsCounted = 0;

    /** Docs we've only counted (so this excludes the processed hits) */
    int docsCounted = 0;

    /** Up to where we've reported the count to subscribers.
     * Next time we'll report counted - countedPrev. */
    long hitsCountedPrev = 0;

    /** Up to where we've reported the count to subscribers.
     * Next time we'll report counted - countedPrev. */
    int docsCountedPrev = 0;

    /** At what point should we stop storing hits and just count them? */
    final long maxToProcess;

    /** At what point should we give up even just counting the hits? */
    final long maxToCount;

    /** The hits processed/counted across HitPublisherSpans instances */
    private final ResultsStatsPassive hitsStats;

    /** The docs processed/counted across HitPublisherSpans instances */
    private final ResultsStatsPassive docsStats;

    /** Where our fetch thread should run. */
    private final ExecutorService executorService;

    /** If set and not isDone(), the thread fetching hits is running. */
    private Future<?> fetchThread;

    /** Our subscribers, that we will publish our hits to. */
    HitSubscribers subscribers;

    /** Field, match info defs and segment. */
    private final Hits.HitsContext context;

    /** Will count down when all hits have been found. Used by getStatic(). */
    private final CountDownLatch allHitsFound = new CountDownLatch(1);

    /** If set to true, we need to collect all hits. Ignore subscriber's ideas about pausing. */
    private final AtomicBoolean needAllHits = new AtomicBoolean(false);

    /** Lazy Hits interface to a single Spans object. */
    public HitPublisherSpans(LeafReaderContext lrc, BLSpanWeight weight, HitQueryContext sourceHitQueryContext,
            ExecutorService executorService, ResultsStatsPassive hitsStats, ResultsStatsPassive docsStats) {
        this.weight = weight;
        this.sourceHitQueryContext = sourceHitQueryContext;
        this.spans = null;
        this.executorService = executorService;
        this.hitsStats = hitsStats;
        maxToProcess = hitsStats.getMaxHitsToProcess();
        maxToCount = hitsStats.getMaxHitsToCount();
        this.docsStats = docsStats;
        this.context = new Hits.HitsContext(sourceHitQueryContext.getField(), sourceHitQueryContext.getMatchInfoDefs(), lrc);
        alreadyPublishedHits = HitsMutable.create(context, -1, true, true);
        currentBatchOfHits = HitsMutable.create(context, -1, true, false);

        subscribers = new HitSubscribers(sub -> {
            // Send all hits so far to the new subscriber
            sub.start(lrc, alreadyPublishedHits);
            if (!alreadyPublishedHits.isEmpty()) {
                // This method is called when the batch has already been added to alreadyPublishedHits,
                // but not yet reported to subscribers. Take this into account.
                long howManyActuallyPublished = alreadyPublishedHits.size() - currentBatchOfHits.size();
                sub.hits(lrc, alreadyPublishedHits, 0, howManyActuallyPublished, docsProcessed, 0);
            }
            if (hitsCounted > 0)
                sub.counted(hitsCounted, docsCounted);
            if (isDone.get()) {
                sub.flush(lrc, alreadyPublishedHits);
                sub.done(lrc);
            }
        });
    }

    public Hits.HitsContext context() {
        return context;
    }

    /**
     * Will retrieve its own Spans object on when it's ran.
     * <p>
     * This will self-initialize (meaning its Spans object and HitQueryContext are set). This is done
     * because HitFetcherQuerySegments can hold a lot of memory and time to set up and only a few are active at a time.
     * <p>
     * All HitFetcherQuerySegments share an instance of MatchInfoDefs (via the hit query context, of which each
     * HitFetcherQuerySegment gets a personalized copy, but with the same shared MatchInfoDefs instance).
     * <p>
     * HitFetcherQuerySegments will register their match infos with the MatchInfoDefs instance. Often the first
     * HitFetcherQuerySegment will register all match infos, but sometimes the first HitFetcherQuerySegment only
     * matches some match infos, and subsequent HitFetcherQuerySegments will register additional match infos. This is
     * dealt with later (when merging two matchInfo[] arrays of different length).
     * <p>
     */
    private void initialize() {
        isInitialized = true;
        try {
            LeafReaderContext lrc = context().leafReaderContext();
            BLSpans spansForWeight = this.weight.getSpans(lrc,
                    SpanWeight.Postings.OFFSETS);
            this.weight = null;
            if (spansForWeight == null) { // This is normal, sometimes a section of the index does not contain hits.
                setDone();
                return;
            }
            // If the resulting spans are not known to be sorted and unique, ensure that now.
            // TODO: do we unique twice???
            this.spans = BLSpans.ensureSortedUnique(spansForWeight);

            // We use two-phase iteration which allows us to skip to matching documents quickly.
            // Determine two-phase iterator and approximation now (approximation will return documents
            // that may match; iterator can check if one actually does match).
            this.twoPhaseIt = spans.asTwoPhaseIterator();
            this.twoPhaseApproximation = twoPhaseIt == null ? spans : twoPhaseIt.approximation();

            // Get query context for this spans and register it with our query.
            // Then determine the number of match infos (query registers match infos with the context)
            hitQueryContext = this.sourceHitQueryContext.withSpans(this.spans);
            this.sourceHitQueryContext = null;
            this.spans.setHitQueryContext(hitQueryContext);
            this.numMatchInfos = hitQueryContext.numberOfMatchInfos();

            this.liveDocs = lrc.reader().getLiveDocs();
            if (!hasPrefetchedHit) {
                hasPrefetchedHit = advanceSpansToNextHit();
            }
            prevHit.doc_ = -1;

        } catch (IOException e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    /**
     * Step through all hits in all documents in this spans object.
     *
     * @return true if the spans has been advanced to the next hit, false if out of hits.
     */
    private boolean advanceSpansToNextHit() throws IOException {
        // Make sure we've nexted at least once
        int doc = twoPhaseApproximation.docID();
        if (doc != -1) {
            // See if there's more matches in the current document
            int start = spans.nextStartPosition();
            if (start != Spans.NO_MORE_POSITIONS) {
                // Yes, we're at the next valid match.
                return true;
            }
        }

        // No more matches in this document. Find first match in next matching document.
        while (true) {
            assert twoPhaseApproximation.docID() != DocIdSetIterator.NO_MORE_DOCS;
            doc = twoPhaseApproximation.nextDoc();
            if (doc == DocIdSetIterator.NO_MORE_DOCS) {
                // We're done.
                spans = null;
                twoPhaseApproximation = null;
                twoPhaseIt = null;
                liveDocs = null;
                return false;
            }
            boolean actualMatch = twoPhaseIt == null || twoPhaseIt.matches();
            if (actualMatch && (liveDocs == null || liveDocs.get(doc))) {
                // Document matches. Put us at the first match.
                int startPos = spans.nextStartPosition();
                assert startPos >= 0;
                assert startPos != Spans.NO_MORE_POSITIONS;
                return true;
            }
        }
    }

    /**
     * Minimum number of hits to fetch in an ensureHitsRead() block.
     * <p>
     * This prevents locking again and again for a single hit when iterating.
     */
    public static final int FETCH_HITS_MIN = 100;

    @Override
    public synchronized void activate() {
        if (fetchThread != null && (fetchThread.isDone() || fetchThread.isCancelled())) {
            // Any error that occurred has already been reported to subscribers
            fetchThread = null;
        }
        if (!isDone.get() && fetchThread == null) {
            // We're not done, and the fetch thread is not running. Start it.
            fetchThread = executorService.submit(this::fetchAndPublishHits);
        }
    }

    /** This method is what the fetch thread runs.
     * <p>
     * It will fetch and publish hits until neither we nor our subscribers
     * want any more, or there are no more.
     */
    private void fetchAndPublishHits() {
        LeafReaderContext lrc = context().leafReaderContext();
        try {
            if (!isInitialized)
                initialize();
            if (isDone.get())
                return;
            boolean processingHits = hitsStats.processedSoFar() < maxToProcess;

            // Process hits in greater batches as we process more hits (should improve performance)
            long fetchHitsMin = Math.max(FETCH_HITS_MIN, hitsStats.processedSoFar() / 100);

            while (true) {
                // Are we done?
                if (!hasPrefetchedHit) {
                    setDone();
                    break;
                }

                // Get hit
                assert spans.docID() != DocIdSetIterator.NO_MORE_DOCS;
                assert spans.startPosition() != Spans.NO_MORE_POSITIONS;
                assert spans.endPosition() != Spans.NO_MORE_POSITIONS;
                hit.doc_ = spans.docID();
                hit.start_ = spans.startPosition();
                hit.end_ = spans.endPosition();
                assert hit.doc_ >= 0;
                assert hit.start_ >= 0;
                assert hit.end_ >= 0;
                if (numMatchInfos > 0) {
                    hit.matchInfos_ = new MatchInfo[numMatchInfos];
                    hitQueryContext.getMatchInfo(hit.matchInfos_);
                }
                boolean atDocBoundary = hit.doc_ != prevHit.doc_;

                // Check that this is a unique hit, not the exact same as the previous one.
                boolean sameAsLast = hit.equals(prevHit);
                if (!sameAsLast) {

                    // Should we produce the hits we've found before this hit now?
                    // Only ever do this at a document boundary, so we don't split up documents.
                    if (atDocBoundary && (currentBatchOfHits.size() >= fetchHitsMin || hitsCounted - hitsCountedPrev > fetchHitsMin)) {
                        if (unpublishedIndex >= hitsProcessed) {
                            // We're only counting now.
                            if (hitsCounted > hitsCountedPrev) {
                                subscribers.counted(hitsCounted - hitsCountedPrev, docsCounted - docsCountedPrev);
                                hitsStats.add(0, hitsCounted - hitsCountedPrev);
                                docsStats.add(0, (long)docsCounted - docsCountedPrev);
                                hitsCountedPrev = hitsCounted;
                                docsCountedPrev = docsCounted;
                            }
                            unpublishedIndex = alreadyPublishedHits.size();
                        } else {
                            // We've collected some hits. Publish them to our subscribers.
                            publishBatch(lrc);
                        }

                        // Stop processing hits?
                        if (processingHits && hitsStats.processedSoFar() >= maxToProcess)
                            processingHits = false;

                        // Make the batches larger as we process more hits
                        fetchHitsMin = Math.max(FETCH_HITS_MIN, hitsStats.countedSoFar() / 100);
                    }

                    // stop counting hits? (don't care about doc boundary here)
                    if (hitsStats.countedSoFar() >= maxToCount) {
                        // We're done counting hits.
                        setDone();
                        break;
                    }

                    // Now actually process the hit we fetched above
                    if (processingHits) {
                        // Collect this hit
                        currentBatchOfHits.add(hit);
                        hitsProcessed++;
                        if (atDocBoundary)
                            docsProcessedThisBatch++;
                    } else {
                        // We're no longer collecting hits, just counting them.
                        hitsCounted++;
                        if (atDocBoundary)
                            docsCounted++;
                    }

                    // Swap hit and prevHit, so prevHit is always the previous hit
                    EphemeralHit tmp = prevHit;
                    prevHit = hit;
                    hit = tmp;

                }

                // Position spans for the next hit after this
                hasPrefetchedHit = advanceSpansToNextHit();

                // See if we can pause fetching
                if (atDocBoundary && !needAllHits.get() && !subscribers.needsMoreHits()) {
                    // We can pause fetching at this time and resume later, when more hits are needed.
                    subscribers.flush(lrc, alreadyPublishedHits);
                    break;
                }

                // Do this at the end so interruptions don't happen halfway through a loop and lead to invalid states
                ThreadAborter.checkAbort();
            }
        } catch (AssertionError e) {
            subscribers.error(lrc, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // preserve interrupted status
            subscribers.error(lrc, e);
        } catch (Exception e) {
            subscribers.error(lrc, e);
        }
    }

    private void publishBatch(LeafReaderContext lrc) {
        subscribers.hits(lrc, currentBatchOfHits, 0, currentBatchOfHits.size(),
                docsProcessedThisBatch, unpublishedIndex);
        alreadyPublishedHits.addAll(currentBatchOfHits);
        currentBatchOfHits.clear();
        long n = alreadyPublishedHits.size() - unpublishedIndex;
        hitsStats.add(n, n);
        docsStats.add(docsProcessedThisBatch, docsProcessedThisBatch);
        docsProcessed += docsProcessedThisBatch;
        docsProcessedThisBatch = 0;
        unpublishedIndex = alreadyPublishedHits.size();
    }

    private void setDone() {
        LeafReaderContext lrc = context().leafReaderContext();
        publishBatch(lrc);
        subscribers.flush(lrc, alreadyPublishedHits);
        subscribers.done(lrc);
        assert !isDone.get() : "Already done";
        isDone.set(true); // do this last, or we get problems if a new subscriber is in the queue
        allHitsFound.countDown();
    }

    @Override
    public Hits getStatic() {
        // Indicate that we need all hits and start fetch thread if needed
        needAllHits.set(true);
        activate();

        // Wait for all hits to be fetched
        try {
            allHitsFound.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // preserve interrupted status
            throw new InterruptedSearch(e);
        }
        return alreadyPublishedHits.getStatic();
    }

    @Override
    public void subscribe(HitSubscriber subscriber) {
        subscribers.add(subscriber);
        activate();
    }

}
