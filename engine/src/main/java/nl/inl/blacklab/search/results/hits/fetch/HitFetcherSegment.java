package nl.inl.blacklab.search.results.hits.fetch;

import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.results.hits.EphemeralHit;
import nl.inl.blacklab.search.results.hits.Hits;

public interface HitFetcherSegment extends Runnable {

    /** State a HitFetcherSegment receives to do its job. */
    class State {

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
