package nl.inl.blacklab.search.results;

import nl.inl.blacklab.search.lucene.MatchInfoDefs;

/**
 * A collection of matches being fetched as they are needed.
 *
 * Should be thread-safe and most methods are safe w.r.t. hits having been fetched.
 */
public abstract class HitsMutable extends HitsAbstract {

    /** Writable version of our HitsInternal object */
    protected final HitsInternalMutable hitsInternalMutable;

    /** Construct an empty Hits object.
     *
     * @param queryInfo query info for corresponding query
     */
    public HitsMutable(QueryInfo queryInfo) {
        this(queryInfo, HitsInternal.create(-1, true, true), null);
    }

    /**
     * Construct a Hits object from a hits array.
     *
     * NOTE: if you pass null, a new, mutable HitsArray is used.
     *
     * @param queryInfo query info for corresponding query
     * @param hits hits array to use for this object. The array is used as-is, not copied.
     * @param matchInfoDefs names of match info to store
     */
    public HitsMutable(QueryInfo queryInfo, HitsInternalMutable hits, MatchInfoDefs matchInfoDefs) {
        super(queryInfo, hits, matchInfoDefs);
        hitsInternalMutable = hits == null ? HitsInternal.create(-1, true, true) : hits;
    }

}
