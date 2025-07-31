package nl.inl.blacklab.codec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Quickly look up a segment an id occurs in.
 *
 * Each segment has a base id, which represents the lowest id
 * in that segment. The segments should be sorted in base order,
 * and the first segment should have base 0 (or whatever the lowest
 * id is).
 *
 * Generic class to make testing easier and for reusability.
 */
public class SegmentLookup<T> {

    /** Segments to look up */
    private final List<T> segments;

    /** Base id for each segment  */
    private final List<Integer> ids;

    @FunctionalInterface
    interface SegmentBaseLookup<T> {
        int base(T segment);
    }

    public SegmentLookup(Collection<T> segments, SegmentBaseLookup<T> lookup) {
        this.segments = new ArrayList<>(segments);
        ids = segments.stream().map(lookup::base).toList();
    }

    /**
     * Find the segment a given id occurs in.
     *
     * Uses binary search to find the right segment.
     *
     * @param id (global) docId we're looking for
     * @return matching leafReaderContext, which gives us the leaf reader and docBase
     */
    public T forId(int id) {
        // Binary search for our segment
        int lo = 0, hi = ids.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2; // round up, so mid != lo (or we would loop forever)
            int base = ids.get(mid);
            if (base == id) {
                // This is the right segment.
                lo = hi = mid;
            } else if (base <= id) {
                // Our segment is this one or a higher one.
                lo = mid;
            } else {
                // Our segment is definitely before this one.
                hi = mid - 1;
            }
        }
        assert lo == hi;
        return segments.get(lo);
    }
}
