package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntComparator;

/**
 * Wrap a Spans to retrieve hits per document, so we can process all matches in
 * a document efficiently.
 *
 * Hits are sorted by either start or end point.
 */
class SpansInBucketsPerDocumentSorted extends SpansInBucketsPerDocument {

    private class PerDocSortedBucket extends PerDocBucket {
        private final boolean sortByStartPoint;

        public PerDocSortedBucket(boolean sortByStartPoint) {
            this.sortByStartPoint = sortByStartPoint;
        }

        private int ord(int index) {
            return sortIndexes.getInt(index);
        }

        @Override
        public long getStartEndAsLong(int indexInBucket) {
            // NOTE: this will return the original, not the sorted
            //       order! This is intentional, because we use it
            //       during sorting.
            return super.getStartEndAsLong(indexInBucket);
        }

        @Override
        public int startPosition(int indexInBucket) {
            return super.startPosition(ord(indexInBucket));
        }

        @Override
        public int endPosition(int indexInBucket) {
            return super.endPosition(ord(indexInBucket));
        }

        @Override
        public MatchInfo[] matchInfos(int indexInBucket) {
            return super.matchInfos(ord(indexInBucket));
        }

        @Override
        public RelationInfo relationInfo(int indexInBucket) {
            return super.relationInfo(ord(indexInBucket));
        }

        @Override
        public void gatherHits() throws IOException {
            super.gatherHits();

            // Sort by start- or endpoint
            sortIndexes.clear();
            sortIndexes.ensureCapacity(bucketSize());
            for (int i = 0; i < size(); i++)
                sortIndexes.add(i);
            IntArrays.quickSort(sortIndexes.elements(), 0, sortIndexes.size(), sortByStartPoint ? cmpStartPoint : cmpEndPoint);
        }
    }

    private static int compareEnds(long a, long b) {
        int ea = (int)a;
        int eb = (int)b;
        if (ea == eb) {
            // Identical end points; start point is tiebreaker
            return (int)(a >> 32) - (int)(b >> 32);
        }
        return ea - eb;
    }

    private final IntComparator cmpStartPoint = (i1, i2) -> {
        long a = ((PerDocBucket)bucket).getStartEndAsLong(i1);
        long b = ((PerDocBucket)bucket).getStartEndAsLong(i2);
        return Long.compare(a, b);
    };

    private final IntComparator cmpEndPoint = (i1, i2) -> {
        long a = ((PerDocBucket)bucket).getStartEndAsLong(i1);
        long b = ((PerDocBucket)bucket).getStartEndAsLong(i2);
        return compareEnds(a, b);
    };

    private final IntArrayList sortIndexes = new IntArrayList(LIST_INITIAL_CAPACITY);

    private final SpanGuaranteesAdapter guarantees;

    public SpansInBucketsPerDocumentSorted(BLSpans source, boolean sortByStartPoint) {
        super(source);
        setBucket(new PerDocSortedBucket(sortByStartPoint));
        this.guarantees = new SpanGuaranteesAdapter(source.guarantees()) {
            @Override
            public boolean hitsStartPointSorted() {
                return sortByStartPoint || source.guarantees().hitsAllSameLength();
            }

            @Override
            public boolean hitsEndPointSorted() {
                return !sortByStartPoint || source.guarantees().hitsAllSameLength();
            }
        };
    }

    @Override
    public SpanGuarantees guarantees() {
        return this.guarantees;
    }

    @Override
    public String toString() {
        return "SIB-DOC-SORT(" + source + ")";
    }
}
