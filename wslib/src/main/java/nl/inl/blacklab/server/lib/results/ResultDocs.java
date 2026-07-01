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
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
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
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.searches.SearchCacheEntry;
import nl.inl.blacklab.searches.SearchCount;
import nl.inl.blacklab.searches.SearchDocGroups;
import nl.inl.blacklab.searches.SearchDocs;
import nl.inl.blacklab.searches.SearchFacets;
import nl.inl.blacklab.searches.SearchHits;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.jobs.WindowSettings;
import nl.inl.blacklab.server.lib.ParamsForResponse;
import nl.inl.blacklab.server.lib.SearchTimings;
import nl.inl.blacklab.server.lib.requests.RequestDocs;
import nl.inl.blacklab.server.lib.requests.RequestHits;
import nl.inl.util.SearchTimer;

public class ResultDocs {

    private final RequestDocs requestDocs;

    private final ParamsForResponse paramsForResponse;

    private final DocGroups groups;

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

    ResultDocs(RequestDocs requestDocs,
            Collection<MetadataField> metadataToInclude,
            BlackLabIndex blIndex,
            long totalTokens,
            DocResults subcorpusResults,
            ResultSummaryCommonFields summaryFields,
            DocResults window,
            Collection<Annotation> annotationsToList,
            DocGroups groups,
            List<CorpusSize> corpusSizes) throws InvalidQuery {
        this.requestDocs = requestDocs;
        this.paramsForResponse = requestDocs.params();
        this.annotationsToList = annotationsToList;
        this.totalTokens = totalTokens;
        this.subcorpusResults = subcorpusResults;
        this.summaryFields = summaryFields;

        docFields = WebserviceOperations.getDocFields(blIndex);
        metaDisplayNames = WebserviceOperations.getMetaDisplayNames(blIndex);

        facetInfo = null;
        SearchFacets facets = requestDocs.facets();
        if (facets != null) {
            Map<DocProperty, DocGroups> counts = facets.execute().countsPerFacet();
            facetInfo = WebserviceOperations.getFacetInfo(counts);
        }
        if (window != null) {
            listResultDocs = new ArrayList<>();
            for (DocResult dr: window) {
                listResultDocs.add(new ResultDocResult(metadataToInclude, requestDocs, getAnnotationsToList(), dr));
            }
        }
        this.docResults = window;
        this.groups = groups;
        this.corpusSizes = corpusSizes;
    }

    static ResultDocs docsResponse(RequestDocs requestDocs) throws InvalidQuery {
        // Retrieve parameters
        RequestHits optRequestHits = requestDocs.optHits();

        // Depending on parameters, determine some searches we need
        SearchHits searchHits = optRequestHits == null ? null : RequestHits.createSearch(optRequestHits);
        boolean mustGroup = requestDocs.groupBy() != null;
        boolean isViewGroup = mustGroup && requestDocs.viewGroup() != null;
        boolean viewingGroups = mustGroup && !isViewGroup;
        SearchDocs searchDocs = isViewGroup ? null : requestDocs.docsSorted();
        SearchCount searchCount = isViewGroup ? null : requestDocs.docsCount();
        SearchDocGroups searchDocGroups = mustGroup ? requestDocs.docsGrouped() : null;

        SearchCacheEntry<ResultsStats> originalHitsSearch =
                searchHits == null ? null : searchHits.hitCount().executeAsync();

        DocGroups groups = null;
        DocResults docs, window;
        SearchCacheEntry<?> search;
        SearchCacheEntry<DocGroups> groupsSearch;

        DocGroup group = null;
        if (mustGroup) {
            search = groupsSearch = searchDocGroups.executeAsync();
            try {
                groups = groupsSearch.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // preserve interrupted status
                throw WebserviceOperations.translateSearchException(e);
            } catch (ExecutionException e) {
                throw WebserviceOperations.translateSearchException(e);
            }
            if (isViewGroup) {
                PropertyValue viewGroupVal = PropertyValue.deserialize(groups.field(), requestDocs.viewGroup());
                if (viewGroupVal == null)
                    throw new BadRequest("ERROR_IN_GROUP_VALUE",
                            "Parameter 'viewgroup' has an illegal value: " + requestDocs.viewGroup());
                group = groups.get(viewGroupVal);
                if (group == null)
                    throw new BadRequest("GROUP_NOT_FOUND", "Group not found: " + viewGroupVal);

                docs = group.storedResults();

                // NOTE: sortBy is automatically applied to regular results, but not to results within groups
                // See ResultsGrouper::init (uses hits.getByOriginalOrder(i)) and DocResults::constructor
                // Also see SearchParams (hitsSortSettings, docSortSettings, hitGroupsSortSettings, docGroupsSortSettings)
                // There is probably no reason why we can't just sort/use the sort of the input results, but we need some
                // more testing to see if everything is correct if we change this
                if (requestDocs.sortBy() != null) {
                    docs = docs.sort(requestDocs.sortBy());
                }
            } else {
                docs = searchDocs.execute();
            }
        } else {
            // Non-grouped doc results
            search = searchDocs.executeAsync();
            try {
                docs = (DocResults) search.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // preserve interrupted status
                throw WebserviceOperations.translateSearchException(e);
            } catch (ExecutionException e) {
                throw WebserviceOperations.translateSearchException(e);
            }

            // If "waitfortotal=true" was passed, block until all results have been fetched
            if (requestDocs.waitForTotal())
                docs.size();
        }

        // apply window settings

        WindowSettings windowSettings = requestDocs.windowSettings();
        window = viewingGroups ? null : docs.window(windowSettings.first(), windowSettings.size());
        if (viewingGroups)
            groups = groups.window(windowSettings.first(), windowSettings.size());
        WindowStats windowStats = viewingGroups ? groups.windowStats() : window.windowStats();

        long totalTime = search.timer().time();
        //STRANGE!? boolean waitForTotal = true;

        DocResults subcorpusResults = BlackLabIndex.getSubcorpusSearch(requestDocs.index(), requestDocs.filterQuery())
                .execute();
        CorpusSize subcorpusSize = null;
        DocResults subcorpus = null;
        DocProperty metadataGroupProperties = null;
        if (!isViewGroup) { // viewgroup response doesn't include subcorpus size (or should it...?)
            if (groups != null) {
                metadataGroupProperties = groups.groupCriteria();
                subcorpus = subcorpusResults;
                subcorpusSize = subcorpus.subcorpusSize();
            } else {
                if (requestDocs.includeSubcorpusSize()) {
                    subcorpusSize = subcorpusResults.subcorpusSize();
                }
            }
        } else {
            // Viewing a single group. Determine the subcorpus size.
            subcorpusSize = WebserviceOperations.findSubcorpusSize(requestDocs.index(), subcorpusResults.query(),
                    groups.groupCriteria(), group.identity());
        }

        ResultsStats hitsStats, docsStats;
        hitsStats = isViewGroup ?
                new ResultsStatsSaved(window.getNumberOfHits(), window.getNumberOfHits()) :
                (originalHitsSearch == null ? null : originalHitsSearch.peek());
        docsStats = isViewGroup ?
                window.resultsStats() :
                searchCount.executeAsync().peek();

        SearchTimer timer = search.timer();
        SearchTimings timings = new SearchTimings(timer.time(), totalTime);
        AnnotatedField searchField = docs == null ? groups.field() : docs.field();
        TextPattern originalPattern = optRequestHits == null ? null : optRequestHits.patternOriginal();
        ResultSummaryNumDocs numResultDocs = null;
        ResultSummaryNumHits numResultHits = null;
        if (hitsStats == null) {
            numResultDocs = new ResultSummaryNumDocs(isViewGroup, docs, timings, subcorpusSize);
        } else {
            numResultHits = new ResultSummaryNumHits(hitsStats, docsStats, requestDocs.waitForTotal(), timings, subcorpusSize);
        }
        ResultSummaryCommonFields summaryFields = new ResultSummaryCommonFields(originalPattern, timings,
                null, groups, windowStats, searchField, Collections.emptyList(), null,
                requestDocs.params(), numResultDocs, numResultHits
        );

        // Find subcorpus sizes per group
        List<CorpusSize> corpusSizes = new ArrayList<>();
        if (metadataGroupProperties != null && searchHits != null) {
            for (long i = windowStats.first(); i <= windowStats.last(); ++i) {
                DocGroup currentGroup = groups.get(i);
                // Find size of corresponding subcorpus group
                CorpusSize size = WebserviceOperations.findSubcorpusSize(optRequestHits.index(), subcorpus.query(),
                        metadataGroupProperties, currentGroup.identity());
                corpusSizes.add(size);
            }
        }

        long totalTokens = subcorpusSize == null ? -1 : subcorpusSize.getTotalCount().getTokens();

        return new ResultDocs(
                requestDocs,
                requestDocs.metadataToInclude(),
                requestDocs.index(),
                totalTokens,
                subcorpusResults,
                summaryFields,
                window,
                optRequestHits == null ? null :
                        optRequestHits.hitsResponseSettings().annotationsToInclude(),
                groups,
                corpusSizes);
    }

    public Collection<Annotation> getAnnotationsToList() {
        return annotationsToList;
    }

    public long getTotalTokens() {
        return totalTokens;
    }

    public ParamsForResponse paramsForResponse() {
        return paramsForResponse;
    }

    public ResultSummaryCommonFields getSummaryFields() {
        return summaryFields;
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

    public RequestDocs getRequestDocs() {
        return requestDocs;
    }
}
