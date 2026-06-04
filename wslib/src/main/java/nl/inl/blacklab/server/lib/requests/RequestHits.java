package nl.inl.blacklab.server.lib.requests;

import java.util.List;
import java.util.Optional;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.HitGroupProperty;
import nl.inl.blacklab.resultproperty.HitGroupPropertySize;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.results.SampleParameters;
import nl.inl.blacklab.search.results.SearchSettings;
import nl.inl.blacklab.search.results.hitresults.ContextSize;
import nl.inl.blacklab.search.results.hitresults.HitGroupScorer;
import nl.inl.blacklab.search.textpattern.CompleteQuery;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.searches.SearchFacets;
import nl.inl.blacklab.searches.SearchHits;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.jobs.ContextSettings;
import nl.inl.blacklab.server.jobs.WindowSettings;
import nl.inl.blacklab.server.lib.ParamUtil;
import nl.inl.blacklab.server.lib.ParamsForResponse;
import nl.inl.blacklab.server.lib.QueryParams;
import nl.inl.blacklab.webservice.WebserviceOperation;
import nl.inl.blacklab.webservice.WsParam;

/**
 * A request for a hits search.
 * <p>
 * Searches the given pattern in the documents determined by the filterQuery, if any.
 * <p>
 * Can also filter hits by a property and value, sort the hits, and sample them,
 * if requested.
 *
 * @param searchField    Which annotated field we're searching
 * @param pattern        Pattern to search for
 * @param adjustHits     Adjust hits to include all matched relations or not? (adjusts the pattern)
 * @param withSpans      Automatically capture any spans overlapping with the hit or not? (adjusts the pattern)
 * @param filterQuery    Search only in documents matching this query, or null for all
 * @param searchSettings Some settings that influence query optimization and maximum # of hits processed
 * @param useCache       Use the results cache or ignore it for this query?
 * @param propFilter     Optional property/value hit filter
 * @param sampleParams   Optional sample parameters
 * @param sortBy         Optional property to sort by
 */
public record RequestHits(
        AnnotatedField searchField,
        TextPattern pattern,
        TextPattern patternOriginal,
        boolean adjustHits,
        boolean withSpans,
        Query filterQuery,
        SearchSettings searchSettings,
        HitPropFilter propFilter,
        String viewGroup,
        SampleParameters sampleParams,
        ContextSettings contextSettings,
        HitProperty sortBy,
        HitProperty groupBy,
        HitGroupScorer groupScorer,
        HitGroupProperty sortGroupsBy,
        boolean includeGroupContents,
        WindowSettings windowSettings,
        String facetDesc,
        boolean calculateCollocations, // (OLD collocations)
        Boolean sensitive, // (OLD collocations)
        boolean useCache,
        boolean waitForTotal,
        boolean includeSubcorpusSize,
        boolean explain,
        HitsResponseSettings hitsResponseSettings,
        List<MetadataField> metadataToInclude,
        boolean isCsv,
        CsvSettings csvSettings,
        ParamsForResponse paramsForResponse) {

    public static RequestHits fromParams(QueryParams params, boolean isCsv, TextPattern pattern) {
        return optFromParams(params, isCsv, pattern).orElseThrow(() -> new IllegalArgumentException("No pattern specified"));
    }

    public static Optional<RequestHits> optFromParams(QueryParams qpar, boolean isCsv, TextPattern overridePattern) {
        BlackLabIndex index = ParamUtil.index(qpar.getCorpusName());
        ContextSize contextSize = ParamUtil.getContext(qpar);
        String optContextTag = contextSize.inlineTagName();
        TextPattern pattern;
        pattern = overridePattern == null ?
                ParamUtil.pattern(index, qpar.get(WsParam.PATTERN_LANGUAGE), qpar.get(WsParam.PATTERN),
                        qpar.get(WsParam.PATTERN_GAP_DATA),
                        optContextTag).orElse(null) :
                overridePattern;
        if (pattern == null)
            return Optional.empty(); // pattern is required
        String groupBy = qpar.opt(WsParam.GROUP_BY).orElse(null);
        String viewGroup = qpar.opt(WsParam.VIEW_GROUP).orElse(null);
        String sortBy = qpar.opt(WsParam.SORT_BY).orElse(null);
        AnnotatedField annotatedField = ParamUtil.getAnnotatedField(index, qpar.get(WsParam.FIELD));
        AnnotatedField searchField = ParamUtil.getSearchField(index, qpar.get(WsParam.FIELD),
                qpar.opt(WsParam.SEARCH_FIELD).orElse(null));
        WebserviceOperation operation = ParamUtil.getOperation(qpar);
        HitProperty hitsGroupProperty = ParamUtil.getHitsGroupProperty(operation, groupBy,
                annotatedField, contextSize);
        HitGroupScorer hitGroupScorer = ParamUtil.getHitGroupScorer(annotatedField,
                qpar.opt(WsParam.SCORER).orElse(null));
        TextPattern patternOriginal = ParamUtil.patternNoWithinContextTag(index,
                qpar.get(WsParam.PATTERN_LANGUAGE),
                qpar.get(WsParam.PATTERN), qpar.get(WsParam.PATTERN_GAP_DATA)).orElse(null);
        HitProperty hitsSortProperty = ParamUtil.hitsSortProperty(operation, annotatedField, groupBy,
                viewGroup, sortBy, contextSize);
        HitGroupProperty sortGroupsBy = ParamUtil.hitGroupSortProperty(operation, groupBy, sortBy,
                viewGroup, HitGroupPropertySize.get());
        boolean includeGroupContents = ParamUtil.getIncludeGroupContents(
                qpar.optBool(WsParam.INCLUDE_GROUP_CONTENTS).orElse(null), qpar.config());
        SampleParameters sampleParams = ParamUtil.sampleParams(
                qpar.optDouble(WsParam.SAMPLE).orElse(null),
                qpar.optLong(WsParam.SAMPLE_NUMBER).orElse(null),
                qpar.optLong(WsParam.SAMPLE_SEED).orElse(null));
        SearchSettings searchSettings = ParamUtil.searchSettings(qpar.getLong(WsParam.MAX_HITS_TO_RETRIEVE),
                qpar.getLong(WsParam.MAX_HITS_TO_COUNT),
                qpar.debugMode() ? qpar.getInt(WsParam.FORWARD_INDEX_MATCHING_SETTING) : -1, qpar.config());
        ConcordanceType concordanceType = ParamUtil.getConcordanceType(qpar.get(WsParam.USE_CONTENT));
        return Optional.of(new RequestHits(
                searchField,
                pattern,
                patternOriginal,
                qpar.getBool(WsParam.REL_ADJUST_HITS),
                qpar.getBool(WsParam.WITH_SPANS),
                ParamUtil.filterQuery(qpar),
                searchSettings,
                HitPropFilter.fromParams(qpar),
                viewGroup,
                sampleParams,
                ParamUtil.contextSettings(contextSize, concordanceType, qpar.config()),
                hitsSortProperty,
                hitsGroupProperty,
                hitGroupScorer,
                sortGroupsBy,
                includeGroupContents,
                ParamUtil.windowSettings(qpar, isCsv),
                qpar.opt(WsParam.FACETS).orElse(null),
                qpar.get(WsParam.CALCULATE_STATS).equals("colloc"),
                qpar.optBool(WsParam.SENSITIVE).orElse(null),
                ParamUtil.useCache(qpar.getBool(WsParam.USE_CACHE), qpar.debugMode()),
                qpar.getBool(WsParam.WAIT_FOR_TOTAL_COUNT),
                ParamUtil.includeSubcorpusSize(qpar),
                qpar.getBool(WsParam.EXPLAIN_QUERY_REWRITE),
                HitsResponseSettings.fromParams(qpar),
                ParamUtil.getMetadataToInclude(index, qpar.getList(WsParam.LIST_VALUES_FOR_METADATA_FIELDS)),
                isCsv,
                CsvSettings.fromParams(qpar),
                qpar)
        );
    }

    public BlackLabIndex index() {
        return searchField.index();
    }

    public SearchFacets facets() {
        List<DocProperty> facets = DocProperty.propsFromDesc(index(), facetDesc);
        return facets == null ? null :
                RequestDocs.docsSearch(index(), filterQuery(), this).facet(facets);
    }

    public RequestHits withPattern(TextPattern pattern) {
        return new RequestHits(
                searchField, pattern, pattern,
                adjustHits, withSpans, filterQuery, searchSettings, propFilter, viewGroup, sampleParams,
                contextSettings, sortBy, groupBy, groupScorer, sortGroupsBy,
                includeGroupContents, windowSettings, facetDesc, calculateCollocations, sensitive, useCache,
                waitForTotal, includeSubcorpusSize, explain, hitsResponseSettings, metadataToInclude,
                isCsv, csvSettings, paramsForResponse);
    }

    /**
     * @return hits - filtered then sorted then sampled
     */
    public static SearchHits createSearch(RequestHits requestHits) throws BlsException {

        // Find hits
        TextPattern pattern = requestHits.pattern();
        if (pattern == null)
            throw new BadRequest("NO_PATTERN_GIVEN",
                    "Text search pattern required. Please specify 'patt' parameter.");
        if (requestHits.adjustHits() || requestHits.withSpans())
            pattern = pattern.adjustTextPattern(requestHits.adjustHits(), requestHits.withSpans());
        SearchHits hits;
        try {
            CompleteQuery cp = new CompleteQuery(pattern, requestHits.filterQuery());
            hits = requestHits.index().search(requestHits.searchField(), requestHits.useCache())
                    .find(cp, requestHits.searchSettings());
        } catch (InvalidQuery e) {
            throw BadRequest.pattSyntaxError(e);
        }

        // Optionally filter by property and value
        HitPropFilter filter = requestHits.propFilter();
        if (filter != null) {
            hits = hits.filter(filter.prop(), filter.value());
        }

        // Optionally sort
        if (requestHits.sortBy() != null)
            hits = hits.sort(requestHits.sortBy());

        // Optionally sample
        if (requestHits.sampleParams() != null)
            hits = hits.sample(requestHits.sampleParams());

        return hits;
    }
}
