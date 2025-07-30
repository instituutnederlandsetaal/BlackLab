package nl.inl.blacklab.search.results;

/**
 * An immutable list of hits.
 */
public class HitsList extends HitsAbstract {

    private final ResultsStats hitsStats;

    private final ResultsStatsSaved docsStats;

    /** Our window stats, if this is a window; null otherwise. */
    private WindowStats windowStats;

    /** Our sample parameters, if any. null if not a sample of a larger result set */
    private SampleParameters sampleParameters;

    /**
     * Make a wrapper Hits object for a list of Hit objects.
     *
     * Does not copy the list, but reuses it.
     *
     * @param queryInfo query info
     * @param hits the list of hits to wrap
     */
    protected HitsList(QueryInfo queryInfo, HitsInternal hits) {
        super(queryInfo, hits, false);

        // Count docs
        int prevDoc = -1;
        int docsCounted = 0;
        for (long i = 0; i < hits.size(); i++) {
            int docId = hits.doc(i);
            if (docId != prevDoc) {
                docsCounted++;
                prevDoc = docId;
            }
        }
        hitsStats = new ResultsStatsSaved(hitsInternal.size());
        docsStats = new ResultsStatsSaved(docsCounted);
    }

    /**
     * Construct a HitsList from all its components.
     *
     * Should only be used internally.
     */
    protected HitsList(
                       QueryInfo queryInfo,
                       HitsInternal hits,
                       WindowStats windowStats,
                       SampleParameters sampleParameters,
                       ResultsStats hitsStats,
                       ResultsStats docsStats) {
        super(queryInfo, hits, false);
        this.windowStats = windowStats;
        this.sampleParameters = sampleParameters;
        assert hitsStats.processedSoFar() == hits.size();
        this.hitsStats = hitsStats.save();
        this.docsStats = docsStats.save();
    }

    @Override
    public ResultsStats resultsStats() {
        return hitsStats;
    }

    @Override
    public ResultsStats docsStats() {
        return docsStats;
    }

    @Override
    public String toString() {
        return "HitsList";
    }

    /**
     * Ensure that we have read at least as many hits as specified in the parameter.
     *
     * @param number the minimum number of hits that will have been read when this
     *            method returns (unless there are fewer hits than this); if
     *            negative, reads all hits
     */
    @Override
    protected final boolean ensureResultsRead(long number) {
        return size() >= number; // all results have been read
    }

    public SampleParameters sampleParameters() {
        return sampleParameters;
    }

    @Override
    public WindowStats windowStats() {
        return windowStats;
    }

    public MaxStats maxStats() {
        return MaxStats.NOT_EXCEEDED;
    }
}
