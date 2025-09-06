package nl.inl.blacklab.search.results.hits.fetch;

import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.search.lucene.BLSpanWeight;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.results.hits.EphemeralHit;
import nl.inl.blacklab.search.results.hits.Hits;
import nl.inl.blacklab.search.results.hits.HitsMutable;
import nl.inl.util.ThreadAborter;

/** 
 * Abstract base class for HitFetcherSegment implementations.
 */
public class HitFetcherSegmentImpl implements HitFetcherSegment {

    public static HitFetcherSegment get(State state, BLSpanWeight weight) {
        return new HitFetcherSegmentImpl(state, new HitsSpans(weight, state.lrc, state.hitQueryContext));
    }

    /** Our state: segment, hit processor, counts, etc. */
    State state;

    /** Are we done with this segment? */
    boolean isDone = false;

    /** Has initialize() been called? */
    boolean isInitialized = false;

    /** What doc was the previous hit in? */
    int prevDoc = -1;

    /** Lazy segment hits we're fetching from */
    Hits source;

    /** Current hit index */
    long hitIndex = -1;

    HitFetcherSegmentImpl(State state, Hits source) {
        this.state = state;
        this.source = source;
    }

    /**
     * Collect hits from our source.
     * Updates the global counters, shared with the other HitFetcherSegment objects operating on the same result set.
     * {@link HitCollectorSegment#collect} is called when we encounter a document boundary.
     * <p>
     * Updating the maximums while this is running is allowed.
     */
    @Override
    public void run() {
        boolean hasFilter = state.filter != HitFilter.ACCEPT_ALL;
        if (!isInitialized) {
            if (hasFilter) {
                state.filter = state.filter.forSegment(source, state.lrc, state.globalFetcher.collationCache);
            }
            isInitialized = true;
        }
        if (isDone) // NOTE: initialize() may instantly set isDone to true, so order is important here.
            return;

        final HitsMutable runHits = HitsMutable.create(
                state.hitQueryContext.getField(), state.hitQueryContext.getMatchInfoDefs(),
                -1, true, false);
        long counted = 0;

        // Keep track of phase: are we still storing runHits, or only counting, or done?
        HitFetcher.Phase phase = HitFetcher.Phase.STORING_AND_COUNTING;

        EphemeralHit hit = new EphemeralHit();
        try {
            // Try to set the spans to a valid hit.
            // Mark if it is at a valid hit.
            // Count and store the hit (if we're not at the limits yet)
            runPrepare();
            prevDoc = -1; // (we already know we're at a document boundary here)
            while (phase != HitFetcher.Phase.MAX_HITS_REACHED) {
                hitIndex++;
                if (!this.source.sizeAtLeast(hitIndex + 1)) {
                    // No more runHits, we're done
                    produceHits(runHits, counted, phase);
                    break; // we're done
                }

                // Check filter
                boolean acceptedHit = true;
                if (hasFilter) {
                    acceptedHit = state.filter.accept(hitIndex);
                }

                // Check that this is a unique hit, not the exact same as the previous one.
                boolean processHit;
                if (acceptedHit) {
                    this.source.getEphemeral(hitIndex, hit);
                    assert hit.doc_ >= 0;
                    assert hit.start_ >= 0;
                    assert hit.end_ >= 0;

                    long prevHitIndex = runHits.size() - 1;
                    boolean sameAsLast = prevHitIndex >= 0 &&
                            hit.doc_ == runHits.doc(prevHitIndex) &&
                            hit.start_ == runHits.start(prevHitIndex) &&
                            hit.end_ == runHits.end(prevHitIndex) &&
                            MatchInfo.areEqual(hit.matchInfos_, runHits.matchInfos(prevHitIndex));
                    processHit = !sameAsLast;
                } else {
                    processHit = false;
                }

                boolean atDocumentBoundary = false;
                if (processHit) {
                    // If we're at a document boundary, pass the runHits we have so far to the collector and update stats.
                    atDocumentBoundary = hit.doc_ != prevDoc;
                    if (atDocumentBoundary && (!runHits.isEmpty() || counted > 0)) {
                        // Update stats and determine phase (fetching/counting/done)
                        phase = produceHits(runHits, counted, phase);
                        counted = 0;
                    }

                    if (phase != HitFetcher.Phase.MAX_HITS_REACHED)
                        counted++;
                    if (phase == HitFetcher.Phase.STORING_AND_COUNTING) {
                        runHits.add(hit);
                    }
                    prevDoc = hit.doc_;
                }

                if (atDocumentBoundary && shouldPauseFetching()) {
                    // We've reached the requested number of runHits and are at a document boundary.
                    // We'll stop for now. When more runHits are requested, this method will be called again.
                    return;
                }

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
            state.collector.flush();
        }

        // If we're here, the loop reached its natural end - we're done.

        // Free some objects to avoid holding on to memory
        this.isDone = true;
        state = null;
        runCleanup();
        // (don't null out leafReaderContext because we use it to make equal groups of SpansReaders)
    }

    private boolean shouldPauseFetching() {
        return state.globalFetcher.shouldPauseFetching();
    }

    private HitFetcher.Phase produceHits(HitsMutable results, long counted, HitFetcher.Phase phase) {
        // Update stats and determine phase (fetching/counting/done)
        phase = state.globalFetcher.updateStats(results.size(), counted,
                prevDoc >= 0, phase == HitFetcher.Phase.STORING_AND_COUNTING);
        state.collector.collect(results);
        results.clear();
        return phase;
    }

    protected void runPrepare() {
    }

    protected void runCleanup() {
        this.source = null;
    }

    @Override
    public LeafReaderContext getLeafReaderContext() {
        return state.lrc;
    }

    @Override
    public boolean isDone() {
        return isDone;
    }

    /** State a HitFetcherSegment receives to do its job. */
    public static class State {

        public static final State DUMMY = new State();

        /** Used to check if doc has been removed from the index. */
        public final LeafReaderContext lrc;

        /** What hits to include/exclude (or null for all) */
        public HitFilter filter;

        /** What to do when a document boundary is encountered. (e.g. merge to global hits list) */
        public final HitCollectorSegment collector;

        /** The global fetcher this segment is part of */
        public final HitFetcherAbstract globalFetcher;

        /** Root hitQueryContext, needs to be shared between instances of HitFetcherSegment due to some
         *  internal global state. */
        public HitQueryContext hitQueryContext;

        public State(
                LeafReaderContext lrc,
                HitFilter filter,
                HitCollectorSegment collector,
                HitFetcherAbstract globalFetcher) {
            this.lrc = lrc;
            this.filter = filter;
            this.collector = collector;
            this.globalFetcher = globalFetcher;
            this.hitQueryContext = globalFetcher == null ? null : globalFetcher.getHitQueryContext();
        }

        private State() {
            this(null, null, null, null);
        }

        public int docBase() {
            return lrc == null ? 0 : lrc.docBase;
        }
    }
}
