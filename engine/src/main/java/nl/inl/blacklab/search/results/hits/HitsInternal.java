package nl.inl.blacklab.search.results.hits;

/**
 * A list of simple hits.
 * <p>
 * Contrary to {@link Hits}, this only contains doc, start and end
 * for each hit, so no captured groups information, and no other
 * bookkeeping (hit/doc retrieved/counted stats, etc.).
 * <p>
 * This is a read-only interface.
 */
public interface HitsInternal extends HitsSimple {

}
