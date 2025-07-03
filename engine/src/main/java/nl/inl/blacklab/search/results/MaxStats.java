package nl.inl.blacklab.search.results;

/** Information about whether we reached the limit of processing/counting */
public class MaxStats {

    public static final MaxStats NOT_EXCEEDED = new MaxStats(false, false);

    /** If true, we've stopped retrieving hits because there are more than the
     * maximum we've set. */
    private final boolean maxHitsProcessed;

    /** If true, we've stopped counting hits because there are more than the maximum
     * we've set. */
    private final boolean maxHitsCounted;

    public MaxStats(boolean maxHitsProcessed, boolean maxHitsCounted) {
        super();
        this.maxHitsProcessed = maxHitsProcessed;
        this.maxHitsCounted = maxHitsCounted;
    }

    public MaxStats() {
        this(false, false);
    }

    public boolean hitsProcessedExceededMaximum() {
        return maxHitsProcessed;
    }

    public boolean hitsCountedExceededMaximum() {
        return maxHitsCounted;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (maxHitsCounted ? 1231 : 1237);
        result = prime * result + (maxHitsProcessed ? 1231 : 1237);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        MaxStats other = (MaxStats) obj;
        return maxHitsCounted == other.maxHitsCounted && maxHitsProcessed == other.maxHitsProcessed;
    }

    @Override
    public String toString() {
        return "MaxStats(" + maxHitsProcessed + ", " + maxHitsCounted + ")";
    }
}
