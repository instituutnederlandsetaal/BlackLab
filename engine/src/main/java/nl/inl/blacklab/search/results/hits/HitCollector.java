package nl.inl.blacklab.search.results.hits;

import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.search.results.stats.ResultsStatsPassive;

/**
 * Collects hits from multiple segments, keeping track of totals. Works with HitProcessor.
 */
interface HitCollector {

    /**
     * Get hit processor for this segment.
     */
    HitProcessor getHitProcessor(LeafReaderContext lrc);

    void setDone();

    ResultsStatsPassive resultsStats();

    ResultsStatsPassive docsStats();

    long globalHitsSoFar();
}
