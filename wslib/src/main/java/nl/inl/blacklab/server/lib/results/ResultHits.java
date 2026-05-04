package nl.inl.blacklab.server.lib.results;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.document.Document;

import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.ResultProperty;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;
import nl.inl.blacklab.search.results.CorpusSize;
import nl.inl.blacklab.search.results.HitOrDocGroup;
import nl.inl.blacklab.search.results.Results;
import nl.inl.blacklab.search.results.docs.DocGroups;
import nl.inl.blacklab.search.results.docs.DocResults;
import nl.inl.blacklab.search.results.hitresults.HitGroup;
import nl.inl.blacklab.search.results.hitresults.HitGroups;
import nl.inl.blacklab.search.results.hitresults.HitResults;
import nl.inl.blacklab.search.results.hits.Hits;
import nl.inl.blacklab.search.results.stats.ResultsStats;
import nl.inl.blacklab.search.results.stats.ResultsStatsSaved;
import nl.inl.blacklab.searches.SearchCacheEntry;
import nl.inl.blacklab.searches.SearchCount;
import nl.inl.blacklab.searches.SearchHits;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.index.Index;
import nl.inl.blacklab.server.index.IndexManager;
import nl.inl.blacklab.server.jobs.ContextSettings;
import nl.inl.blacklab.server.jobs.WindowSettings;
import nl.inl.blacklab.server.lib.ConcordanceContext;
import nl.inl.blacklab.server.lib.SearchTimings;
import nl.inl.blacklab.server.lib.WebserviceParams;

public class ResultHits {
    private static final Logger logger = LogManager.getLogger(ResultHits.class);

    private final WebserviceParams params;

    private final HitResults hitResults;

    private List<? extends ResultProperty> groupCriteria = null;

    private HitOrDocGroup group = null;

    private ResultsStats hitsStats = null;

    private ResultsStats docsStats = null;

    private DocResults subcorpusResults = null;

    private final boolean isViewGroup;

    private final SearchCacheEntry<?> cacheEntry;

    private SearchCacheEntry<HitResults> cacheEntryWindow;

    private final long kwicTimeMs;

    private final HitResults window;

    private long totalTokens;

    private final ConcordanceContext concordanceContext;

    Map<String, List<Pair<String, Long>>> facetInfo;

    private final Map<Integer, String> docIdToPid;

    private final Map<String, ResultDocInfo> docInfos;

    private final Map<String, String> docFields;

    private final Map<String, String> metaDisplayNames;

    private final List<Annotation> annotationsToWrite;

    private Index.IndexStatus indexStatus;

    private final ResultSummaryCommonFields summaryCommonFields;

    private final ResultSummaryNumHits summaryNumHits;

    private final ResultListOfHits listOfHits;

    /**
     * Get the hits (and the groups from which they were extracted - if applicable)
     * for this request. Exceptions cleanly mapping to http error
     * responses are thrown if any part of the request cannot be fulfilled. Sorting
     * is already applied to the hits.
     */
    @SuppressWarnings("unchecked")
    ResultHits(WebserviceParams params, boolean includeIndexStatus, long maxWindowSize) {
        this.params = params;
        indexStatus = null;
        if (includeIndexStatus) {
            IndexManager indexMan = params.getIndexManager();
            indexStatus = indexMan.getIndex(params.getCorpusName()).getStatus();
        }

        // Do we want to view a single group after grouping?
        Optional<String> groupBy = params.getGroupProps();
        Optional<String> viewGroup = params.getViewGroup();

        isViewGroup = groupBy.isPresent() && viewGroup.isPresent();
        boolean waitForTotal = params.getWaitForTotal();

        try {
            if (isViewGroup) {
                // We're viewing a single group. Get the hits from the grouping results.
                HitsFromGroup res = getHitsFromGroup(params, viewGroup.get());
                cacheEntry = res.cacheEntry();
                hitResults = res.hitResults();
                group = res.group();
                groupCriteria = res.criteria();

                // The hits are already complete - get the stats directly.
                hitsStats = hitResults.resultsStats();
                docsStats = hitResults.docsStats();
            } else {
                // Regular hits request.
                // Create the search objects
                SearchHits searchHits = params.hitsSample();
                SearchCount searchHitCount = searchHits.hitCount();
                SearchCount searchDocCount = searchHits.docCount();
                // Start the search.
                // - First start the hit count, which will start the underlying hits search.
                // - Then get the underlying hits search from the cache (this may take a while as
                //   it will complete when the Hits object is available)
                cacheEntry = searchHitCount.executeAsync();
                hitResults = searchHits.execute();
                try {
                    hitsStats = ((SearchCacheEntry<ResultsStats>) cacheEntry).peek();
                    docsStats = searchDocCount.executeAsync().peek();
                    // Wait until all hits have been counted.
                    if (waitForTotal) {
                        hitsStats.countedTotal();
                        docsStats.countedTotal();
                    }
                } catch (InterruptedSearch e) {
                    // Our count was probably aborted.
                    logger.debug("Error getting count(s)", e);
                    if (hitsStats == null)
                        hitsStats = ResultsStatsSaved.INVALID;
                    if (docsStats == null)
                        docsStats = ResultsStatsSaved.INVALID;
                    throw e;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // preserve interrupted status
            logger.debug("Searching threw an exception", e);
            throw WebserviceOperations.translateSearchException(e);
        } catch (ExecutionException | InvalidQuery e) {
            logger.debug("Searching threw an exception", e);
            throw WebserviceOperations.translateSearchException(e);
        }

        //long maxWindowSize = params.getSearchManager().config().getParameters().getPageSize().getMax();
        WindowSettings windowSettings = params.windowSettings(maxWindowSize);
        if (!hitResults.getHits().sizeAtLeast(windowSettings.first()))
            throw new BadRequest("HIT_NUMBER_OUT_OF_RANGE", "Non-existent hit number specified.");

        cacheEntryWindow = null;
        if (!isViewGroup) {
            // Request the window of hits we're interested in.
            // (we hold on to the cache entry so that we can differentiate between search and count time later)
            cacheEntryWindow = params.hitsWindow().executeAsync();
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
            window = hitResults.window(windowSettings.first(), windowSettings.size());
        }

        totalTokens = -1;
        CorpusSize subcorpusSize = null;
        if (params.getIncludeSubcorpusSize()) {
            subcorpusSize = hitResults.perDocResults(Results.NO_LIMIT)
                    .subcorpusSize();
            // Determine total number of tokens in result set
            totalTokens = subcorpusSize.getTotalCount().getTokens();
        }

        // Find KWICs/concordances from forward index or original XML
        // (note that on large indexes, this can actually take significant time)
        long startTimeKwicsMs = System.currentTimeMillis();
        ContextSettings contextSettings = params.contextSettings();
        Hits windowHits = window.getHits().getStatic();
        concordanceContext = ConcordanceContext.get(windowHits, contextSettings.concType(), contextSettings.size());
        kwicTimeMs = System.currentTimeMillis() - startTimeKwicsMs;

        Map<Integer, Document> luceneDocs = new HashMap<>();
        BlackLabIndex index = params.blIndex();
        docIdToPid = WebserviceOperations.collectDocsAndPids(index, windowHits, luceneDocs);
        Collection<MetadataField> metadataFieldsToList = WebserviceOperations.getMetadataToWrite(params);
        docInfos = WebserviceOperations.getDocInfos(index, luceneDocs, metadataFieldsToList);

        docFields = WebserviceOperations.getDocFields(index);
        metaDisplayNames = WebserviceOperations.getMetaDisplayNames(index);

        annotationsToWrite = WebserviceOperations.getAnnotationsToWrite(params);

        SearchTimings searchTimings = getSearchTimings();
        summaryNumHits = WebserviceOperations.numResultsSummaryHits(
                getHitsStats(), getDocsStats(),
                params.getWaitForTotal(), searchTimings, subcorpusSize);
        MatchInfoDefs matchInfoDefs = hitResults.getHits().matchInfoDefs();
        Set<AnnotatedField> otherFields = new HashSet<>();
        for (MatchInfo.Def def : matchInfoDefs.currentList()) {
            otherFields.add(def.getField());
            if (def.getTargetField() != null)
                otherFields.add(def.getTargetField());
        }
        otherFields.remove(hitResults.field());
        summaryCommonFields = WebserviceOperations.summaryCommonFields(params,
                getIndexStatus(), searchTimings, matchInfoDefs, null, window.windowStats(),
                hitResults.field(), otherFields);
        listOfHits = WebserviceOperations.listOfHits(params, window, getConcordanceContext(),
                getDocIdToPid());
    }

    record HitsFromGroup(SearchCacheEntry<?> cacheEntry, HitResults hitResults, HitOrDocGroup group,
                         List<? extends ResultProperty> criteria) {}

    private static HitsFromGroup getHitsFromGroup(WebserviceParams params, String viewGroup)
            throws InterruptedException, ExecutionException {
        PropertyValue viewGroupVal = PropertyValue.deserialize(params.blIndex(), params.getAnnotatedField(), viewGroup);
        if (viewGroupVal == null)
            throw new BadRequest("ERROR_IN_GROUP_VALUE", "Cannot deserialize group value: " + viewGroup);
        SearchCacheEntry<HitGroups> jobHitGroups = params.hitsGroupedStats().executeAsync();
        HitGroups hitGroups = jobHitGroups.get();
        HitGroup group = hitGroups.get(viewGroupVal);
        if (group == null)
            throw new BadRequest("GROUP_NOT_FOUND", "Group not found: " + viewGroup);

        List<HitProperty> groupCriteria = hitGroups.groupCriteria().propsList();
        HitResults hitResults = group.storedResults();

        // NOTE: sortBy is automatically applied to regular results, but not to results within groups
        // See ResultsGrouper::init (uses hits.getByOriginalOrder(i)) and DocResults::constructor
        // Also see SearchParams (hitsSortSettings, docSortSettings, hitGroupsSortSettings, docGroupsSortSettings)
        // There is probably no reason why we can't just sort/use the sort of the input results, but we need some more
        // testing to see if everything is correct if we change this
        HitProperty sortBy = params.hitsSortSettings() == null ? null : params.hitsSortSettings().sortBy();
        if (sortBy != null)
            hitResults = hitResults.sorted(sortBy);

        return new HitsFromGroup(jobHitGroups, hitResults, group, groupCriteria);
    }

    public synchronized Map<String, List<Pair<String, Long>>> getFacetInfo() throws InvalidQuery {
        if (facetInfo == null) {
            Map<DocProperty, DocGroups> counts = params.facets().execute().countsPerFacet();
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

    public HitResults getHits() {
        return hitResults;
    }

    public long getTotalTokens() {
        return totalTokens;
    }

    public ConcordanceContext getConcordanceContext() {
        return concordanceContext;
    }

    public boolean hasFacets() {
        return params.hasFacets();
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

    public Map<String, String> getDocFields() {
        return docFields;
    }

    public Map<String, String> getMetaDisplayNames() {
        return metaDisplayNames;
    }

    public synchronized DocResults getSubcorpusResults() {
        if (subcorpusResults == null) {
            subcorpusResults = params.subcorpus().execute();
        }
        return subcorpusResults;
    }

    public boolean isViewGroup() {
        return isViewGroup;
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

    public Index.IndexStatus getIndexStatus() {
        return indexStatus;
    }

    public WebserviceParams getParams() {
        return params;
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
}
