package nl.inl.blacklab.search.results;

import java.util.Objects;

/** Settings for our initial search, including how many hits we want to process/count at most. */
public final class SearchSettings {
    
    public static SearchSettings get(long maxHitsToProcess, long maxHitsToCount, long fiMatchFactor) {
        return new SearchSettings(maxHitsToProcess, maxHitsToCount, fiMatchFactor);
    }

    public static SearchSettings get(long maxHitsToProcess, long maxHitsToCount) {
        return new SearchSettings(maxHitsToProcess, maxHitsToCount, -1);
    }

    public static SearchSettings defaults() {
        return new SearchSettings(DEFAULT_MAX_PROCESS, DEFAULT_MAX_COUNT, -1);
    }
    
    /** How many hits to process by default */
    public static final int DEFAULT_MAX_PROCESS = 10_000_000;
    
    /** How many hits to count by default */
    public static final int DEFAULT_MAX_COUNT = Results.NO_LIMIT;
    
    /**
     * Stop processing hits after this number. (Results.NO_LIMIT = don't stop
     * processing)
     * 
     * Even if we stop processing, we can still keep counting.
     */
    private final long maxHitsToProcess;

    /**
     * Stop counting hits after this number. (ResultsNO_LIMIT = don't stop counting)
     */
    private final long maxHitsToCount;
    
    /** Override FI match NFA factor, or -1 for default */
    private final long fiMatchFactor;

    /**
     * Get settings
     * @param maxHitsToProcess how many hits to process at most
     * @param maxHitsToCount how many hits to count at most
     */
    private SearchSettings(long maxHitsToProcess, long maxHitsToCount, long fiMatchFactor) {
        this.maxHitsToProcess = maxHitsToProcess;
        this.maxHitsToCount = maxHitsToCount;
        this.fiMatchFactor = fiMatchFactor;
    }
    
    public SearchSettings withMaxHitsToProcess(long n) {
        return get(n, maxHitsToCount, fiMatchFactor);
    }

    public SearchSettings withMaxHitsToCount(long n) {
        return get(maxHitsToProcess, n, fiMatchFactor);
    }

    public SearchSettings withFiMatchFactor(long n) {
        return get(maxHitsToProcess, maxHitsToCount, n);
    }

    /** @return the maximum number of hits to retrieve. */
    public long maxHitsToProcess() {
        return maxHitsToProcess;
    }

    /** @return the maximum number of hits to count. */
    public long maxHitsToCount() {
        return maxHitsToCount;
    }

    public long fiMatchFactor() {
        return fiMatchFactor;
    }

    @Override
    public String toString() {
        return "SearchSettings(" + maxHitsToProcess + ", " + maxHitsToCount + ", " + fiMatchFactor + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SearchSettings that = (SearchSettings) o;
        return maxHitsToProcess == that.maxHitsToProcess && maxHitsToCount == that.maxHitsToCount && fiMatchFactor == that.fiMatchFactor;
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxHitsToProcess, maxHitsToCount, fiMatchFactor);
    }
}
