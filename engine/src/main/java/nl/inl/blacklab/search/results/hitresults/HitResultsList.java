package nl.inl.blacklab.search.results.hitresults;

import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SampleParameters;
import nl.inl.blacklab.search.results.WindowStats;
import nl.inl.blacklab.search.results.hits.Hits;
import nl.inl.blacklab.search.results.stats.MaxStats;
import nl.inl.blacklab.search.results.stats.ResultsStats;
import nl.inl.blacklab.search.results.stats.ResultsStatsSaved;

/**
 * An immutable list of hits.
 */
public class HitResultsList extends HitResultsWithHitsInternal {

    private final ResultsStats hitsStats;

    private final ResultsStats docsStats;

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
    protected HitResultsList(QueryInfo queryInfo, Hits hits) {
        this(queryInfo, hits, -1, -1, MaxStats.NOT_EXCEEDED);
    }

    /**
     * Make a wrapper Hits object for a list of Hit objects.
     *
     * Does not copy the list, but reuses it.
     *
     * @param queryInfo query info
     * @param hits the list of hits to wrap
     * @param hitsCounted number of hits counted so far, or -1 if same as number processed
     * @param docsCounted number of documents counted so far, or -1 if same as number processed
     */
    protected HitResultsList(QueryInfo queryInfo, Hits hits, long hitsCounted, long docsCounted, MaxStats maxStats) {
        super(queryInfo, hits);
        long hitsProcessed = hits.size();
        long docsProcessed = hits.countDocs();
        if (hitsCounted < 0)
            hitsCounted = hitsProcessed;
        if (docsCounted < 0)
            docsCounted = docsProcessed;
        hitsStats = new ResultsStatsSaved(hitsProcessed, hitsCounted, maxStats);
        docsStats = new ResultsStatsSaved(docsProcessed, docsCounted, maxStats);
    }

    /**
     * Construct a HitResultsList from all its components.
     *
     * Should only be used internally.
     */
    protected HitResultsList(
            QueryInfo queryInfo,
            Hits hits,
            WindowStats windowStats,
            SampleParameters sampleParameters,
            ResultsStats hitsStats,
            ResultsStats docsStats) {
        super(queryInfo, hits);
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
        return "HitResultsList";
    }

    @Override
    public final boolean ensureResultsRead(long number) {
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
