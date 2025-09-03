package nl.inl.blacklab.search.results.hits.fetch;

import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.lucene.HitQueryContext;

/** Fetches hits from index segments in parallel,
 * reporting them to HitCollector/HitProcessor */
public interface HitFetcher {
    boolean ensureResultsReader(long number);

    boolean isDone();

    HitQueryContext getHitQueryContext();

    void fetchHits(HitCollector hitCollector);

    AnnotatedField field();

    long getMaxHitsToProcess();

    long getMaxHitsToCount();

    enum Phase {
        STORING_AND_COUNTING,
        COUNTING_ONLY,
        DONE
    }
}
