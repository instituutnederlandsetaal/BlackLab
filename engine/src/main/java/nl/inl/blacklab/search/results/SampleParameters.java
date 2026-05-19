package nl.inl.blacklab.search.results;

import java.util.Objects;
import java.util.Random;

/**
 * Parameters that determine how much to sample and whether to use a fixed seed.
 */
public class SampleParameters {
    
    private static final Random random = new Random(); 
    
    public static SampleParameters percentage(double percentageOfHits, long seedValueSet) {
        return new SampleParameters(true, percentageOfHits, 0, true, seedValueSet);
    }
    
    public static SampleParameters percentage(double percentageOfHits) {
        return new SampleParameters(true, percentageOfHits, 0, false, 0);
    }
    
    public static SampleParameters fixedNumber(long numberOfHitsSet, long seedValueSet) {
        return new SampleParameters(false, 0, numberOfHitsSet, true, seedValueSet);
    }
    
    public static SampleParameters fixedNumber(long numberOfHitsSet) {
        return new SampleParameters(false, 0, numberOfHitsSet, false, 0);
    }
    
    final boolean isPercentage;
    
    private final double percentageOfHits;
    
    private final long numberOfHitsSet;
    
    private final boolean isFixedSeed;
    
    private final long seedValueSet;
    
    SampleParameters(boolean isPercentage, double percentageOfHits, long numberOfHitsSet, boolean isFixedSeed, long seedValueSet) {
        this.isPercentage = isPercentage;
        this.percentageOfHits = percentageOfHits;
        this.numberOfHitsSet = numberOfHitsSet;
        this.isFixedSeed = isFixedSeed;
        this.seedValueSet = isFixedSeed ? seedValueSet : random.nextLong();
        if (isPercentage && percentageOfHits < 0 || percentageOfHits > 1)
            throw new IllegalArgumentException("Sample percentage must be in the range 0-1");
        if (!isPercentage && numberOfHitsSet < 0)
            throw new IllegalArgumentException("Negative sample number specified");
    }
    
    // "Simple" interface
    // -----------------------------------------------------------------------------
    // Always returns a seed and a number of hits to sample, regardless of settings.
    
    /**
     * Get the seed for the sample operation.
     * 
     * This may either be a fixed seed set by the user, or a randomly chosen seed.
     * However, for an existing instance of SampleParameters, the seed won't ever change.
     * 
     * @return seed
     */
    public long seed() {
        return seedValueSet; 
    }
    
    /**
     * Get number of hits to sample, given total number of hits.
     * 
     * Always valid, regardless of isPercentage() setting.
     * 
     * @param totalNumberOfHits the total number of hits
     * @return number to sample
     */
    public long numberOfHits(long totalNumberOfHits) {
        return isPercentage() ? Math.round((float)percentageOfHits() * totalNumberOfHits) : numberOfHitsSet();
    }
    
    
    // More detailed interface
    // -----------------------------------------------------------------------------
    // Details about what type of sampling (percentage/number) was set, and if a fixed
    // seed value was set
    
    /**
     * Is this a relative sample amount?
     * 
     * If true, percentage() return the percentage of hits to sample.
     * 
     * If false, number() returns the absolute number of hits to sample.
     * 
     * @return true if this is relative, false if not
     */
    public boolean isPercentage() {
        return isPercentage;
    }
    
    /**
     * Get percentage of hits to sample.
     * 
     * Only valid if isRelative() return true.
     * 
     * @return desired percentage (actually fraction 0-1)
     */
    public double percentageOfHits() {
        return percentageOfHits;
    }
    
    /**
     * Get number of hits to sample.
     * 
     * Only valid if isRelative() return false.
     * 
     * @return desired number
     */
    public long numberOfHitsSet() {
        return numberOfHitsSet;
    }
    
    /**
     * Was a fixed seed set?
     * 
     * If not, a random seed was chosen. In either case, call {@link #seed()}
     * to get the seed used. The return value will not change for an instance
     * of SampleParameters.
     * 
     * @return true if a fixed seed value was set, false if it wasn't
     */
    public boolean isFixedSeed() {
        return isFixedSeed;
    }
    
    @Override
    public String toString() {
        return "sample=" + (isPercentage ? String.format("%.1f%%", percentageOfHits) : Long.toString(numberOfHitsSet)) +
                ", seed=" + seedValueSet + (isFixedSeed ? " (FIXED SEED)" : " (RANDOM SEED)");
    }

    @Override
    public int hashCode() {
        return Objects.hash(isPercentage, percentageOfHits, numberOfHitsSet, isFixedSeed, seedValueSet);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        SampleParameters other = (SampleParameters) obj;
        if (isFixedSeed != other.isFixedSeed)
            return false;
        if (isPercentage != other.isPercentage)
            return false;
        if (numberOfHitsSet != other.numberOfHitsSet)
            return false;
        if (Double.doubleToLongBits(percentageOfHits) != Double.doubleToLongBits(other.percentageOfHits))
            return false;
        if (seedValueSet != other.seedValueSet)
            return false;
        return true;
    }
    
    
}
