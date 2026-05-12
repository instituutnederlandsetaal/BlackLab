package nl.inl.blacklab.server.lib;

import java.util.List;
import java.util.Optional;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.results.SampleParameters;
import nl.inl.blacklab.search.results.SearchSettings;
import nl.inl.blacklab.search.results.hitresults.HitGroupScorer;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.searches.SearchCount;
import nl.inl.blacklab.searches.SearchDocGroups;
import nl.inl.blacklab.searches.SearchDocs;
import nl.inl.blacklab.searches.SearchEmpty;
import nl.inl.blacklab.searches.SearchFacets;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.jobs.ContextSettings;
import nl.inl.blacklab.server.jobs.HitSortSettings;
import nl.inl.blacklab.server.jobs.WindowSettings;

/**
 * Represents a webservice request, with more logic than just the query parameters.
 * <p>
 * Extends the QueryParams interface with methods that instantiate searches
 * based on the parameter values.
 * <p>
 * Should probably be refactored so there's a separate class for each operation, with
 * just the parameters relevant to that operation.
 */
public interface WebserviceParams extends QueryParams {

    static SearchDocs getSubcorpusSearch(WebserviceParams params) {
        Query docFilterQuery = params.filterQuery();
        if (docFilterQuery == null) {
            docFilterQuery = params.blIndex().getAllRealDocsQuery();
        }
        SearchEmpty search = params.blIndex().search(params.getAnnotatedField(), params.useCache());
        return search.findDocuments(docFilterQuery);
    }

    BlackLabIndex blIndex();

    boolean hasPattern() throws BlsException;

    /**
     * The pattern as passed by the user.
     *
     * This excludes the (optionally added) within clause for context.
     *
     * E.g. if the user has passed context=s, we add within &lt;s/&gt; to the
     * query so we can capture the relevant sentence span for the requested
     * context. This is a separate method because we don't want to report
     * this query with the additional clause in the response.
     *
     * @return original query without the optionally added within clause
     * @throws BlsException
     */
    Optional<TextPattern> patternNoWithinContextTag() throws BlsException;

    /**
     * The pattern to find in the corpus.
     *
     * This includes the (optionally added) within clause for context.
     * E.g. if the user has passed context=s, we add within &lt;s/&gt; to the
     * query so we can capture the relevant sentence span for the requested
     * context. This is a separate method because we don't want to report
     * this query with the additional clause in the response.
     *
     * @return query with optionally added within clause
     * @throws BlsException
     */
    Optional<TextPattern> pattern() throws BlsException;

    @Override
    String getDocPid();

    Query filterQuery() throws BlsException;

    WindowSettings windowSettings(boolean isCsv);

    HitSortSettings hitsSortSettings();

    SampleParameters sampleSettings();

    boolean hasFacets();

    SearchSettings searchSettings();

    ContextSettings contextSettings();

    boolean useCache();

    AnnotatedField getAnnotatedField();

//    /**
//     * @return hits - filtered then sorted then sampled
//     */
//    SearchHits hitsSample() throws BlsException;

    SearchDocs docsSorted() throws BlsException;

    SearchCount docsCount() throws BlsException;

    SearchDocs docs() throws BlsException;

//    SearchHitGroups hitsGroupedStats() throws BlsException;

    SearchDocGroups docsGrouped() throws BlsException;

    /**
     * Return our subcorpus.
     * The subcorpus is defined as all documents satisfying the metadata query.
     * If no metadata query is given, the subcorpus is all documents in the corpus.
     *
     * @return subcorpus
     */
    SearchDocs subcorpus() throws BlsException;

    SearchFacets facets() throws BlsException;

    HitGroupScorer getHitGroupScorer();

    @Override
    Optional<String> getInputFormat();

    List<SpanAndAttributeName> getSpanAttributes();

    record SpanAndAttributeName(String spanName, String attributeName) {
        public static SpanAndAttributeName fromString(String sa) {
            if (sa.equals("*"))
                return new SpanAndAttributeName("*", "*");
            String[] parts = sa.split("\\.", 2);
            if (parts.length == 2) {
                return new SpanAndAttributeName(parts[0], parts[1]);
            } else {
                throw new IllegalArgumentException("Invalid span attribute format (must be \"spanName.attrName\") : " + sa);
            }
        }
    }
}
