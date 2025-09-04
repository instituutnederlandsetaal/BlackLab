package nl.inl.blacklab.search.results.hits.fetch;

import java.io.IOException;

import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.search.results.hits.EphemeralHit;
import nl.inl.blacklab.search.results.hits.HitsMutable;
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

    HitFetcherSegmentAbstract(State state) {
        this.state = state;
    }

    /**
     * Collect hits from our source.
     * Updates the global counters, shared with the other HitFetcherSegment objects operating on the same result set.
     * {@link HitProcessor#onDocumentBoundary} is called when we encounter a document boundary.
     * <p>
     * Updating the maximums while this is running is allowed.
     */
    @Override
    public void run() {
        if (!isInitialized) {
            initialize();
            isInitialized = true;
        }
        if (isDone) // NOTE: initialize() may instantly set isDone to true, so order is important here.
            return;

        final HitsMutable results = HitsMutable.create(state.hitQueryContext.getField(),
                state.hitQueryContext.getMatchInfoDefs(), -1, true,
                false);
        long counted = 0;
        try {
            // Try to set the spans to a valid hit.
            // Mark if it is at a valid hit.
            // Count and store the hit (if we're not at the limits yet)

            // If we reach or exceed the limit when at a document boundary, we stop storing hits,
            // but we still count them.
            HitFetcher.Phase phase = HitFetcher.Phase.STORING_AND_COUNTING;
            EphemeralHit hit = new EphemeralHit();

            runPrepare();
            while (phase != HitFetcher.Phase.DONE) {
                if (!runGetHit(hit))
                    break; // we're done
                boolean atDocumentBoundary = hit.doc_ != prevDoc;

                // Check that this is a unique hit, not the exact same as the previous one.
                boolean ignoreHit = HitFetcherSegment.isSameAsLast(results, hit);
                if (!ignoreHit) {
                    // Only if previous value (which is returned) was not yet at the limit (and thus we actually incremented) do we count this hit.
                    // Otherwise, don't store it either. We're done, just return.
                    counted++;

                    if (atDocumentBoundary) {
                        state.docsStats.increment(phase == HitFetcher.Phase.STORING_AND_COUNTING);
                        phase = state.hitProcessor.onDocumentBoundary(results, counted);
                        counted = 0;
                    }

                    if (phase == HitFetcher.Phase.STORING_AND_COUNTING) {
                        assert hit.start_ >= 0;
                        assert hit.end_ >= 0;
                        results.add(hit);
                    }
                }
                prevDoc = hit.doc_;

                if (atDocumentBoundary &&
                        state.hitProcessor.globalProcessedSoFar() >= state.globalHitsToProcess.get() &&
                        state.hitProcessor.globalCountedSoFar() >= state.globalHitsToCount.get()) {
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
            // write out leftover hits in last document/aborted document
            state.hitProcessor.onFinished(results, counted);
        }

        // If we're here, the loop reached its natural end - we're done.
        // Free some objects to avoid holding on to memory
        this.isDone = true;
        state = null;
        runCleanup();
        // (don't null out leafReaderContext because we use it to make equal groups of SpansReaders)
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
