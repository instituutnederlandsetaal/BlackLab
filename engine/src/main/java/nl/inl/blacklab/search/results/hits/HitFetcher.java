package nl.inl.blacklab.search.results.hits;

import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.results.SearchSettings;

public interface HitFetcher {
    boolean ensureResultsReader(long number);

    boolean isDone();

    HitQueryContext getHitQueryContext();

    void fetchHits(HitCollector hitCollector);

    AnnotatedField field();

    SearchSettings getSearchSettings();
}
