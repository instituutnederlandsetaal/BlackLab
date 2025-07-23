package nl.inl.blacklab.search.results;

import java.util.Objects;

/** Information about whether we reached the limit of processing/counting */
public class MaxStatsStatic implements MaxStats {

    /** If true, we've stopped retrieving hits because there are more than the
     * maximum we've set. */
    private final boolean maxHitsProcessed;

    /** If true, we've stopped counting hits because there are more than the maximum
     * we've set. */
    private final boolean maxHitsCounted;

    MaxStatsStatic(boolean maxHitsProcessed, boolean maxHitsCounted) {
        super();
        this.maxHitsProcessed = maxHitsProcessed;
        this.maxHitsCounted = maxHitsCounted;
    }

    @Override
    public boolean hitsProcessedExceededMaximum() {
        return maxHitsProcessed;
    }

    @Override
    public boolean hitsCountedExceededMaximum() {
        return maxHitsCounted;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        MaxStatsStatic maxStats = (MaxStatsStatic) o;
        return maxHitsProcessed == maxStats.maxHitsProcessed && maxHitsCounted == maxStats.maxHitsCounted;
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxHitsProcessed, maxHitsCounted);
    }

    @Override
    public String toString() {
        return "MaxStats(" + hitsProcessedExceededMaximum() + ", " + hitsCountedExceededMaximum() + ")";
    }
}
