package nl.inl.blacklab.search.results.hits;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import nl.inl.blacklab.search.textpattern.CompleteQuery;

/** A simple container representing a group of hits, some or all of which may be stored. */
public class Group {

    /** Stored hits in this group, up to the configured maximum. */
    private final HitsMutable storedHits;

    /** Total number of hits in the group, including any we haven't stored.  */
    private long totalNumberOfHits;

    /** Total number of docs in the group.  */
    private int totalNumberOfDocs;

    /** Query to get hits in this group, or null if unknown */
    private final CompleteQuery hitsInGroupQuery;

    /** Doc ids in this group (stored or not), so we can get an accurate number of documents in the group.
     *  Only valid for segment groups and while !isFinished. */
    private IntSet docIdsInGroup = new IntOpenHashSet();

    /** Have all hits been added to this group? */
    private boolean isFinished = false;

    public Group(HitsMutable storedHits, long totalNumberOfHits, int totalNumberOfDocs, CompleteQuery hitsInGroupQuery) {
        this.storedHits = storedHits;
        this.totalNumberOfHits = totalNumberOfHits;
        this.totalNumberOfDocs = totalNumberOfDocs;
        this.hitsInGroupQuery = hitsInGroupQuery;
    }

    public HitsMutable getStoredHits() {
        return storedHits;
    }

    public long getTotalNumberOfHits() {
        return totalNumberOfHits;
    }

    public int getTotalNumberOfDocs() {
        return totalNumberOfDocs;
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
        totalNumberOfDocs += segmentGroup.totalNumberOfDocs;
        return this;
    }

    public CompleteQuery getHitsInGroupQuery() {
        return hitsInGroupQuery;
    }

    public void add(EphemeralHit hit, boolean storeHit) {
        assert !isFinished;
        if (storeHit)
            storedHits.add(hit);
        totalNumberOfHits++;
        if (docIdsInGroup.add(hit.doc()))
            totalNumberOfDocs++;
        assert sanityCheck();
    }

    public void finishGroup() {
        isFinished = true;
        docIdsInGroup = null;
    }

    public boolean sanityCheck() {
        if (storedHits.size() > totalNumberOfHits)
            throw new IllegalStateException(
                    "Stored hits size (" + storedHits.size() + ") is greater than total number of hits ("
                            + totalNumberOfHits + ")");

        if (totalNumberOfDocs > totalNumberOfHits)
            throw new IllegalStateException(
                    "Total number of docs (" + totalNumberOfDocs + ") is greater than total number of hits ("
                            + totalNumberOfHits + ")");

        if (totalNumberOfDocs == 0) {
            if (totalNumberOfHits > 0)
                throw new IllegalStateException(
                        "Total number of docs is 0 but total number of hits is " + totalNumberOfHits);
            if (!storedHits.isEmpty())
                throw new IllegalStateException(
                        "Total number of docs is 0 but stored hits size is " + storedHits.size());
        }
        return true;
    }
}
