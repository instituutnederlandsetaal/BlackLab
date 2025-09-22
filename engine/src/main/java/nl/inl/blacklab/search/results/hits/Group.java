package nl.inl.blacklab.search.results.hits;

/** A simple container representing a group of hits, some or all of which may be stored. */
public class Group {

    /** Stored hits in this group, up to the configured maximum. */
    HitsMutable storedHits;

    /** Total number of hits in the group, including any we haven't stored.  */
    long totalNumberOfHits;

    public Group(HitsMutable storedHits, int totalNumberOfHits) {
        this.storedHits = storedHits;
        this.totalNumberOfHits = totalNumberOfHits;
    }

    public HitsMutable getStoredHits() {
        return storedHits;
    }

    public long getTotalNumberOfHits() {
        return totalNumberOfHits;
    }

    /**
     * Merge another segment group into this one.
     *
     * @param segmentGroup the group to merge into this one
     * @param maxValuesToStorePerGroup maximum number of hits to store per group (use
     *    {@link nl.inl.blacklab.search.results.Results#NO_LIMIT} for no limit)
     * @return this
     */
    public Group merge(Group segmentGroup, long maxValuesToStorePerGroup) {
        if (maxValuesToStorePerGroup < 0)
            throw new IllegalArgumentException("maxValuesToStorePerGroup must be greater than 0");
        if (storedHits.size() + segmentGroup.storedHits.size() > maxValuesToStorePerGroup) {
            // Can we hold any more hits?
            if (storedHits.size() < maxValuesToStorePerGroup) {
                // We can add a limited number of hits, so we need to trim the segment group
                Hits hitsToAdd = segmentGroup.storedHits
                        .sublist(0, maxValuesToStorePerGroup - storedHits.size());
                storedHits.addAll(hitsToAdd);
            }
        } else {
            // Just add all the hits
            storedHits.addAll(segmentGroup.getStoredHits());
        }
        totalNumberOfHits += segmentGroup.totalNumberOfHits;
        return this;
    }
}
