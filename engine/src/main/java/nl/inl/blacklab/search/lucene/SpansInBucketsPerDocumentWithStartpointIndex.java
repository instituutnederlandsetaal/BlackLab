package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.search.spans.Spans;
import org.eclipse.collections.api.map.primitive.MutableIntLongMap;
import org.eclipse.collections.impl.factory.primitive.IntLongMaps;

/**
 * Each bucket contains all the matches in a single document, startpoint sorted.
 *
 * There's also an index of startpoints to the matches that start there, so we
 * can quickly find the matches that start at a certain position. This is useful
 * for SpansRepetition, which needs to find connecting matches from the same clause.
 */
class SpansInBucketsPerDocumentWithStartpointIndex extends SpansInBucketsPerDocument {

    /** For each start position, keep track of the first index and count in the bucket. */
    MutableIntLongMap startPositionIndex = IntLongMaps.mutable.empty();

    int curStart = -1;

    int curStartFirstIndex = -1;

    int curStartCount = -1;

    public SpansInBucketsPerDocumentWithStartpointIndex(BLSpans source) {
        super(source);
        setBucket(new PerDocBucket() {
            @Override
            public void gatherHits() throws IOException {
                do {
                    addHitFromSource();
                    int index = bucketSize() - 1;
                    int start = startPosition(index);

                    if (curStart == start) {
                        // Keep track of how many hits there are at this start position.
                        curStartCount++;
                    } else {
                        // New start position.
                        if (curStart != -1) {
                            // Store offset and count of previous in index.
                            long value = ((long) curStartFirstIndex << 32) | curStartCount;
                            startPositionIndex.put(curStart, value);
                        }

                        // Start counting for the new start position.
                        curStart = start;
                        curStartFirstIndex = index;
                        curStartCount = 1;
                    }

                } while (source.nextStartPosition() != Spans.NO_MORE_POSITIONS);
            }
        });
    }

    @Override
    public int nextBucket() throws IOException {
        // Reset start position indexing
        startPositionIndex.clear();
        curStart = -1;
        curStartFirstIndex = -1;
        curStartCount = -1;

        // Gather hits and index start positions
        int returnValue = super.nextBucket();

        // Make sure we index the final start position as well.
        if (curStart != -1) {
            long value = ((long) curStartFirstIndex << 32) | curStartCount;
            startPositionIndex.put(curStart, value);
        }

        return returnValue;
    }

    /**
     * Get the first index and count for a given start position.
     *
     * @param start start position
     * @return a long with the first index in the bucket in the high 32 bits and the count in the low 32 bits
     */
    long indexAndCountForStartPoint(int start) {
        return startPositionIndex.get(start);
    }

    @Override
    public String toString() {
        return "SIB-DOC-SPI(" + source + ")";
    }

}
