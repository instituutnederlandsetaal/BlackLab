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
import nl.inl.blacklab.server.lib.ParamsForResponse;
import nl.inl.blacklab.server.lib.QueryParams;
import nl.inl.blacklab.server.lib.WebserviceParams;

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

    public static RequestHits fromParams(QueryParams params, boolean isCsv) {
        return optFromParams(params, isCsv).orElseThrow(() -> new IllegalArgumentException("No pattern specified"));
    }

    public static Optional<RequestHits> optFromParams(QueryParams qpar, boolean isCsv) {
        BlackLabIndex index = WebserviceParams.index(qpar.getCorpusName());
        ContextSize contextSize = WebserviceParams.getContext(qpar.getContextParam(), qpar.config());
        String optContextTag = contextSize.inlineTagName();
        Optional<TextPattern> pattern = WebserviceParams.pattern(index, qpar.getPattLanguage(),
                qpar.getPattern(), qpar.getPattGapData(), optContextTag);
        if (pattern.isEmpty())
            return Optional.empty();
        String groupBy = qpar.getGroupBy().orElse(null);
        String viewGroup = qpar.getViewGroup().orElse(null);
        String sortBy = qpar.getSortBy().orElse(null);
        AnnotatedField annotatedField = WebserviceParams.getAnnotatedField(index, qpar.getFieldName());
        AnnotatedField searchField = WebserviceParams.getSearchField(index, qpar.getFieldName(),
                qpar.getSearchFieldName().orElse(null));
        HitProperty hitsGroupProperty = WebserviceParams.getHitsGroupProperty(qpar.getOperation(), groupBy,
                annotatedField, contextSize);
        HitGroupScorer hitGroupScorer = WebserviceParams.getHitGroupScorer(annotatedField,
                qpar.getScorer().orElse(null));
        TextPattern patternOriginal = WebserviceParams.patternNoWithinContextTag(index, qpar.getPattLanguage(),
                qpar.getPattern(), qpar.getPattGapData()).orElse(null);
        HitProperty hitsSortProperty = WebserviceParams.hitsSortProperty(qpar.getOperation(), annotatedField, groupBy,
                viewGroup, sortBy, contextSize);
        HitGroupProperty sortGroupsBy = WebserviceParams.hitGroupSortProperty(qpar.getOperation(), groupBy, sortBy,
                viewGroup, HitGroupPropertySize.get());
        boolean includeGroupContents = WebserviceParams.getIncludeGroupContents(
                qpar.optIncludeGroupContents().orElse(null), qpar.config());
        SampleParameters sampleParams = WebserviceParams.sampleParams(
                qpar.getSampleFraction().orElse(null),
                qpar.getSampleNumber().orElse(null),
                qpar.getSampleSeed().orElse(null));
        SearchSettings searchSettings = WebserviceParams.searchSettings(qpar.getMaxRetrieve(), qpar.getMaxCount(),
                qpar.debugMode() ? qpar.getForwardIndexMatchFactor() : -1, qpar.config());
        return Optional.of(new RequestHits(
                searchField,
                pattern.get(),
                patternOriginal,
                qpar.getAdjustRelationHits(),
                qpar.getWithSpans(),
                WebserviceParams.filterQuery(qpar),
                searchSettings,
                HitPropFilter.fromParams(qpar),
                viewGroup,
                sampleParams,
                WebserviceParams.contextSettings(contextSize, qpar.getConcordanceType(), qpar.config()),
                hitsSortProperty,
                hitsGroupProperty,
                hitGroupScorer,
                sortGroupsBy,
                includeGroupContents,
                WebserviceParams.windowSettings(qpar, isCsv),
                qpar.getFacetProps().orElse(null),
                qpar.isCalculateCollocations(),
                qpar.optSensitive().orElse(null),
                WebserviceParams.useCache(qpar.getUseCache(), qpar.debugMode()),
                qpar.getWaitForTotal(),
                qpar.getIncludeSubcorpusSize(),
                qpar.getExplain(),
                HitsResponseSettings.fromParams(qpar),
                WebserviceParams.getMetadataToInclude(index, qpar.getListMetadataValuesFor()),
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
