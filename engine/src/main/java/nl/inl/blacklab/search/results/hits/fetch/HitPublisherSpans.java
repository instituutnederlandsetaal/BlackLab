package nl.inl.blacklab.search.results.hits.fetch;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.queries.spans.SpanWeight;
import org.apache.lucene.queries.spans.Spans;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.util.Bits;

import nl.inl.blacklab.exceptions.BlackLabException;
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
    private BLSpanWeight weight;

    /** Set until initialized (needed to construct own hitQueryContext); null afterwards */
    private HitQueryContext sourceHitQueryContext;

    /** Has initialize() been called? */
    private boolean isInitialized = false;

    /** Our own hit query context */
    private HitQueryContext hitQueryContext;

    /** How many match infos there are in the hitQueryContext */
    private int numMatchInfos;

    /** Spans object we read our hits from. Will be lazy-initialized in initialize() because
     * it can take a long time to set up and hold a large amount of memory, even if you never
     * fetch a hit from it.
     * After we finish, this is set to null.
     */
    private BLSpans spans;

    /** Allows us to more efficiently step to the next potentially matching document */
    private DocIdSetIterator twoPhaseApproximation;

    /** Allows us to check that doc matched by approximation is an actual match */
    private TwoPhaseIterator twoPhaseIt;

    /** Which documents in the segment have (not) been deleted? */
    private Bits liveDocs;

    /** Does spans points to a valid hit we haven't fetched yet? */
    private boolean hasPrefetchedHit = false;

    /** The previous hit we've looked at. */
    private EphemeralHit prevHit = new EphemeralHit();

    /** The hit we're currently looking at. */
    private EphemeralHit hit = new EphemeralHit();

    /** How many distinct documents are in the current (unpublished) batch */
    private int docsProcessedThisBatch = 0;

    /** Count-only results not yet reported to the output. */
    private long hitsToReport;

    /** Count-only documents not yet reported to the output. */
    private int docsToReport;

    /** At what point should we stop storing hits and just count them? */
    private final long maxToProcess;

    /** At what point should we give up even just counting the hits? */
    private final long maxToCount;

    /** The hits processed/counted across HitPublisherSpans instances */
    private final ResultsStatsPassive hitsStats;

    /** The docs processed/counted across HitPublisherSpans instances */
    private final ResultsStatsPassive docsStats;

    /** Where our fetch thread should run. */
    private final ExecutorService executorService;

    /**
     * Whether one fetch worker owns this publisher, including while it is still queued.
     * Guarded by this publisher's monitor.
     */
    private boolean workerScheduled;

    /** Retained hits, subscribers and externally visible publication state. */
    private final HitPublisherOutput output;

    /** The current batch of hits (not yet published). NONLOCKING. */
    private final HitsMutable currentBatchOfHits;

    /** If set to true, we need to collect all hits. Ignore subscriber's ideas about pausing. */
    private volatile boolean needAllHits;

    /** Lazy Hits interface to a single Spans object. */
    public HitPublisherSpans(LeafReaderContext lrc, BLSpanWeight weight, HitQueryContext sourceHitQueryContext,
            ExecutorService executorService, ResultsStatsPassive hitsStats, ResultsStatsPassive docsStats,
            boolean saveAllPublishedHits) {
        this.weight = weight;
        this.sourceHitQueryContext = sourceHitQueryContext;
        this.spans = null;
        this.executorService = executorService;
        this.hitsStats = hitsStats;
        maxToProcess = hitsStats.getMaxHitsToProcess();
        maxToCount = hitsStats.getMaxHitsToCount();
        this.docsStats = docsStats;
        Hits.HitsContext context = new Hits.HitsContext(sourceHitQueryContext.getField(),
                sourceHitQueryContext.getMatchInfoDefs(), lrc);
        output = new HitPublisherOutput(context, saveAllPublishedHits);
        currentBatchOfHits = HitsMutable.create(context, -1, true, false);
    }

    public Hits.HitsContext context() {
        return output.context();
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
        if (output.isComplete() || workerScheduled)
            return;
        workerScheduled = true;
        try {
            executorService.execute(this::fetchAndPublishHits);
        } catch (RuntimeException | Error e) {
            // Submission did not transfer ownership to a worker, so a later activation may retry.
            workerScheduled = false;
            throw e;
        }
    }

    /** This method is what the fetch thread runs.
     * <p>
     * It will fetch and publish hits until neither we nor our subscribers
     * want any more, or there are no more.
     */
    private void fetchAndPublishHits() {
        try {
            if (!isInitialized)
                initialize();
            if (output.isComplete())
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
                    if (atDocBoundary && (currentBatchOfHits.size() >= fetchHitsMin || hitsToReport > fetchHitsMin)) {
                        if (currentBatchOfHits.isEmpty()) {
                            // We're only counting now.
                            publishCounted();
                        } else {
                            // We've collected some hits. Publish them to our subscribers.
                            publishBatch();
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
                        if (atDocBoundary)
                            docsProcessedThisBatch++;
                    } else {
                        // We're no longer collecting hits, just counting them.
                        hitsToReport++;
                        if (atDocBoundary)
                            docsToReport++;
                    }

                    // Swap hit and prevHit, so prevHit is always the previous hit
                    EphemeralHit tmp = prevHit;
                    prevHit = hit;
                    hit = tmp;

                }

                // Position spans for the next hit after this
                hasPrefetchedHit = advanceSpansToNextHit();

                // See if we can pause fetching
                if (atDocBoundary && !needsMoreHits()) {
                    output.flush();
                    synchronized (this) {
                        // Subscription and getStatic() publish their monotonic demand before activate(). Because
                        // output.needsMoreHits() is a pure volatile-snapshot poll, it is safe under this monitor.
                        if (needsMoreHits())
                            continue;
                        workerScheduled = false;
                        return; // paused; no publisher state may be touched after releasing ownership
                    }
                }

                // Do this at the end so interruptions don't happen halfway through a loop and lead to invalid states
                ThreadAborter.checkAbort();
            }
        } catch (AssertionError e) {
            setFailed(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // preserve interrupted status
            setFailed(e);
        } catch (Exception e) {
            setFailed(e);
        }
    }

    /** Pure demand poll used outside and inside the lifecycle monitor. */
    private boolean needsMoreHits() {
        return needAllHits || output.needsMoreHits();
    }

    private void setFailed(Throwable exception) {
        try {
            output.fail(exception);
        } finally {
            releaseTerminalWorker();
        }
    }

    private void publishBatch() {
        long n = currentBatchOfHits.size();
        output.publish(currentBatchOfHits, docsProcessedThisBatch);
        currentBatchOfHits.clear();
        hitsStats.add(n, n);
        docsStats.add(docsProcessedThisBatch, docsProcessedThisBatch);
        docsProcessedThisBatch = 0;
    }

    private void setDone() {
        publishBatch();
        publishCounted();
        output.flush();
        output.complete();
        releaseTerminalWorker();
    }

    /** Relinquish the current worker only after its terminal outcome is externally visible. */
    private synchronized void releaseTerminalWorker() {
        workerScheduled = false;
    }

    private void publishCounted() {
        if (hitsToReport > 0) {
            output.counted(hitsToReport, docsToReport);
            hitsStats.add(0, hitsToReport);
            docsStats.add(0, docsToReport);
            hitsToReport = 0;
            docsToReport = 0;
        }
    }

    @Override
    public Hits getStatic() {
        if (!output.retainsHits())
            throw new IllegalStateException("This publisher doesn't save its hits, cannot get static view");
        // Indicate that we need all hits and start fetch thread if needed
        needAllHits = true;
        activate();

        return output.getStatic();
    }

    @Override
    public void subscribe(HitSubscriber subscriber) {
        output.subscribe(subscriber);
        activate();
    }

}
