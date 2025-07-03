package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.queries.spans.Spans;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * Wrap a Spans to retrieve sequences of certain matches (in "buckets"), so we
 * can process the sequence efficiently.
 *
 * Examples of sequences of hits might be:
 * - all hits in a document
 * - all consecutive hits in a document
 *
 * This way we can retrieve hits and perform some operation on them (like
 * sorting or retrieving some extra information).
 *
 * Note that with this class, "bucketing" is only possible with consecutive hits
 * from the Spans object. If you want other kinds of hit buckets (containing
 * non-consecutive spans), you should just extend SpansInBuckets, not this class.
 *
 * Also, SpansInBuckets assumes all hits in a bucket are from a single document.
 *
 */
abstract class SpansInBucketsAbstract extends SpansInBuckets {

    protected abstract class AbstractBucket implements Bucket {
        /** Starts and ends of hits in our bucket */
        protected final LongList startsEnds = new LongArrayList(LIST_INITIAL_CAPACITY);

        /**
         * For each hit we fetched, store the match info (e.g. captured groups, relations),
         * so we don't lose this information.
         */
        protected ObjectArrayList<MatchInfo[]> matchInfos = null;

        /**
         * For each hit we fetched, store the active relation info, if any.
         */
        protected ObjectArrayList<RelationInfo> activeRelationPerHit = null;

        @Override
        public int size() {
            return startsEnds.size();
        }

        public long getStartEndAsLong(int indexInBucket) {
            return startsEnds.getLong(indexInBucket);
        }

        @Override
        public int startPosition(int indexInBucket) {
            return (int)(startsEnds.getLong(indexInBucket) >> 32);
        }

        @Override
        public int endPosition(int indexInBucket) {
            return (int) startsEnds.getLong(indexInBucket);
        }

        @Override
        public MatchInfo[] matchInfos(int indexInBucket) {
            return matchInfos.get(indexInBucket);
        }

        @Override
        public RelationInfo relationInfo(int indexInBucket) {
            return activeRelationPerHit.get(indexInBucket);
        }

        private void clear() {
            startsEnds.clear();
            if (doMatchInfo) {
                if (matchInfos == null)
                    matchInfos = new ObjectArrayList<>(LIST_INITIAL_CAPACITY);
                else
                    matchInfos.clear();
                if (activeRelationPerHit == null)
                    activeRelationPerHit = new ObjectArrayList<>(LIST_INITIAL_CAPACITY);
                else
                    activeRelationPerHit.clear();
            }
        }

        /**
         * Subclasses should override this to gather the hits they wish to put in the
         * next bucket.
         *
         * Upon entering this method, the source spans is at the last unused hit (or the
         * first hit in a new document). At the end, it should be at the first hit that
         * doesn't fit in the bucket (or beyond the last hit, i.e.
         * Spans.NO_MORE_POSITIONS).
         *
         */
        public void gatherHits() throws IOException {
            // (override in subclass)
            //SpansInBucketsAbstract.this.gatherHits();
        }

        @Override
        public int gatherHitsInternal() throws IOException {
            assert positionedAtHitIfPositionedInDoc();
            doMatchInfo = hitQueryContext != null && (clauseCapturesMatchInfo && hitQueryContext.numberOfMatchInfos() > 0 ||
                    hitQueryContext.hasRelationCaptures());
            clear();
            assert(source.startPosition() >= 0 && source.startPosition() != Spans.NO_MORE_POSITIONS);
            gatherHits();
            assert(source.startPosition() >= 0);
            assert positionedAtHitIfPositionedInDoc();
            return source.docID();
        }

        protected void addHitFromSource() {
            assert positionedAtHitIfPositionedInDoc();
            assert source.startPosition() >= 0 && source.startPosition() != Spans.NO_MORE_POSITIONS;
            assert source.endPosition() >= 0 && source.endPosition() != Spans.NO_MORE_POSITIONS;
            long span = ((long)source.startPosition() << 32) | source.endPosition();
            if (doMatchInfo) {
                // Store match information such as captured groups and active relation (if any)
                int n = hitQueryContext == null ? 0 : hitQueryContext.numberOfMatchInfos();
                MatchInfo[] matchInfo = new MatchInfo[n];
                source.getMatchInfo(matchInfo);
                RelationInfo relationInfo = source.getRelationInfo();
                add(span, matchInfo, relationInfo);
            } else {
                add(span);
            }
            assert positionedAtHitIfPositionedInDoc();
        }

        void add(long span) {
            startsEnds.add(span);
        }

        public void add(long span, MatchInfo[] matchInfo, RelationInfo relationInfo) {
            add(span);
            matchInfos.add(matchInfo);
            activeRelationPerHit.add(relationInfo == null ? null : relationInfo.copy());
        }
    }

    /**
     * Construct the spans in buckets object.
     *
     * @param source (startpoint-sorted) source spans
     */
    protected SpansInBucketsAbstract(BLSpans source) {
        super(source);
    }

    public abstract String toString();

}
