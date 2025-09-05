package nl.inl.blacklab.search.results.hits.fetch;

import java.io.IOException;

import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.search.results.hits.EphemeralHit;
import nl.inl.blacklab.search.results.hits.HitsMutable;
import nl.inl.blacklab.search.results.hits.HitsSingle;
import nl.inl.util.ThreadAborter;

/** 
 * Abstract base class for HitFetcherSegment implementations.
 */
public abstract class HitFetcherSegmentAbstract implements HitFetcherSegment {

    /** Our state: segment, hit processor, counts, etc. */
    State state;

    /** Are we done with this segment? */
    boolean isDone = false;

    /** Has initialize() been called? */
    boolean isInitialized = false;

    /** What doc was the previous hit in? */
    int prevDoc = -1;

    /** Used to filter because HitProperty needs a Hits instance... */
    private HitsSingle filterHit;

    HitFetcherSegmentAbstract(State state) {
        this.state = state;
    }

    /**
     * Collect hits from our source.
     * Updates the global counters, shared with the other HitFetcherSegment objects operating on the same result set.
     * {@link HitCollectorSegment#onDocumentBoundary} is called when we encounter a document boundary.
     * <p>
     * Updating the maximums while this is running is allowed.
     */
    @Override
    public void run() {
        boolean hasFilter = state.filter != HitFilter.ACCEPT_ALL;
        if (!isInitialized) {
            initialize();
            if (hasFilter) {
                filterHit = new HitsSingle(state.hitQueryContext.getField(), state.hitQueryContext.getMatchInfoDefs());
                state.filter = state.filter.forSegment(filterHit, state.lrc, state.globalFetcher.collationCache);
            }
            isInitialized = true;
        }
        if (isDone) // NOTE: initialize() may instantly set isDone to true, so order is important here.
            return;

        final HitsMutable results = HitsMutable.create(state.hitQueryContext.getField(),
                state.hitQueryContext.getMatchInfoDefs(), -1, true,
                false);
        long counted = 0;

        // Keep track of phase: are we still storing hits, or only counting, or done?
        HitFetcher.Phase phase = HitFetcher.Phase.STORING_AND_COUNTING;

        try {
            // Try to set the spans to a valid hit.
            // Mark if it is at a valid hit.
            // Count and store the hit (if we're not at the limits yet)

            EphemeralHit hit = new EphemeralHit();

            runPrepare();
            while (phase != HitFetcher.Phase.DONE) {
                if (!runGetHit(hit)) {
                    produceHits(results, counted, phase);
                    break; // we're done
                }
                assert hit.doc_ >= 0;
                assert hit.start_ >= 0;
                assert hit.end_ >= 0;

                // Check filter
                boolean acceptedHit = true;
                if (hasFilter) {
                    // TODO: Icky... change HitProperty so get is called with Hits and index?
                    // (but then we cannot use it as a comparator...?)
                    filterHit.set(hit);
                    state.filter.disposeContext(); // get rid of context for previous hit
                    acceptedHit = state.filter.accept(0);
                }

                // Check that this is a unique hit, not the exact same as the previous one.
                boolean uniqueHit = acceptedHit && !HitFetcherSegment.isSameAsLast(results, hit);

                boolean atDocumentBoundary = hit.doc_ != prevDoc;

                if (uniqueHit) {

                    // If we're at a document boundary, pass the hits we have so far to the collector and update stats.
                    if (atDocumentBoundary && (!results.isEmpty() || counted > 0)) {
                        // Update stats and determine phase (fetching/counting/done)
                        phase = produceHits(results, counted, phase);
                        counted = 0;
                    }

                    if (phase != HitFetcher.Phase.DONE)
                        counted++;
                    if (phase == HitFetcher.Phase.STORING_AND_COUNTING)
                        results.add(hit);
                    prevDoc = hit.doc_;
                }

                if (atDocumentBoundary && state.globalFetcher.shouldPauseFetching()) {
                    // We've reached the requested number of hits and are at a document boundary.
                    // We'll stop for now. When more hits are requested, this method will be called again.
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

    private HitFetcher.Phase produceHits(HitsMutable results, long counted, HitFetcher.Phase phase) {
        // Update stats and determine phase (fetching/counting/done)
        phase = state.globalFetcher.updateStats(results.size(), counted,
                prevDoc >= 0, phase == HitFetcher.Phase.STORING_AND_COUNTING);
        state.collector.onDocumentBoundary(results);
        results.clear();
        return phase;
    }

    /** Prepare for fetching hits in run() (e.g. make sure first hit is prefetched). */
    protected abstract void runPrepare() throws IOException;

    /** Fetch the next hit in run(). */
    protected abstract boolean runGetHit(EphemeralHit hit) throws IOException;

    /** Clean up when run() finishes. */
    protected abstract void runCleanup();

    @Override
    public LeafReaderContext getLeafReaderContext() {
        return state.lrc;
    }

    @Override
    public boolean isDone() {
        return isDone;
    }
}
