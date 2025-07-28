package nl.inl.blacklab.search.results;

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
    protected HitsMutable(QueryInfo queryInfo) {
        this(queryInfo, HitsInternal.create(queryInfo.field(), null, -1, true, true));
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
    protected HitsMutable(QueryInfo queryInfo, HitsInternalMutable hits) {
        super(queryInfo, hits);
        if (hits == null)
            throw new IllegalArgumentException("HitsMutable must be constructed with valid hits object (got null)");
        hitsInternalMutable = hits;
    }

    @Override
    public WindowStats windowStats() {
        return null;
    }

}
