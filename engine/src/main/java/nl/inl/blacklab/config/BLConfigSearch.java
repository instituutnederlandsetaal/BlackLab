package nl.inl.blacklab.config;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.lucene.optimize.ClauseCombinerNfa;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.Results;
import nl.inl.blacklab.search.results.SearchSettings;

public class BLConfigSearch {
    private BLConfigCollator collator = new BLConfigCollator();

    private int contextSize = 5;

    private long maxHitsToRetrieve = 5_000_000;

    private long maxHitsToCount = 10_000_000;

    private long fiMatchFactor = ClauseCombinerNfa.defaultForwardIndexMatchingThreshold;

    /** Should result sets larger than the maximum array size (roughly 2^31) be supported?
     *
     * If you don't need this, you can disable it for slightly better performance.
     */
    private boolean enableHugeResultSets = true;

    public BLConfigCollator getCollator() {
        return collator;
    }

    @SuppressWarnings("unused")
    public void setCollator(BLConfigCollator collator) {
        this.collator = collator;
    }

    public int getContextSize() {
        return contextSize;
    }

    public void setContextSize(int contextSize) {
        this.contextSize = contextSize;
    }

    public long getMaxHitsToRetrieve() {
        return maxHitsToRetrieve;
    }

    @SuppressWarnings("unused")
    public void setMaxHitsToRetrieve(int maxHitsToRetrieve) {
        this.maxHitsToRetrieve = maxHitsToRetrieve < 0 ? Results.NO_LIMIT : maxHitsToRetrieve;
    }

    public long getMaxHitsToCount() {
        return maxHitsToCount;
    }

    @SuppressWarnings("unused")
    public void setMaxHitsToCount(long maxHitsToCount) {
        this.maxHitsToCount = maxHitsToCount < 0 ? Results.NO_LIMIT : maxHitsToCount;
    }

    public long getFiMatchFactor() {
        return fiMatchFactor;
    }

    @SuppressWarnings("unused")
    public void setFiMatchFactor(int fiMatchFactor) {
        this.fiMatchFactor = fiMatchFactor;
    }

    public boolean isEnableHugeResultSets() {
        return enableHugeResultSets;
    }

    @SuppressWarnings("unused")
    public void setEnableHugeResultSets(boolean enableHugeResultSets) {
        this.enableHugeResultSets = enableHugeResultSets;
    }

    /**
     * Apply the search configuration settings to an opened index.
     *
     * @param index index to apply the configuration to
     */
    public void apply(BlackLabIndex index) {
        index.setCollator(getCollator().get());
        int maxContextSize = getContextSize();
        index.setDefaultContextSize(ContextSize.get(maxContextSize, ContextSize.maxSnippetLengthFromMaxContextSize(maxContextSize)));
        long maxHitsToProcess = getMaxHitsToRetrieve();
        long maxHitsToCount = getMaxHitsToCount();
        long fiMatchFactor = getFiMatchFactor();
        SearchSettings sett = SearchSettings.get(maxHitsToProcess, maxHitsToCount, fiMatchFactor);
        ClauseCombinerNfa.setNfaThreshold(fiMatchFactor);
        index.setSearchSettings(sett);
    }

}
