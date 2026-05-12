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
import nl.inl.blacklab.search.results.WindowStats;
import nl.inl.blacklab.search.results.docs.DocGroup;
import nl.inl.blacklab.search.results.docs.DocGroups;
import nl.inl.blacklab.search.results.docs.DocResult;
import nl.inl.blacklab.search.results.docs.DocResults;
import nl.inl.blacklab.search.results.stats.ResultsStats;
import nl.inl.blacklab.search.results.stats.ResultsStatsSaved;
import nl.inl.blacklab.searches.SearchCacheEntry;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.lib.SearchTimings;
import nl.inl.blacklab.server.lib.WebserviceParams;
import nl.inl.blacklab.server.lib.WebserviceParamsImpl;
import nl.inl.blacklab.server.lib.requests.RequestHits;
import nl.inl.util.SearchTimer;

public class ResultDocs {

    private final WebserviceParams params;

    private final DocGroups groups;

    private final ResultSummaryNumDocs numResultDocs;

    private final ResultSummaryNumHits numResultHits;

    private final ResultSummaryCommonFields summaryFields;

    private final Collection<Annotation> annotationsToList;

    private final List<CorpusSize> corpusSizes;

    private final long totalTokens;

    private final DocResults subcorpusResults;

    private final Map<String, String> docFields;

    private final Map<String, String> metaDisplayNames;

    private Map<String, List<Pair<String, Long>>> facetInfo;

    List<ResultDocResult> listResultDocs;

    DocResults docResults;

    ResultDocs(WebserviceParams params,
            Collection<MetadataField> metadataFieldsToList,
            BlackLabIndex blIndex,
            long totalTokens,
            DocResults subcorpusResults,
            ResultSummaryCommonFields summaryFields,
            ResultSummaryNumDocs numResultDocs,
            ResultSummaryNumHits numResultHits,
            DocResults window,
            Collection<Annotation> annotationsToList,
            DocGroups groups,
            List<CorpusSize> corpusSizes) throws InvalidQuery {
        this.params = params;
        this.annotationsToList = annotationsToList;
        this.totalTokens = totalTokens;
        this.subcorpusResults = subcorpusResults;
        this.summaryFields = summaryFields;
        this.numResultDocs = numResultDocs;
        this.numResultHits = numResultHits;

        docFields = WebserviceOperations.getDocFields(blIndex);
        metaDisplayNames = WebserviceOperations.getMetaDisplayNames(blIndex);

        facetInfo = null;
        if (params.hasFacets()) {
            Map<DocProperty, DocGroups> counts = params.facets().execute().countsPerFacet();
            facetInfo = WebserviceOperations.getFacetInfo(counts);
        }
        if (window != null) {
            listResultDocs = new ArrayList<>();
            for (DocResult dr: window) {
                listResultDocs.add(new ResultDocResult(metadataFieldsToList, params, getAnnotationsToList(), dr));
            }
        }
        this.docResults = window;

        this.groups = groups;
        this.corpusSizes = corpusSizes;
    }

    static ResultDocs docsResponse(WebserviceParams params, long maxWindowSize, long defaultWindowSize) throws InvalidQuery {
        SearchCacheEntry<ResultsStats> originalHitsSearch = null;
        if (params.hasPattern()) {
            originalHitsSearch = WebserviceParamsImpl.determineHitsSearch(RequestHits.fromParams(params)).hitCount().executeAsync();
        }

        SearchCacheEntry<?> search;
        SearchCacheEntry<DocGroups> groupsSearch;

        String groupBy = params.getGroupProps().orElse(null);

        DocResults subcorpusResults = params.subcorpus().execute();

        DocGroups groups = null;
        DocResults docs, window;
        boolean isViewGroup = false;
        if (groupBy != null) {
            search = groupsSearch = params.docsGrouped().executeAsync();
            try {
                groups = groupsSearch.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // preserve interrupted status
                throw WebserviceOperations.translateSearchException(e);
            } catch (ExecutionException e) {
                throw WebserviceOperations.translateSearchException(e);
            }

            String viewGroup = params.getViewGroup().orElse(null);
            if (viewGroup != null) {
                isViewGroup = true;
                PropertyValue viewGroupVal = PropertyValue.deserialize(groups.field(), viewGroup);
                if (viewGroupVal == null)
                    throw new BadRequest("ERROR_IN_GROUP_VALUE",
                            "Parameter 'viewgroup' has an illegal value: " + viewGroup);
                DocGroup group = groups.get(viewGroupVal);
                if (group == null)
                    throw new BadRequest("GROUP_NOT_FOUND", "Group not found: " + viewGroup);

                docs = group.storedResults();

                // NOTE: sortBy is automatically applied to regular results, but not to results within groups
                // See ResultsGrouper::init (uses hits.getByOriginalOrder(i)) and DocResults::constructor
                // Also see SearchParams (hitsSortSettings, docSortSettings, hitGroupsSortSettings, docGroupsSortSettings)
                // There is probably no reason why we can't just sort/use the sort of the input results, but we need some
                // more testing to see if everything is correct if we change this
                String sortBy = params.getSortProps().orElse(null);
                if (sortBy != null) {
                    DocProperty sortProp = DocProperty.deserialize(params.blIndex(), sortBy);
                    if (sortProp != null)
                        docs = docs.sort(sortProp);
                }
            } else {
                docs = params.docsSorted().execute();
            }
        } else {
            // Non-grouped doc results
            search = params.docsSorted().executeAsync();
            try {
                docs = (DocResults)search.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // preserve interrupted status
                throw WebserviceOperations.translateSearchException(e);
            } catch (ExecutionException e) {
                throw WebserviceOperations.translateSearchException(e);
            }

            // If "waitfortotal=true" was passed, block until all results have been fetched
            boolean waitForTotal = params.getWaitForTotal();
            if (waitForTotal)
                docs.size();
        }

        boolean viewingGroups = groupBy != null && !isViewGroup;

        // apply window settings
        long first = Math.max(0, params.getFirstResultToShow());
        long number = params.optNumberOfResultsToShow().orElse(defaultWindowSize);
        if (number < 0)
            number = defaultWindowSize;
        if (number > maxWindowSize)
            number = maxWindowSize;
        window = viewingGroups ? null : docs.window(first, number);
        if (viewingGroups)
            groups = groups.window(first, number);
        WindowStats windowStats = viewingGroups ? groups.windowStats() : window.windowStats();

        long totalTime = search.timer().time();
        boolean waitForTotal = true;

        Collection<Annotation> annotationsToList = WebserviceOperations.getAnnotationsToWrite(params);
        Collection<MetadataField> metadataFieldsToList = WebserviceOperations.getMetadataToWrite(params);
        BlackLabIndex index = params.blIndex();

        CorpusSize subcorpusSize = null;
        DocResults subcorpus = null;
        DocProperty metadataGroupProperties = null;
        if (!isViewGroup) { // viewgroup response doesn't include subcorpus size (or should it...?)
            if (groups != null) {
                metadataGroupProperties = groups.groupCriteria();
                subcorpus = subcorpusResults;
                subcorpusSize = subcorpus.subcorpusSize();
            } else if (params.getIncludeSubcorpusSize()) {
                subcorpusSize = subcorpusResults.subcorpusSize();
            }
        }

        ResultsStats hitsStats, docsStats;
        hitsStats = isViewGroup ?
                new ResultsStatsSaved(window.getNumberOfHits(), window.getNumberOfHits()) :
                originalHitsSearch == null ? null : originalHitsSearch.peek();
        docsStats = isViewGroup ?
                window.resultsStats() :
                params.docsCount().executeAsync().peek();

        SearchTimer timer = search.timer();
        SearchTimings timings = new SearchTimings(timer.time(), totalTime);
        ResultSummaryCommonFields summaryFields = WebserviceOperations.summaryCommonFields(
                params,
                timings, null,
                groups, windowStats, docs == null ? groups.field() : docs.field(),
                Collections.emptyList());
        ResultSummaryNumDocs numResultDocs = null;
        ResultSummaryNumHits numResultHits = null;
        if (hitsStats == null) {
            numResultDocs = WebserviceOperations.numResultsSummaryDocs(isViewGroup,
                    docs, timings, subcorpusSize);
        } else {
            numResultHits = WebserviceOperations.numResultsSummaryHits(
                    hitsStats, docsStats, waitForTotal, timings, subcorpusSize);
        }

        // Find subcorpus sizes per group
        List<CorpusSize> corpusSizes = new ArrayList<>();
        if (metadataGroupProperties != null && params.hasPattern()) {
            for (long i = windowStats.first(); i <= windowStats.last(); ++i) {
                DocGroup group = groups.get(i);
                // Find size of corresponding subcorpus group
                CorpusSize size = WebserviceOperations.findSubcorpusSize(params.blIndex(), subcorpus.query(),
                        metadataGroupProperties, group.identity());
                corpusSizes.add(size);
            }
        }

        long totalTokens = subcorpusSize == null ? -1 : subcorpusSize.getTotalCount().getTokens();

        return new ResultDocs(params, metadataFieldsToList, index,
                totalTokens, subcorpusResults, summaryFields, numResultDocs,
                numResultHits, window, annotationsToList,
                groups, corpusSizes);
    }

    public Collection<Annotation> getAnnotationsToList() {
        return annotationsToList;
    }

    public long getTotalTokens() {
        return totalTokens;
    }

    public WebserviceParams getParams() {
        return params;
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
        return listResultDocs;
    }

    public List<CorpusSize> getCorpusSizes() {
        return corpusSizes;
    }

    public DocGroups getGroups() {
        return groups;
    }

    public WindowStats getOurWindow() {
        return summaryFields.getWindow();
    }

    public DocResults getSubcorpusResults() {
        return subcorpusResults;
    }

    public DocResults getDocs() {
        return docResults;
    }
}
