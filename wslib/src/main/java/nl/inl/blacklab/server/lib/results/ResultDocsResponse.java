package nl.inl.blacklab.server.lib.results;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.apache.commons.lang3.tuple.Pair;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.results.CorpusSize;
import nl.inl.blacklab.search.results.DocGroup;
import nl.inl.blacklab.search.results.DocGroups;
import nl.inl.blacklab.search.results.DocResult;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.ResultsStats;
import nl.inl.blacklab.searches.SearchCacheEntry;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.index.Index;
import nl.inl.blacklab.server.lib.WebserviceParams;
import nl.inl.blacklab.server.lib.SearchTimings;

public class ResultDocsResponse {
    private final Collection<Annotation> annotationsToList;
    private final Collection<MetadataField> metadataFieldsToList;
    private final BlackLabIndex index;
    private final long totalTokens;
    private final ResultSummaryCommonFields summaryFields;
    private final ResultSummaryNumDocs numResultDocs;
    private final ResultSummaryNumHits numResultHits;
    private final DocResults window;
    private final WebserviceParams params;
    private final Map<String, String> docFields;
    private final Map<String, String> metaDisplayNames;
    private Map<String, List<Pair<String, Long>>> facetInfo;
    List<ResultDocResult> docResults;

    ResultDocsResponse(Collection<Annotation> annotationsToList, Collection<MetadataField> metadataFieldsToList,
            BlackLabIndex blIndex, long totalTokens,
            ResultSummaryCommonFields summaryFields, ResultSummaryNumDocs numResultDocs,
            ResultSummaryNumHits numResultHits, DocResults window,
            WebserviceParams params) throws InvalidQuery {
        this.annotationsToList = annotationsToList;
        this.metadataFieldsToList = metadataFieldsToList;
        this.index = blIndex;
        this.totalTokens = totalTokens;
        this.summaryFields = summaryFields;
        this.numResultDocs = numResultDocs;
        this.numResultHits = numResultHits;
        this.window = window;
        this.params = params;

        docFields = WebserviceOperations.getDocFields(index);
        metaDisplayNames = WebserviceOperations.getMetaDisplayNames(index);

        facetInfo = null;
        if (params.hasFacets()) {
            Map<DocProperty, DocGroups> counts = params.facets().execute().countsPerFacet();
            facetInfo = WebserviceOperations.getFacetInfo(counts);
        }
        docResults = new ArrayList<>();
        for (DocResult dr: window) {
            docResults.add(new ResultDocResult(metadataFieldsToList, params, getAnnotationsToList(), dr));
        }
    }

    static ResultDocsResponse viewGroupDocsResponse(WebserviceParams params) throws InvalidQuery {
        String viewGroup = params.getViewGroup().get();

        // TODO: clean up, do using JobHitsGroupedViewGroup or something (also cache sorted group!)

        SearchCacheEntry<DocGroups> docGroupFuture;
        // Yes. Group, then show hits from the specified group
        SearchCacheEntry<?> search = docGroupFuture = params.docsGrouped().executeAsync();
        DocGroups groups;
        try {
            groups = docGroupFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            throw WebserviceOperations.translateSearchException(e);
        }

        PropertyValue viewGroupVal = PropertyValue.deserialize(groups.index(), groups.field(), viewGroup);
        if (viewGroupVal == null) {
            throw new BadRequest("ERROR_IN_GROUP_VALUE", "Parameter 'viewgroup' has an illegal value: " + viewGroup);
        }

        DocGroup group = groups.get(viewGroupVal);
        if (group == null)
            throw new BadRequest("GROUP_NOT_FOUND", "Group not found: " + viewGroup);

        // NOTE: sortBy is automatically applied to regular results, but not to results within groups
        // See ResultsGrouper::init (uses hits.getByOriginalOrder(i)) and DocResults::constructor
        // Also see SearchParams (hitsSortSettings, docSortSettings, hitGroupsSortSettings, docGroupsSortSettings)
        // There is probably no reason why we can't just sort/use the sort of the input results, but we need
        // some more testing to see if everything is correct if we change this
        String sortBy = params.getSortProps().orElse("");
        DocResults docsSorted = group.storedResults();
        DocProperty sortProp = DocProperty.deserialize(params.blIndex(), sortBy);
        if (sortProp != null)
            docsSorted = docsSorted.sort(sortProp);

        long first = params.getFirstResultToShow();
        if (first < 0)
            first = 0;
        long number = params.getNumberOfResultsToShow();
        if (number < 0 || number > params.getSearchManager().config().getParameters().getPageSize().getMax())
            number = params.getSearchManager().config().getParameters().getPageSize().getDefault();
        DocResults totalDocResults = docsSorted;
        DocResults window = docsSorted.window(first, number);

        DocResults docResults = group.storedResults();
        long totalTime = docGroupFuture.timer().time();

        return docsResponse(params, totalDocResults, docResults, window, search, null,
                true, true, totalTime);
    }

    static ResultDocsResponse regularDocsResponse(WebserviceParams params) throws InvalidQuery {
        // Make sure we have the hits search, so we can later determine totals.
        SearchCacheEntry<ResultsStats> originalHitsSearch = null;
        if (params.hasPattern()) {
            originalHitsSearch = params.hitsSample().hitCount().executeAsync();
        }

        SearchCacheEntry<DocResults> searchWindow = params.docsWindow().executeAsync();
        SearchCacheEntry<?> search = searchWindow;

        // Also determine the total number of hits
        SearchCacheEntry<DocResults> total = params.docs().executeAsync();

        DocResults window, totalDocResults;
        try {
            window = searchWindow.get();
            totalDocResults = total.get();
        } catch (InterruptedException | ExecutionException e) {
            throw WebserviceOperations.translateSearchException(e);
        }

        // If "waitfortotal=true" was passed, block until all results have been fetched
        boolean waitForTotal = params.getWaitForTotal();
        if (waitForTotal)
            totalDocResults.size();

        DocResults docResults = totalDocResults;
        long totalTime = total.threwException() ? 0 : total.timer().time();

        return docsResponse(params, totalDocResults, docResults, window, search, originalHitsSearch,
                false, waitForTotal, totalTime);
    }

    private static ResultDocsResponse docsResponse(
            WebserviceParams params,
            DocResults totalDocResults,
            DocResults docResults,
            DocResults window,
            SearchCacheEntry<?> search,
            SearchCacheEntry<ResultsStats> originalHitsSearch,
            boolean isViewGroup,
            boolean waitForTotal,
            long totalTime) throws InvalidQuery {

        Collection<Annotation> annotationsToList = WebserviceOperations.getAnnotationsToWrite(params);
        Collection<MetadataField> metadataFieldsToList = WebserviceOperations.getMetadataToWrite(params);

                BlackLabIndex index = params.blIndex();
        CorpusSize subcorpusSize = null;
        if (params.getIncludeTokenCount()) {
            // Determine total number of tokens in result set
            subcorpusSize = totalDocResults.subcorpusSize();
        }

        ResultsStats hitsStats, docsStats;
        hitsStats = originalHitsSearch == null ? null : originalHitsSearch.peek();
        docsStats = params.docsCount().executeAsync().peek();
        SearchTimings timings = new SearchTimings(search.timer().time(), totalTime);
        Index.IndexStatus indexStatus = params.getIndexManager().getIndex(params.getCorpusName()).getStatus();
        ResultSummaryCommonFields summaryFields = WebserviceOperations.summaryCommonFields(params, indexStatus, timings,
                null, null, window.windowStats(), docResults.field().name(),
                Collections.emptyList());
        ResultSummaryNumDocs numResultDocs = null;
        ResultSummaryNumHits numResultHits = null;
        if (hitsStats == null) {
            numResultDocs = WebserviceOperations.numResultsSummaryDocs(isViewGroup,
                    docResults, timings, subcorpusSize);
        } else {
            numResultHits = WebserviceOperations.numResultsSummaryHits(
                    hitsStats, docsStats, waitForTotal, timings, subcorpusSize);
        }

        // Search is done; construct the results object
        long totalTokens = subcorpusSize == null ? -1 : subcorpusSize.getTotalCount().getTokens();
        return new ResultDocsResponse(annotationsToList, metadataFieldsToList, index,
                totalTokens, summaryFields, numResultDocs, numResultHits, window, params);
    }

    public Collection<Annotation> getAnnotationsToList() {
        return annotationsToList;
    }

    public Collection<MetadataField> getMetadataFieldsToList() {
        return metadataFieldsToList;
    }

    public BlackLabIndex getIndex() {
        return index;
    }

    public long getTotalTokens() {
        return totalTokens;
    }

    public ResultSummaryCommonFields getSummaryFields() {
        return summaryFields;
    }

    public ResultSummaryNumDocs getNumResultDocs() {
        return numResultDocs;
    }

    public ResultSummaryNumHits getNumResultHits() {
        return numResultHits;
    }

    public DocResults getWindow() {
        return window;
    }

    public WebserviceParams getParams() {
        return params;
    }

    public Map<String, String> getDocFields() {
        return docFields;
    }

    public Map<String, String> getMetaDisplayNames() {
        return metaDisplayNames;
    }

    public Map<String, List<Pair<String, Long>>> getFacetInfo() {
        return facetInfo;
    }

    public List<ResultDocResult> getDocResults() {
        return docResults;
    }
}
