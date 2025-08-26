package nl.inl.blacklab.search.results.hits;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.queries.spans.SpanWeight.Postings;
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
import nl.inl.blacklab.search.results.hitresults.HitResultsFromQuery;
import nl.inl.blacklab.search.results.stats.ResultsStatsPassive;
import nl.inl.util.ThreadAborter;

/** 
 * Helper class for use with {@link HitResultsFromQuery} <br><br>
 * 
 * HitsFromQueryParallel generally constructs one SpansReader instance per segment ({@link LeafReaderContext}) of the index.
 * The SpansReader will then produce results for the segment, periodically merging them back to the global resultset passed in.
 * The global result set is contained within HitsFromQueryParallel, and when finished, exposed as Hits.
 */
public class SpansReader implements Runnable {

    /** Everything that's only relevant before initialization */
    class SpansReaderBeforeInit {
        BLSpanWeight weight;

        HitQueryContext sourceHitQueryContext; // Set when uninitialized (needed to construct own hitQueryContext)

        public SpansReaderBeforeInit(BLSpanWeight weight, HitQueryContext hitQueryContext) {
            this.weight = weight;
            this.sourceHitQueryContext = hitQueryContext;
        }
    }

    // Relevant when SpansReader is uninitialized; discarded afterwards
    private SpansReaderBeforeInit beforeInit;

    BLSpans spans; // usually lazy initialization - takes a long time to set up and holds a large amount of memory.
                   // Set to null after we're finished

    /** Allows us to more efficiently step to the next potentially matching document */
    private DocIdSetIterator twoPhaseApproximation;

    /** Allows us to check that doc matched by approximation is an actual match */
    private TwoPhaseIterator twoPhaseIt;

    /**
     * Root hitQueryContext, needs to be shared between instances of SpansReader due to some internal global state.
     * <p>
     * TODO refactor or improve documentation in HitQueryContext, the internal backing array is now shared between
     *   instances of it, and is modified in copyWith(spans), which seems...dirty and it's prone to errors.
     */
    HitQueryContext hitQueryContext; // Set after initialization. Set to null after we're finished

    // Used to check if doc has been removed from the index. Set to null after we're finished.
    LeafReaderContext leafReaderContext;

    // Global counters, shared between instances of SpansReader in order to coordinate progress
    ResultsStatsPassive hitsStats;
    ResultsStatsPassive docsStats;

    /** Target number of hits to store in the results list */
    final AtomicLong globalHitsToProcess;
    /** Target number of hits to count, must always be >= {@link #globalHitsToProcess} */
    final AtomicLong globalHitsToCount;

    /** What to do when a document boundary is encountered.
     *  (e.g. merge to global hits list) */
    private final Strategy spansReaderStrategy;

    // Internal state
    boolean isDone;
    private boolean isInitialized;
    /* only valid after initialize() */
    private int docBase; 

    private boolean hasPrefetchedHit = false;
    private int prevDoc = -1;

    /**
     * Construct an uninitialized SpansReader that will retrieve its own Spans object on when it's ran.
     * <p>
     * SpansReader will self-initialize (meaning its Spans object and HitQueryContext are set). This is done
     * because SpansReaders can hold a lot of memory and time to set up and only a few are active at a time.
     * <p>
     * All SpansReaders share an instance of MatchInfoDefs (via the hit query context, of which each SpansReader gets
     * a personalized copy, but with the same shared MatchInfoDefs instance).
     * <p>
     * SpansReaders will register their match infos with the MatchInfoDefs instance. Often the first SpansReader will
     * register all match infos, but sometimes the first SpansReader only matches some match infos, and subsequent
     * SpansReaders will register additional match infos. This is dealt with later (when merging two matchInfo[] arrays
     * of different length).
     * <p>
     *
     * @param weight                span weight we're querying
     * @param leafReaderContext     leaf reader we're running on
     * @param sourceHitQueryContext source HitQueryContext from HitsFromQueryParallel; we'll derive our own context from it
     * @param spansReaderStrategy   how to handle the hits as they are found, or null not to do anything yet
     * @param globalHitsToProcess   how many more hits to retrieve
     * @param globalHitsToCount     how many more hits to count
     */
    SpansReader(
        BLSpanWeight weight,
        LeafReaderContext leafReaderContext,
        HitQueryContext sourceHitQueryContext,

        Strategy spansReaderStrategy,
        AtomicLong globalHitsToProcess,
        AtomicLong globalHitsToCount,
        ResultsStatsPassive hitsStats,
        ResultsStatsPassive docsStats
    ) {
        this.beforeInit = new SpansReaderBeforeInit(weight, sourceHitQueryContext);
        this.spans = null;

        this.hitQueryContext = null;

        this.leafReaderContext = leafReaderContext;

        this.spansReaderStrategy = spansReaderStrategy;
        this.globalHitsToCount = globalHitsToCount;
        this.globalHitsToProcess = globalHitsToProcess;

        this.hitsStats = hitsStats;
        this.docsStats = docsStats;

        this.isInitialized = false;
        this.isDone = false;
    }

    /**
     * Check if hit is the same as the last hit.
     *
     * @param doc   the document number
     * @param start the start position
     * @param end   the end position
     * @param matchInfo the match info
     */
    private boolean isSameAsLast(Hits hits, int doc, int start, int end, MatchInfo[] matchInfo) {
        long prev = hits.size() - 1;
        return hits.size() > 0 && doc == hits.doc(prev) && start == hits.start(prev) && end == hits.end(prev) &&
                MatchInfo.areEqual(matchInfo, hits.matchInfos(prev));
    }

    private void initialize() {
        try {
            this.isInitialized = true;
            this.docBase = this.leafReaderContext.docBase;
            BLSpans spansForWeight = this.beforeInit.weight.getSpans(this.leafReaderContext, Postings.OFFSETS);
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

            this.hitQueryContext = this.beforeInit.sourceHitQueryContext.withSpans(this.spans);
            this.spans.setHitQueryContext(this.hitQueryContext);
            this.beforeInit = null;
        } catch (IOException e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    /**
     * Step through all hits in all documents in this spans object.
     *
     * @param liveDocs used to check if the document is still alive in the index.
     * @return true if the spans has been advanced to the next hit, false if out of hits.
     */
    private boolean advanceSpansToNextHit(Bits liveDocs) throws IOException {
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

    public enum Phase {
        STORING_AND_COUNTING,
        COUNTING_ONLY,
        DONE
    }

    /**
     * Collect hits from our spans object.
     * Updates the global counters, shared with other SpansReader objects operating on the same result set.
     * {@link Strategy::onDocumentBoundary} is called when we encounter a document boundary.
     * <p>
     * Updating the maximums while this is running is allowed.
     */
    @Override
    public synchronized void run() {
        if (!isInitialized)
            this.initialize();

        if (isDone) // NOTE: initialize() may instantly set isDone to true, so order is important here.
            return;

        final int numMatchInfos = hitQueryContext.numberOfMatchInfos();

        final HitsMutable results = HitsMutable.create(hitQueryContext.getField(), hitQueryContext.getMatchInfoDefs(), -1, true, false);
        long counted = 0;
        final Bits liveDocs = leafReaderContext.reader().getLiveDocs();

        // Increment if we're NOT at a document boundary OR we haven't reached the currently requested number of hits yet.
        // (that means that we can go over the requested number until we reach a document boundary. This is because
        //  we want to keep hits from the same document contiguous in the results list, so e.g. document count is
        //  correct)
        final BiFunction<Long, Boolean, Long> incrementCountUnlessAtMaxAndBoundary = (count, atBoundary) ->
                (count < this.globalHitsToCount.get() || !atBoundary) ? count + 1 : count;
        final BiFunction<Long, Boolean, Long> incrementProcessUnlessAtMaxAndBoundary = (count, atBoundary) ->
                (count < this.globalHitsToProcess.get() || !atBoundary) ? count + 1 : count;

        try {
            // Try to set the spans to a valid hit.
            // Mark if it is at a valid hit.
            // Count and store the hit (if we're not at the limits yet)

            if (!hasPrefetchedHit) {
                prevDoc = spans.docID();
                hasPrefetchedHit = advanceSpansToNextHit(liveDocs);
            }

            // If we reach or exceed the limit when at a document boundary, we stop storing hits,
            // but we still count them.
            Phase phase = Phase.STORING_AND_COUNTING;

            while (phase != Phase.DONE && hasPrefetchedHit) {
                // Find all the hit information
                assert spans.docID() != DocIdSetIterator.NO_MORE_DOCS;
                assert spans.startPosition() != Spans.NO_MORE_POSITIONS;
                assert spans.endPosition() != Spans.NO_MORE_POSITIONS;
                final int doc = spans.docID() /*+ docBase*/;
                boolean atDocumentBoundary = doc != prevDoc;
                int start = spans.startPosition();
                int end = spans.endPosition();
                MatchInfo[] matchInfo = null;
                if (numMatchInfos > 0) {
                    matchInfo = new MatchInfo[numMatchInfos];
                    hitQueryContext.getMatchInfo(matchInfo);
                }

                // Check that this is a unique hit, not the exact same as the previous one.
                boolean isSameAsLast = isSameAsLast(results, doc, start, end, matchInfo);

                if (!isSameAsLast) {
                    // Only if previous value (which is returned) was not yet at the limit (and thus we actually incremented) do we count this hit.
                    // Otherwise, don't store it either. We're done, just return.
                    counted++;

                    if (atDocumentBoundary) {
                        docsStats.increment(phase == Phase.STORING_AND_COUNTING);
                        phase = spansReaderStrategy.onDocumentBoundary(results, counted);
                        counted = 0;
                    }

                    if (phase == Phase.STORING_AND_COUNTING) {
                        assert start >= 0;
                        assert end >= 0;
                        results.add(doc, start, end, matchInfo);
                    }
                }

                hasPrefetchedHit = advanceSpansToNextHit(liveDocs);
                prevDoc = doc;

                // Do this at the end so interruptions don't happen halfway through a loop and lead to invalid states
                ThreadAborter.checkAbort();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            Thread.currentThread().interrupt(); // preserve interrupted status
            throw new InterruptedSearch(e);
        } catch (Exception e) {
            e.printStackTrace();
            throw BlackLabException.wrapRuntime(e);
        } finally {
            // write out leftover hits in last document/aborted document
            spansReaderStrategy.onFinished(results, counted);
        }

        // If we're here, the loop reached its natural end - we're done.
        // Free some objects to avoid holding on to memory
        this.isDone = true;
        this.spans = null;
        this.hitQueryContext = null;
        // (don't null out leafReaderContext because we use it to make equal groups of SpansReaders)
    }

    /** How to deal with the hits found in the segment. */
    public interface Strategy {
        /**
         * Called when the SpansReader has reached the end of a document.
         * @param results the hits collected so far
         * @return whether to continue storing hits, or just count them, or stop altogether
         */
        Phase onDocumentBoundary(HitsMutable results, long counted);

        /**
         * Called when the SpansReader is done.
         * @param results the hits collected so far
         */
        void onFinished(HitsMutable results, long counted);
    }

}
