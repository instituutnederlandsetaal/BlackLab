package nl.inl.blacklab.server.lib.results;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.document.Document;
import org.jspecify.annotations.NonNull;

import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.ResultProperty;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;
import nl.inl.blacklab.search.results.CorpusSize;
import nl.inl.blacklab.search.results.HitOrDocGroup;
import nl.inl.blacklab.search.results.Results;
import nl.inl.blacklab.search.results.WindowStats;
import nl.inl.blacklab.search.results.docs.DocGroups;
import nl.inl.blacklab.search.results.hitresults.HitGroup;
import nl.inl.blacklab.search.results.hitresults.HitGroups;
import nl.inl.blacklab.search.results.hitresults.HitResults;
import nl.inl.blacklab.search.results.hits.Hits;
import nl.inl.blacklab.search.results.stats.ResultsStats;
import nl.inl.blacklab.searches.SearchCacheEntry;
import nl.inl.blacklab.searches.SearchCount;
import nl.inl.blacklab.searches.SearchFacets;
import nl.inl.blacklab.searches.SearchHitGroups;
import nl.inl.blacklab.searches.SearchHits;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.jobs.ContextSettings;
import nl.inl.blacklab.server.jobs.WindowSettings;
import nl.inl.blacklab.server.lib.ConcordanceContext;
import nl.inl.blacklab.server.lib.ParamsForResponse;
import nl.inl.blacklab.server.lib.SearchTimings;
import nl.inl.blacklab.server.lib.requests.RequestHits;

public class ResultHits {
    private static final Logger logger = LogManager.getLogger(ResultHits.class);
    public static final String LOG_MSG_SEARCHING_THREW_AN_EXCEPTION = "Searching threw an exception";

    private final RequestHits reqHits;

    private final HitResults hitResults;

    private final List<? extends ResultProperty> groupCriteria;

    private final HitOrDocGroup group;

    private final ResultsStats hitsStats;

    private final ResultsStats docsStats;

    private final SearchCacheEntry<?> cacheEntry;

    private final SearchCacheEntry<HitResults> cacheEntryWindow;

    private final long kwicTimeMs;

    /** Total number of tokens in documents matching pattern+filter */
    private final long tokensInMatchingDocuments;

    private final ConcordanceContext concordanceContext;

    private final Map<Integer, String> docIdToPid;

    private final Map<String, ResultDocInfo> docInfos;

    private final List<Annotation> annotationsToWrite;

    private final ResultSummaryCommonFields summaryCommonFields;

    private final ResultSummaryNumHits summaryNumHits;

    private final ResultListOfHits listOfHits;

    private final SearchFacets searchFacets;

    /** Results of faceting. Calculated only when needed. */
    private Map<String, List<Pair<String, Long>>> facetInfo;

    /** (API v4 included special metadata fields (pidField, etc.) in response) */
    private final Map<String, String> specialMetadataFields;

    /** (API v4 included metadata field display names in response) */
    private final Map<String, String> metaDisplayNames;

    /**
     * Get the hits for this request. Exceptions cleanly mapping to http error
     * responses are thrown if any part of the request cannot be fulfilled. Sorting
     * is already applied to the hits.
     */
    public static @NonNull ResultHits get(RequestHits reqHits) {
        try {
            // Create the search objects
            SearchHits searchHits = RequestHits.createSearch(reqHits);
            SearchCount searchHitCount = searchHits.hitCount();
            SearchCount searchDocCount = searchHits.docCount();
            // Start the search.
            // - First start the hit count, which will start the underlying hits search.
            // - Then get the underlying hits search from the cache (this may take a while as
            //   it will complete when the Hits object is available)
            SearchCacheEntry<ResultsStats> cacheEntry = searchHitCount.executeAsync();
            HitResults hitResults = searchHits.execute();
            try {
                ResultsStats hitsStats = cacheEntry.peek();
                ResultsStats docsStats = searchDocCount.executeAsync().peek();
                // Wait until all hits have been counted.
                if (reqHits.waitForTotal()) {
                    hitsStats.countedTotal();
                    docsStats.countedTotal();
                }
                return new ResultHits(reqHits, hitResults, cacheEntry, hitsStats, docsStats,
                        false, null, null);
            } catch (InterruptedSearch e) {
                // Our count was probably aborted.
                logger.debug("Error getting count(s)", e);
                throw e;
            }
        } catch (InvalidQuery e) {
            logger.debug(LOG_MSG_SEARCHING_THREW_AN_EXCEPTION, e);
            throw WebserviceOperations.translateSearchException(e);
        }
    }

    /**
     * Get the hits (and the groups from which they were extracted)
     * for this request. Exceptions cleanly mapping to http error
     * responses are thrown if any part of the request cannot be fulfilled. Sorting
     * is already applied to the hits.
     */
    public static @NonNull ResultHits getViewGroup(RequestHits reqHits) {
        try {
            // Find hits
            SearchHits searchHits = RequestHits.createSearch(reqHits);

            // Group them
            SearchHitGroups hitsGrouped = searchHits.groupStats(reqHits.groupBy(), Results.NO_LIMIT,
                    reqHits.groupScorer());

            // Find single group
            HitsFromGroup hitsFromGroup = getHitsFromGroup(
                    reqHits.searchField(),
                    hitsGrouped,
                    reqHits.viewGroup(),
                    reqHits.sortBy());

            HitResults hitResults = hitsFromGroup.hitResults();
            return new ResultHits(reqHits, hitResults, hitsFromGroup.cacheEntry(), hitResults.resultsStats(),
                    hitResults.docsStats(), true, hitsFromGroup.group(), hitsFromGroup.criteria()
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // preserve interrupted status
            logger.debug(LOG_MSG_SEARCHING_THREW_AN_EXCEPTION, e);
            throw WebserviceOperations.translateSearchException(e);
        } catch (ExecutionException | InvalidQuery e) {
            logger.debug(LOG_MSG_SEARCHING_THREW_AN_EXCEPTION, e);
            throw WebserviceOperations.translateSearchException(e);
        }
    }

    private static HitsFromGroup getHitsFromGroup(AnnotatedField field, SearchHitGroups searchHitsGrouped,
            String viewGroup, HitProperty sortBy) throws InterruptedException, ExecutionException {
        // Find the group we want to view
        SearchCacheEntry<HitGroups> jobHitGroups = searchHitsGrouped.executeAsync();
        PropertyValue viewGroupVal = PropertyValue.deserialize(field, viewGroup);
        if (viewGroupVal == null)
            throw new BadRequest("ERROR_IN_GROUP_VALUE", "Cannot deserialize group value: " + viewGroup);
        HitGroups hitGroups = jobHitGroups.get();
        HitGroup group = hitGroups.get(viewGroupVal);
        if (group == null)
            throw new BadRequest("GROUP_NOT_FOUND", "Group not found: " + viewGroup);

        // Sort group if needed
        // NOTE: sortBy is automatically applied to regular results, but not to results within groups
        // See ResultsGrouper::init (uses hits.getByOriginalOrder(i)) and DocResults::constructor
        // Also see SearchParams (hitsSortSettings, docSortSettings, hitGroupsSortSettings, docGroupsSortSettings)
        // There is probably no reason why we can't just sort/use the sort of the input results, but we need some more
        // testing to see if everything is correct if we change this
        HitResults hitResults = group.storedResults();
        if (sortBy != null)
            hitResults = hitResults.sorted(sortBy);

        return new HitsFromGroup(jobHitGroups, hitResults, group, hitGroups.groupCriteria().propsList());
    }

    private ResultHits(RequestHits reqHits, HitResults hitResults, SearchCacheEntry<?> cacheEntry, ResultsStats hitsStats,
            ResultsStats docsStats, boolean isViewGroup,
            HitOrDocGroup group, List<? extends ResultProperty> groupCriteria) {
        this.reqHits = reqHits;
        ParamsForResponse paramsForResponse = reqHits.paramsForResponse();
        this.hitResults = hitResults;
        this.cacheEntry = cacheEntry;
        this.hitsStats = hitsStats;
        this.docsStats = docsStats;
        this.group = group;
        this.groupCriteria = groupCriteria;

        searchFacets = reqHits.facets();

        //long maxWindowSize = params.getSearchManager().config().getParameters().getPageSize().getMax();
        WindowSettings windowSettings = reqHits.windowSettings();
        if (!hitResults.getHits().sizeAtLeast(windowSettings.first()))
            throw new BadRequest("HIT_NUMBER_OUT_OF_RANGE", "Non-existent hit number specified.");

        HitResults window;
        if (!isViewGroup) {
            // Request the window of hits we're interested in.
            // (we hold on to the cache entry so that we can differentiate between search and count time later)
            cacheEntryWindow = hitsWindow(reqHits).executeAsync();
            try {
                window = cacheEntryWindow.get(); // blocks until requested hits window is available
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // preserve interrupted status
                throw WebserviceOperations.translateSearchException(e);
            } catch (ExecutionException e) {
                throw WebserviceOperations.translateSearchException(e);
            }
        } else {
            // We're viewing a single group in a grouping result. Just get the hits window directly.
            cacheEntryWindow = null;
            window = hitResults.window(windowSettings.first(), windowSettings.size());
        }

        CorpusSize matchingDocsSize = null;
        if (reqHits.includeSubcorpusSize()) {
            matchingDocsSize = hitResults.perDocResults(Results.NO_LIMIT).subcorpusSize();
            // Determine total number of tokens in result set
            tokensInMatchingDocuments = matchingDocsSize.getTotalCount().getTokens();
        } else {
            tokensInMatchingDocuments = -1;
        }

        // Find KWICs/concordances from forward index or original XML
        // (note that on large indexes, this can actually take significant time)
        long startTimeKwicsMs = System.currentTimeMillis();
        ContextSettings contextSettings = reqHits.contextSettings();
        Hits windowHits = window.getHits().getStatic();
        concordanceContext = ConcordanceContext.get(windowHits, contextSettings.concType(), contextSettings.size());
        kwicTimeMs = System.currentTimeMillis() - startTimeKwicsMs;

        Map<Integer, Document> luceneDocs = new HashMap<>();
        BlackLabIndex index = reqHits.index();
        docIdToPid = WebserviceOperations.collectDocsAndPids(index, windowHits, luceneDocs);
        annotationsToWrite = reqHits.hitsResponseSettings().annotationsToInclude();
        docInfos = WebserviceOperations.getDocInfos(index, luceneDocs, reqHits.metadataToInclude());

        specialMetadataFields = WebserviceOperations.getDocFields(index);
        metaDisplayNames = WebserviceOperations.getMetaDisplayNames(index);


        SearchTimings searchTimings = getSearchTimings();
        ResultsStats hitsStats1 = getHitsStats();
        ResultsStats docsStats1 = getDocsStats();
        summaryNumHits = new ResultSummaryNumHits(hitsStats1, docsStats1, reqHits.waitForTotal(), searchTimings,
                matchingDocsSize);
        MatchInfoDefs matchInfoDefs = hitResults.getHits().matchInfoDefs();
        Set<AnnotatedField> otherFields = new HashSet<>();
        for (MatchInfo.Def def : matchInfoDefs.currentList()) {
            otherFields.add(def.getField());
            if (def.getTargetField() != null)
                otherFields.add(def.getTargetField());
        }
        otherFields.remove(hitResults.field());
        WindowStats window1 = window.windowStats();
        AnnotatedField searchField = hitResults.field();
        summaryCommonFields = new ResultSummaryCommonFields(reqHits.patternOriginal(), searchTimings, matchInfoDefs, null, window1, searchField,
                otherFields, reqHits.sampleParams(), paramsForResponse, null, summaryNumHits
        );
        ConcordanceContext concordanceContext1 = getConcordanceContext();
        listOfHits = new ResultListOfHits(window, concordanceContext1, getDocIdToPid(), contextSettings,
                annotationsToWrite,
                reqHits.hitsResponseSettings().omitEmptyCaptures());
    }

    private SearchHits hitsWindow(RequestHits reqHits) {
        WindowSettings windowSettings = reqHits.windowSettings();
        SearchHits sample = RequestHits.createSearch(reqHits);
        return windowSettings == null ? sample : sample.window(windowSettings.first(), windowSettings.size());
    }

    record HitsFromGroup(SearchCacheEntry<?> cacheEntry, HitResults hitResults, HitOrDocGroup group,
                         List<? extends ResultProperty> criteria) {}

    public synchronized Map<String, List<Pair<String, Long>>> getFacetInfo() throws InvalidQuery {
        if (facetInfo == null) {
            Map<DocProperty, DocGroups> counts = searchFacets.execute().countsPerFacet();
            facetInfo = WebserviceOperations.getFacetInfo(counts);
        }
        return facetInfo;
    }

    private SearchTimings getSearchTimings() {
        long searchTime = getSearchTime();
        long countTime = getCountTime();
        logger.info("Total search time is:{} ms", searchTime);
        return new SearchTimings(searchTime, countTime);
    }

    public long getSearchTime() {
        return (cacheEntryWindow == null ? cacheEntry.timer().time() : cacheEntryWindow.timer().time()) + kwicTimeMs;
    }

    public long getCountTime() {
        return cacheEntry.threwException() ? -1 : cacheEntry.timer().time();
    }

    public RequestHits getReqHits() {
        return reqHits;
    }

    public HitResults getHits() {
        return hitResults;
    }

    public long getTokensInMatchingDocuments() {
        return tokensInMatchingDocuments;
    }

    public ConcordanceContext getConcordanceContext() {
        return concordanceContext;
    }

    public boolean hasFacets() {
        return searchFacets != null;
    }

    public ResultsStats getHitsStats() {
        return hitsStats;
    }

    public ResultsStats getDocsStats() {
        return docsStats;
    }

    public Map<Integer, String> getDocIdToPid() {
        return docIdToPid;
    }

    public Map<String, ResultDocInfo> getDocInfos() {
        return docInfos;
    }

    public List<? extends ResultProperty> getGroupCriteria() {
        return groupCriteria;
    }

    public HitOrDocGroup getGroup() {
        return group;
    }

    public List<Annotation> getAnnotationsToWrite() {
        return annotationsToWrite;
    }

    public ParamsForResponse paramsForResponse() {
        return reqHits.paramsForResponse();
    }

    public ResultSummaryCommonFields getSummaryCommonFields() {
        return summaryCommonFields;
    }

    public ResultSummaryNumHits getSummaryNumHits() {
        return summaryNumHits;
    }

    public ResultListOfHits getListOfHits() {
        return listOfHits;
    }

    /** (API v4 included special metadata fields (pidField, etc.) in response) */
    public Map<String, String> getSpecialMetadataFields() {
        return specialMetadataFields;
    }

    /** (API v4 included metadata field display names in response) */
    public Map<String, String> getMetaDisplayNames() {
        return metaDisplayNames;
    }
}
