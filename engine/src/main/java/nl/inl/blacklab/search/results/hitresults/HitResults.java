package nl.inl.blacklab.search.results.hitresults;

import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.Results;
import nl.inl.blacklab.search.results.SampleParameters;
import nl.inl.blacklab.search.results.SearchSettings;
import nl.inl.blacklab.search.results.WindowStats;
import nl.inl.blacklab.search.results.docs.DocResults;
import nl.inl.blacklab.search.results.hits.Hits;
import nl.inl.blacklab.search.results.stats.ResultsStats;

/**
 * A list of hits, optionally with captured groups.
 *
 * This interface is read-only.
 */
public interface HitResults extends Results {
    /**
     * Construct a Hits object from a SpanQuery.
     *
     * @param queryInfo      information about the original query
     * @param query          the query to execute to get the hits
     * @param searchSettings settings such as max. hits to process/count
     * @return hits found
     */
    static HitResults fromSpanQuery(QueryInfo queryInfo, BLSpanQuery query, SearchSettings searchSettings) {
        return new HitResultsFromQuery(queryInfo, query, searchSettings);
    }

    /**
     * Make a wrapper Hits object for a list of Hit objects.
     * <p>
     * Will create Hit objects from the arrays. Mainly useful for testing.
     *
     * @param queryInfo information about the original query
     * @param docs      doc ids
     * @param starts    hit starts
     * @param ends      hit ends
     * @return hits found
     */
    static HitResults list(QueryInfo queryInfo, int[] docs, int[] starts, int[] ends) {
        return new HitResultsList(queryInfo, Hits.fromLists(queryInfo.field(), docs, starts, ends));
    }

    static HitResults list(QueryInfo queryInfo, Hits hits) {
        return new HitResultsList(queryInfo, hits);
    }

    /**
     * Return a Hits object with a single hit
     *
     * @param queryInfo   query info
     * @param luceneDocId Lucene document id
     * @param start       start of hit
     * @param end         end of hit
     * @return hits object
     */
    static HitResults singleHit(QueryInfo queryInfo, int luceneDocId, int start, int end) {
        return list(queryInfo, new int[]{luceneDocId}, new int[]{start}, new int[]{end});
    }

    /**
     * Construct an immutable empty Hits object.
     *
     * @param queryInfo query info
     * @return hits found
     */
    static HitResults empty(QueryInfo queryInfo) {
        return new HitResultsList(queryInfo, Hits.empty(queryInfo.field(), null));
    }

    /**
     * Get access to the hits in this list.
     *
     * @return the hits interface
     */
    Hits getHits();

    /**
     * If this is a hits window, return the window stats.
     *
     * @return window stats, or null if this is not a hits window
     */
    WindowStats windowStats();

    /**
     * Get a window into this list of hits.
     * <p>
     * Use this if you're displaying part of the result set, like in a paging
     * interface. It makes sure BlackLab only works with the hits you want to
     * display and doesn't do any unnecessary processing on the other hits.
     * <p>
     * HitsWindow includes methods to assist with paging, like figuring out if there
     * hits before or after the window.
     *
     * @param first      first hit in the window (0-based)
     * @param windowSize size of the window
     * @return the window
     */
    HitResults window(long first, long windowSize);

    /**
     * Take a sample of hits by wrapping an existing Hits object.
     *
     * @param sampleParameters sample parameters
     * @return the sample
     */
    HitResults sample(SampleParameters sampleParameters);

    HitResults sorted(HitProperty sortProp);

    HitGroups group(HitProperty criteria, long maxResultsToStorePerGroup);

    /**
     * Select only the hits where the specified property has the specified value.
     *
     * @param property property to select on, e.g. "word left of hit"
     * @param value    value to select on, e.g. 'the'
     * @return filtered hits
     */
    HitResults filter(HitProperty property, PropertyValue value);

    @Override
    long numberOfResultObjects();

    /**
     * Count occurrences of context words around hit.
     *
     * @param annotation  what annotation to get collocations for
     * @param contextSize how many words around the hits to use
     * @param sensitivity what sensitivity to use
     * @param sort        sort the resulting collocations by descending frequency?
     * @return the frequency of each occurring token
     */
    TermFrequencyList collocations(Annotation annotation, ContextSize contextSize, MatchSensitivity sensitivity, boolean sort);

    /**
     * Return a per-document view of these hits.
     *
     * @param maxHits maximum number of hits to store per document
     * @return the per-document view.
     */
    DocResults perDocResults(long maxHits);

    ResultsStats docsStats();

}
