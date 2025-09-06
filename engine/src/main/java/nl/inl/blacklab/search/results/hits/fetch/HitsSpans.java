package nl.inl.blacklab.search.results.hits.fetch;

import java.io.IOException;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.queries.spans.SpanWeight;
import org.apache.lucene.queries.spans.Spans;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.util.Bits;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.lucene.BLSpanWeight;
import nl.inl.blacklab.search.lucene.BLSpans;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;
import nl.inl.blacklab.search.results.hits.EphemeralHit;
import nl.inl.blacklab.search.results.hits.Hits;
import nl.inl.blacklab.search.results.hits.HitsAbstract;
import nl.inl.util.ThreadAborter;

/** 
 * Lazy Hits interface to a single Spans object.
 * <p>
 * CAUTION: only the LATEST hit can be fetched! So after calling
 * sizeAtLeast(n), you can only get the hit at index n - 1. Attempting to
 * get any other hit will throw an exception.
 * <p>
 * Not thread-safe.
 */
public class HitsSpans extends HitsAbstract {

    /** Set until initialized (needed to get spans); null afterwards */
    BLSpanWeight weight;

    private final LeafReaderContext lrc;

    /** Set until initialized (needed to construct own hitQueryContext); null afterwards */
    HitQueryContext sourceHitQueryContext;

    /** Our own hit query context */
    HitQueryContext hitQueryContext;

    private int numMatchInfos;

    BLSpans spans; // usually lazy initialization - takes a long time to set up and holds a large amount of memory.
    // Set to null after we're finished

    /** Allows us to more efficiently step to the next potentially matching document */
    private DocIdSetIterator twoPhaseApproximation;

    /** Allows us to check that doc matched by approximation is an actual match */
    private TwoPhaseIterator twoPhaseIt;

    private Bits liveDocs;

    private boolean hasPrefetchedHit = false;

    boolean isDone = false;

    /** Has initialize() been called? */
    boolean isInitialized = false;

    /** What doc was the previous hit in? */
    int prevDoc = -1;

    /** The current hit (at hitIndex) */
    private EphemeralHit currentHit = new EphemeralHit();

    /** Previous hit, to check that current one is unique */
    private EphemeralHit prevHit = new EphemeralHit();

    /** Our current size. Actually only the hit at size-1 can be retrieved. */
    private long processed = 0;

    long counted = 0;

    long maxToProcess = Long.MAX_VALUE;

    long maxToCount = Long.MAX_VALUE;

    /** Did we hit max hits to process? */
    private boolean doneProcessing = false;

    /** Lazy Hits interface to a single Spans object. */
    HitsSpans(BLSpanWeight weight, LeafReaderContext lrc, HitQueryContext sourceHitQueryContext) {
        this.weight = weight;
        this.lrc = lrc;
        this.sourceHitQueryContext = sourceHitQueryContext;
        this.spans = null;
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
    public void initialize() {
        isInitialized = true;
        try {
            BLSpans spansForWeight = this.weight.getSpans(lrc, SpanWeight.Postings.OFFSETS);
            this.weight = null;
            if (spansForWeight == null) { // This is normal, sometimes a section of the index does not contain hits.
                this.isDone = true;
                return;
            }
            // If the resulting spans are not known to be sorted and unique, ensure that now.
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
                prevDoc = spans.docID();
                hasPrefetchedHit = advanceSpansToNextHit();
            }

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

    private boolean ensureSeen(long requestedNumberOfHits) {
        if (requestedNumberOfHits == 0)
            return true;
        if (!isInitialized)
            initialize();
        if (requestedNumberOfHits < 0)
            requestedNumberOfHits = Long.MAX_VALUE;
        if (requestedNumberOfHits > maxToCount)
            requestedNumberOfHits = maxToCount;
        while (!isDone && processed < requestedNumberOfHits) {
            try {
                // Are we done?
                if (!hasPrefetchedHit) {
                    isDone = true;
                    break;
                }

                // Switch current and prev hit (to avoid reallocations)
                EphemeralHit temp = prevHit;
                prevHit = currentHit;
                currentHit = temp;

                // Get hit
                assert spans.docID() != DocIdSetIterator.NO_MORE_DOCS;
                assert spans.startPosition() != Spans.NO_MORE_POSITIONS;
                assert spans.endPosition() != Spans.NO_MORE_POSITIONS;
                currentHit.doc_ = spans.docID();
                currentHit.start_ = spans.startPosition();
                currentHit.end_ = spans.endPosition();
                assert currentHit.doc_ >= 0;
                assert currentHit.start_ >= 0;
                assert currentHit.end_ >= 0;
                if (numMatchInfos > 0) {
                    currentHit.matchInfos_ = new MatchInfo[numMatchInfos];
                    hitQueryContext.getMatchInfo(currentHit.matchInfos_);
                } else {
                    currentHit.matchInfos_ = null;
                }

                // Check that this is a unique hit, not the exact same as the previous one.
                boolean sameAsLast = prevHit != null &&
                        currentHit.doc_ == prevHit.doc_ &&
                        currentHit.start_ == prevHit.start_ &&
                        currentHit.end_ == prevHit.end_ &&
                        MatchInfo.areEqual(currentHit.matchInfos_, prevHit.matchInfos_);
                if (!sameAsLast) {
                    if (processed < maxToProcess) {
                        // "collect" this hit
                        processed++;
                    } else {
                        doneProcessing = true;
                    }
                    counted++;
                }

                // Position spans for the next hit after this
                hasPrefetchedHit = advanceSpansToNextHit();

                // Do this at the end so interruptions don't happen halfway through a loop and lead to invalid states
                ThreadAborter.checkAbort();
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt(); // preserve interrupted status
                throw new InterruptedSearch(e);
            }
        }
        // Did we find the requested hit? (with index requestedNumberOfHits - 1)
        return processed >= requestedNumberOfHits;
    }

    @Override
    public AnnotatedField field() {
        return hitQueryContext.getField();
    }

    @Override
    public MatchInfoDefs matchInfoDefs() {
        return hitQueryContext.getMatchInfoDefs();
    }

    @Override
    public long size() {
        ensureSeen(maxToProcess);
        return processed;
    }

    @Override
    public long sizeSoFar() {
        return processed;
    }

    @Override
    public boolean sizeAtLeast(long number) {
        return ensureSeen(number);
    }

    public long counted() {
        ensureSeen(-1);
        return counted;
    }

    @Override
    public void getEphemeral(long index, EphemeralHit hit) {
        ensureSeen(index + 1);
        if (index != processed - 1)
            throw new IndexOutOfBoundsException("Can only get the latest hit (index " + (processed - 1) + "), requested index: " + index);
        hit.doc_ = currentHit.doc_;
        hit.start_ = currentHit.start_;
        hit.end_ = currentHit.end_;
        hit.matchInfos_ = currentHit.matchInfos_;
    }

    @Override
    public int doc(long index) {
        ensureSeen(index + 1);
        if (index != processed - 1)
            throw new IndexOutOfBoundsException("Can only get the latest hit (index " + (processed - 1) + "), requested index: " + index);
        return currentHit.doc_;
    }

    @Override
    public int start(long index) {
        ensureSeen(index + 1);
        if (index != processed - 1)
            throw new IndexOutOfBoundsException("Can only get the latest hit (index " + (processed - 1) + "), requested index: " + index);
        return currentHit.start_;
    }

    @Override
    public int end(long index) {
        ensureSeen(index + 1);
        if (index != processed - 1)
            throw new IndexOutOfBoundsException("Can only get the latest hit (index " + (processed - 1) + "), requested index: " + index);
        return currentHit.end_;
    }

    @Override
    public MatchInfo[] matchInfos(long hitIndex) {
        ensureSeen(hitIndex + 1);
        if (hitIndex != processed - 1)
            throw new IndexOutOfBoundsException("Can only get the latest hit (index " + (processed - 1) + "), requested index: " + hitIndex);
        return currentHit.matchInfos_;
    }

    @Override
    public MatchInfo matchInfo(long hitIndex, int matchInfoIndex) {
        ensureSeen(hitIndex + 1);
        if (hitIndex != processed - 1)
            throw new IndexOutOfBoundsException("Can only get the latest hit (index " + (processed - 1) + "), requested index: " + hitIndex);
        return currentHit.matchInfo(matchInfoIndex);
    }

    @Override
    public Hits getStatic() {
        throw new UnsupportedOperationException();
    }
}
