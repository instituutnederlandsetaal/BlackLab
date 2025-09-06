package nl.inl.blacklab.search.results.hits.fetch;

import java.io.IOException;

import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.MatchInfo;
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

//    /** How many hits have we produced? */
//    private long processed;
//
//    /** How many hits have we counted? */
//    private long counted;

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

        final HitsMutable hits = HitsMutable.create(
                state.hitQueryContext.getField(), state.hitQueryContext.getMatchInfoDefs(),
                -1, true, false);
        long counted = 0;

        // Keep track of phase: are we still storing hits, or only counting, or done?
        HitFetcher.Phase phase = HitFetcher.Phase.STORING_AND_COUNTING;

        EphemeralHit hit = new EphemeralHit();
        try {
            // Try to set the spans to a valid hit.
            // Mark if it is at a valid hit.
            // Count and store the hit (if we're not at the limits yet)
            runPrepare();
            while (phase != HitFetcher.Phase.MAX_HITS_REACHED) {
                if (!runGetHit(hit)) {
                    produceHits(hits, counted, phase);
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
                boolean processHit;
                if (acceptedHit) {
                    long prevHitIndex = hits.size() - 1;
                    boolean sameAsLast = prevHitIndex >= 0 &&
                            hit.doc_ == hits.doc(prevHitIndex) &&
                            hit.start_ == hits.start(prevHitIndex) &&
                            hit.end_ == hits.end(prevHitIndex) &&
                            MatchInfo.areEqual(hit.matchInfos_, hits.matchInfos(prevHitIndex));
                    processHit = !sameAsLast;
                } else {
                    processHit = false;
                }

                boolean atDocumentBoundary = hit.doc_ != prevDoc;

                if (processHit) {

                    // If we're at a document boundary, pass the hits we have so far to the collector and update stats.
                    if (atDocumentBoundary && (!hits.isEmpty() || counted > 0)) {
                        // Update stats and determine phase (fetching/counting/done)
                        phase = produceHits(hits, counted, phase);
                        counted = 0;
                    }

                    if (phase != HitFetcher.Phase.MAX_HITS_REACHED)
                        counted++;
                    if (phase == HitFetcher.Phase.STORING_AND_COUNTING) {
                        hits.add(hit);
                    }
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

//    /**
//     * Used to make sure that only 1 thread can be fetching hits at a time.
//     */
//    private final Lock ensureHitsReadLock = new ReentrantLock();
//
//    @Override
//    public boolean ensureResultsRead(long number) {
//        number = number < 0 ? Long.MAX_VALUE : number + HitFetcherAbstract.FETCH_HITS_MIN;
//
//        boolean hasLock = false;
//        try {
//            while (!ensureHitsReadLock.tryLock(HitFetcherAbstract.HIT_POLLING_TIME_MS, TimeUnit.MILLISECONDS)) {
//                /*
//                 * Another thread is already working on hits, we don't want to straight up block until it's done,
//                 * as it might be counting/retrieving all results, while we might only want trying to retrieve a small fraction.
//                 * So instead poll our own state, then if we're still missing results after that just count them ourselves
//                 */
//                synchronized (this) { // when we see isDone == true, we need hitsStats to also be up to date
//                    if (isDone || processed >= number) {
//                        return processed >= number;
//                    }
//                }
//            }
//            hasLock = true;
//
//            // This is the blocking portion, start worker threads, then wait for them to finish.
//
//            // Distribute the SpansReaders over the threads.
//            // Make sure the number of documents per segment is roughly equal for each thread.
//            Function<HitFetcherSegment, Long> sizeGetter = spansReader ->
//                    spansReader.getLeafReaderContext() == null ? 0 : (long) spansReader.getLeafReaderContext().reader().maxDoc();
//            List<Future<List<Void>>> pendingResults = parallel.forEach(segmentReaders, sizeGetter,
//                    l -> l.forEach(HitFetcherSegment::run));
//
//            // Wait for workers to complete.
//            // This will throw InterrupedException if this (HitsFromQuery) thread is interruped while waiting.
//            // NOTE: the worker will not automatically abort, so we should also interrupt our workers should that happen.
//            // The workers themselves won't ever throw InterruptedException, it would be wrapped in ExecutionException.
//            // (Besides, we're the only thread that can call interrupt() on our worker anyway, and we don't ever do that.
//            //  Technically, it could happen if the Executor were to shut down, but it would still result in an ExecutionException anyway.)
//            //
//            // If we're interrupted while waiting for workers to finish, and we were the thread that created the workers,
//            // cancel them. (this isn't always the case, we may have been interrupted during self-polling phase)
//            // For the TermsReaders that aren't done yet, the next time this function is called we'll just create new
//            // Runnables/Futures of them.
//            parallel.waitForAll(pendingResults);
//        } catch (ExecutionException e) {
//            if (e.getCause() instanceof RuntimeException rte)
//                throw rte;
//            else
//                throw new IllegalStateException(e.getCause());
//        } catch (Exception e) {
//            // something unforseen happened in our thread
//            // Should generally never happen unless there's a bug or something catastrophic happened.
//            throw new IllegalStateException(e);
//        } finally {
//            // Don't do this unless we're the thread that's actually using the SpansReaders.
//            if (hasLock) {
//                // Remove all SpansReaders that have finished.
//                segmentReaders.removeIf(HitFetcherSegment::isDone);
//                if (segmentReaders.isEmpty())
//                    setDone(); // all spans have been read, so we're done
//                ensureHitsReadLock.unlock();
//            }
//        }
//        return hitsStats.processedSoFar() >= number;
//    }

    private HitFetcher.Phase produceHits(HitsMutable results, long counted, HitFetcher.Phase phase) {
        // Update stats and determine phase (fetching/counting/done)
        phase = state.globalFetcher.updateStats(results.size(), counted,
                prevDoc >= 0, phase == HitFetcher.Phase.STORING_AND_COUNTING);
        state.collector.onDocumentBoundary(results);
//        processed += results.size();
//        this.counted += counted;
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
