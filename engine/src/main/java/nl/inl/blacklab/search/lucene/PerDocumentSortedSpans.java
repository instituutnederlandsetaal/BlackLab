package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Arrays;

import org.apache.lucene.queries.spans.SpanCollector;

/**
 * Sort the given Spans per document, according to the given comparator.
 */
final class PerDocumentSortedSpans extends BLFilterDocsSpans<SpansInBuckets> {

    private int curStart = -1;

    private int curEnd = -1;

    private final boolean removeDuplicates;

    private final boolean sortByStartPoint;

    private int indexInBucket = -2; // -2 == no bucket yet; -1 == just started a bucket

    private HitQueryContext hitQueryContext;

    PerDocumentSortedSpans(BLSpans src, boolean sortByStartPoint, boolean removeDuplicates) {
        // Wrap a HitsPerDocument and show it to the client as a normal, sequential Spans.
        super(new SpansInBucketsPerDocumentSorted(src, sortByStartPoint), new SpanGuaranteesAdapter(src.guarantees()) {
            @Override
            public boolean hitsStartPointSorted() {
                return sortByStartPoint || src.guarantees().hitsAllSameLength();
            }

            @Override
            public boolean hitsEndPointSorted() {
                return !sortByStartPoint || src.guarantees().hitsAllSameLength();
            }

            @Override
            public boolean hitsHaveUniqueStartEndAndInfo() {
                return removeDuplicates || super.hitsHaveUniqueStartEndAndInfo();
            }

            @Override
            public boolean hitsHaveUniqueStartEnd() {
                return (removeDuplicates && !src.hasMatchInfo()) || super.hitsHaveUniqueStartEnd();
            }
        });
        this.removeDuplicates = removeDuplicates;
        this.sortByStartPoint = sortByStartPoint;

        SpanGuarantees g = src.guarantees();
        if (removeDuplicates && g.hitsHaveUniqueStartEndAndInfo())
            throw new IllegalArgumentException("Uniqueness requested but hits are already unique");
        if (sortByStartPoint && g.hitsStartPointSorted()) {
            throw new IllegalArgumentException("Hits are already startpoint sorted, use SpansUnique instead");
        }
    }

    @Override
    public int startPosition() {
        if (indexInBucket < 0)
            return -1;
        if (indexInBucket >= in.bucketSize())
            return NO_MORE_POSITIONS;
        return curStart;
    }

    @Override
    public int endPosition() {
        if (indexInBucket < 0)
            return -1;
        if (indexInBucket >= in.bucketSize())
            return NO_MORE_POSITIONS;
        return curEnd;
    }

    @Override
    public int nextDoc() throws IOException {
        assert docID() != NO_MORE_DOCS;
        indexInBucket = -2;
        curStart = -1;
        curEnd = -1;
        return super.nextDoc();
    }

    @Override
    public int advance(int target) throws IOException {
        assert target >= 0 && target > docID();
        indexInBucket = -2;
        curStart = -1;
        curEnd = -1;
        return super.advance(target);
    }

    @Override
    protected boolean twoPhaseCurrentDocMatches() throws IOException {
        assert positionedInDoc();
        // If our clause matches, we match as well; we just reorder the matches.
        indexInBucket = -2; // no bucket yet
        curStart = -1;
        curEnd = -1;
        return true;
    }

    @Override
    public int nextStartPosition() throws IOException {
        assert startPosition() != NO_MORE_POSITIONS;
        if (!removeDuplicates) {
            // No need to eliminate duplicates
            if (indexInBucket == -2 || indexInBucket >= in.bucketSize() - 1) {
                // Bucket exhausted or no bucket yet; get one
                if (in.nextBucket() == SpansInBuckets.NO_MORE_BUCKETS) {
                    indexInBucket = SpansInBuckets.NO_MORE_BUCKETS;
                    return NO_MORE_POSITIONS;
                }
                indexInBucket = -1;
            }
            indexInBucket++;
            curStart = in.startPosition(indexInBucket);
            curEnd = in.endPosition(indexInBucket);
        } else {
            // Eliminate any duplicates
            int prevEnd;
            int prevStart;
            MatchInfo[] prevInfo = childClausesCaptureMatchInfo ? new MatchInfo[hitQueryContext.numberOfMatchInfos()] : null;
            MatchInfo[] curInfo = childClausesCaptureMatchInfo ? new MatchInfo[hitQueryContext.numberOfMatchInfos()] : null;
            do {
                if (indexInBucket == -2 || indexInBucket >= in.bucketSize() - 1) {
                    // Bucket exhausted or no bucket yet; get one
                    if (in.nextBucket() == SpansInBuckets.NO_MORE_BUCKETS) {
                        indexInBucket = SpansInBuckets.NO_MORE_BUCKETS;
                        return NO_MORE_POSITIONS;
                    }
                    indexInBucket = -1;
                }
                if (indexInBucket >= 0) {
                    prevStart = in.startPosition(indexInBucket);
                    prevEnd = in.endPosition(indexInBucket);
                    if (childClausesCaptureMatchInfo) {
                        Arrays.fill(prevInfo, null);
                        System.arraycopy(curInfo, 0, prevInfo, 0, prevInfo.length);
                        in.getMatchInfo(indexInBucket, prevInfo);
                    }
                } else {
                    prevStart = prevEnd = -1;
                    if (childClausesCaptureMatchInfo)
                        Arrays.fill(prevInfo, null);
                }
                indexInBucket++;
                curStart = in.startPosition(indexInBucket);
                curEnd = in.endPosition(indexInBucket);
                if (childClausesCaptureMatchInfo) {
                    Arrays.fill(curInfo, null);
                    in.getMatchInfo(indexInBucket, curInfo);
                }
            } while (prevStart == curStart && prevEnd == curEnd && (prevInfo == null || Arrays.equals(prevInfo, curInfo)));
        }
        return curStart;
    }

    @Override
    public String toString() {
        String name = "sort" + (sortByStartPoint ? "Start" : "End") + (removeDuplicates ? "Uniq" : "");
        return name + "(" + in + ")";
    }

    @Override
    public void passHitQueryContextToClauses(HitQueryContext context) {
        this.hitQueryContext = context;
        in.setHitQueryContext(context);
    }

    @Override
    public void getMatchInfo(MatchInfo[] matchInfo) {
        if (indexInBucket < 0 || indexInBucket >= in.bucketSize())
            return;
        in.getMatchInfo(indexInBucket, matchInfo);
    }

    @Override
    public boolean hasMatchInfo() {
        return in.hasMatchInfo();
    }

    @Override
    public RelationInfo getRelationInfo() {
        return in.getRelationInfo(indexInBucket);
    }

    @Override
    public void collect(SpanCollector collector) {
        // BucketedSpans doesn't collect payload. We could update it to do so for queries that need it,
        // but for now, we don't use payload beyond a "simple" Spans like SpansRelations.
    }

    @Override
    public int width() {
        return in.width();
    }


}
