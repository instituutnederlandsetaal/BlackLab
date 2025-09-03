package nl.inl.blacklab.search.results.hits.fetch;

import java.util.concurrent.atomic.AtomicLong;

import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.results.hits.EphemeralHit;
import nl.inl.blacklab.search.results.hits.Hits;
import nl.inl.blacklab.search.results.stats.ResultsStatsPassive;

public interface HitFetcherSegment extends Runnable {

    /** State a HitFetcherSegment receives to do its job. */
    class State {

        public static final State DUMMY = new State(null, null, null, null, null, null, null);

        /** Used to check if doc has been removed from the index. */
        public LeafReaderContext lrc;

        /** Root hitQueryContext, needs to be shared between instances of HitFetcherSegment due to some internal global state. */
        public HitQueryContext hitQueryContext;

        /** What to do when a document boundary is encountered. (e.g. merge to global hits list) */
        public HitProcessor hitProcessor;

        /** Target number of hits to store in the results list */
        public AtomicLong globalHitsToProcess;

        /** Target number of hits to count, must always be >= globalHitsToProcess */
        public AtomicLong globalHitsToCount;

        /** Global counters, shared between instances of HitFetcherQuerySegment in order to coordinate progress */
        public ResultsStatsPassive hitsStats;

        /** Global counters, shared between instances of HitFetcherQuerySegment in order to coordinate progress */
        public ResultsStatsPassive docsStats;

        public State(LeafReaderContext lrc, HitQueryContext hitQueryContext, HitProcessor hitProcessor,
                AtomicLong globalHitsToProcess, AtomicLong globalHitsToCount, ResultsStatsPassive hitsStats,
                ResultsStatsPassive docsStats) {
            this.lrc = lrc;
            this.hitQueryContext = hitQueryContext;
            this.hitProcessor = hitProcessor;
            this.globalHitsToProcess = globalHitsToProcess;
            this.globalHitsToCount = globalHitsToCount;
            this.hitsStats = hitsStats;
            this.docsStats = docsStats;
        }
    }

    /**
     * Check if hit is the same as the last hit.
     *
     * @param hit the hit to check
     */
    static boolean isSameAsLast(Hits hits, EphemeralHit hit) {
        long prev = hits.size() - 1;
        return !hits.isEmpty() && hit.doc_ == hits.doc(prev) && hit.start_ == hits.start(prev) && hit.end_ == hits.end(prev) &&
                MatchInfo.areEqual(hit.matchInfos_, hits.matchInfos(prev));
    }

    void initialize();

    @Override
    void run();

    LeafReaderContext getLeafReaderContext();

    boolean isDone();
}
