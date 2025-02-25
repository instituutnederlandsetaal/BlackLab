package nl.inl.blacklab.search.lucene;

import nl.inl.blacklab.search.lucene.SpansAndFiltered.SpansAndFilter;

/**
 * Filter factory for filtering our hits
 */
public interface SpansAndFilterFactory {
    SpansAndFilter create(BLSpans spans, SpansInBuckets[] subSpans, int[] indexInBucket);

    boolean equals(Object obj);

    int hashCode();

    default String name() {
        return getClass().getSimpleName().replaceAll("^SpansAndFilterFactory", "");
    }

    default SpansInBuckets bucketize(BLSpans blSpans) {
        return new SpansInBucketsSameStartEnd(blSpans);
    }
}
