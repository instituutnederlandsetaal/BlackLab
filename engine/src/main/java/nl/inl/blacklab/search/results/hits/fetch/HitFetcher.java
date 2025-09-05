package nl.inl.blacklab.search.results.hits.fetch;

import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.results.stats.ResultsStats;

/** Fetches hits from index segments in parallel,
 * reporting them to HitCollector/HitProcessor */
public interface HitFetcher {

    /** Phases of the hit fetching process. */
    enum Phase {
        STORING_AND_COUNTING,
        COUNTING_ONLY,
        DONE
    }

    /** Block until this many results are available or there are no more results.
     *
     * If we hit the maximum number of results to process before reaching the specified
     * number, we stop reading more results and return false.
     *
     * @param number minimum number of results we want available. If negative, read all hits.
     * @return true if the requested number was reached, false if we were done before reaching it
     */
    boolean ensureResultsRead(long number);

    HitQueryContext getHitQueryContext();

    /** Actually fetch the hits and pass them to the collector.
     *
     * @param filter which hits to include (or null for all)
     * @param hitCollector where to report the hits
     */
    void fetchHits(HitFilter filter, HitCollector hitCollector);

    AnnotatedField field();

    ResultsStats hitsStats();

    ResultsStats docsStats();
}
