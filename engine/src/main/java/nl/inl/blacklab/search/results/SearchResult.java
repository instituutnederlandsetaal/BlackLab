package nl.inl.blacklab.search.results;

/** Base interface for all possible search results. */
public interface SearchResult {
    
    /**
     * How many result objects does this search store?
     * 
     * Used for estimating the cache size. Hits make up most of the storage
     * of results objects, and they should take 24 bytes of memory, so we can use
     * this number to make a reasonable approximation of memory usage.
     * 
     * Note that this number can be misleading, because Hit objects are
     * shared between results objects. But it's the best we have at the moment.
     * 
     * @return how many hits are stored in this result object
     */
    long numberOfResultObjects();

}
