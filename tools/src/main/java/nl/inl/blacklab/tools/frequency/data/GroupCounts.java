package nl.inl.blacklab.tools.frequency.data;

import java.io.Serializable;

/**
 * Counts of hits and docs while grouping.
 */
public final class GroupCounts implements Serializable {
    public int hits;
    public int docs;

    public GroupCounts(final int hits, final int docs) {
        this.hits = hits;
        this.docs = docs;
    }
}
