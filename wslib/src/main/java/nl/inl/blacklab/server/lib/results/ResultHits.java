package nl.inl.blacklab.server.lib.results;

import java.util.Collection;
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
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.BytesRef;

import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.HitPropertyDoc;
import nl.inl.blacklab.resultproperty.HitPropertyDocumentId;
import nl.inl.blacklab.resultproperty.HitPropertyDocumentStoredField;
import nl.inl.blacklab.resultproperty.HitPropertyHitText;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.SingleDocIdFilter;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;
import nl.inl.blacklab.search.results.CorpusSize;
import nl.inl.blacklab.search.results.DocGroups;
import nl.inl.blacklab.search.results.HitGroup;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.Results;
import nl.inl.blacklab.search.results.ResultsStats;
import nl.inl.blacklab.search.results.ResultsStatsSaved;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.TextPatternAnd;
import nl.inl.blacklab.search.textpattern.TextPatternTerm;
import nl.inl.blacklab.searches.SearchCacheEntry;
import nl.inl.blacklab.searches.SearchCount;
import nl.inl.blacklab.searches.SearchEmpty;
import nl.inl.blacklab.searches.SearchHitGroupsFromHits;
import nl.inl.blacklab.searches.SearchHits;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.index.Index;
import nl.inl.blacklab.server.index.IndexManager;
import nl.inl.blacklab.server.jobs.ContextSettings;
import nl.inl.blacklab.server.jobs.WindowSettings;
import nl.inl.blacklab.server.lib.ConcordanceContext;
import nl.inl.blacklab.server.lib.SearchTimings;
import nl.inl.blacklab.server.lib.WebserviceParams;
import nl.inl.blacklab.server.util.BlsUtils;

public class ResultHits {
    private static final Logger logger = LogManager.getLogger(ResultHits.class);

    private final WebserviceParams params;

    private final Hits hits;

    private ResultsStats hitsStats = null;

    private ResultsStats docsStats = null;

    private final boolean viewingGroup;

    private final SearchCacheEntry<?> cacheEntry;

    private SearchCacheEntry<Hits> cacheEntryWindow;

    private long kwicTimeMs;

    private Hits window;

    private long totalTokens;

    private ConcordanceContext concordanceContext;

    Map<String, List<Pair<String, Long>>> facetInfo;

    private Map<Integer, String> docIdToPid;

    private Map<String, ResultDocInfo> docInfos;

    private Map<String, String> docFields;

    private Map<String, String> metaDisplayNames;

    private Index.IndexStatus indexStatus;

    private ResultSummaryNumHits summaryNumHits;

    private ResultSummaryCommonFields summaryCommonFields;

    private ResultListOfHits listOfHits;

    @SuppressWarnings("unchecked")
    ResultHits(WebserviceParams params, boolean includeIndexStatus) {
        this.params = params;
        indexStatus = null;
        if (includeIndexStatus) {
            IndexManager indexMan = params.getIndexManager();
            indexStatus = indexMan.getIndex(params.getCorpusName()).getStatus();
        }

        // Do we want to view a single group after grouping?
        String groupBy = params.getGroupProps().orElse("");
        String viewGroup = params.getViewGroup().orElse("");

        viewingGroup = !groupBy.isEmpty() && !viewGroup.isEmpty();
        boolean waitForTotal = params.getWaitForTotal();
        try {
            if (viewingGroup) {
                // We're viewing a single group. Get the hits from the grouping results.
                Pair<SearchCacheEntry<?>, Hits> res = getHitsFromGroup(params, viewGroup);
                cacheEntry = res.getLeft();
                hits = res.getRight();
                // The hits are already complete - get the stats directly.
                hitsStats = hits.resultsStats();
                docsStats = hits.docsStats();
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
                hits = searchHits.execute();
                try {
                    hitsStats = ((SearchCacheEntry<ResultsStats>) cacheEntry).peek();
                    docsStats = searchDocCount.executeAsync().peek();
                    // Wait until all hits have been counted.
                    if (waitForTotal) {
                        hitsStats.waitUntil().allCounted();
                        docsStats.waitUntil().allCounted();
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
    }

    /**
     * Translate the normal Hits query in the searchparams object into a query yielding only those Hits in the group with the specified PropertyValue
     *
     * @param viewGroupVal identity of group to view
     * @param hitsGrouped  grouped hits
     * @return the SearchHits that will yield the hits, or null if the search could not be reconstructed.
     */
    private static SearchHits getQueryForHitsInSpecificGroupOnly(WebserviceParams params, PropertyValue viewGroupVal,
            HitGroups hitsGrouped) throws BlsException, InvalidQuery {
        // see if we can enhance this query
        if (hitsGrouped.sampleParameters() != null)
            return null;

        HitProperty groupByProp = hitsGrouped.groupCriteria();

        // see if this query matches only singular tokens
        // (we can't enhance multi-token queries such as ngrams yet)
        TextPattern tp = params.patternWithinContextTag().orElseThrow();
        BlackLabIndex index = params.blIndex();
        if (!tp.toQuery(QueryInfo.create(index, params.getAnnotatedField())).guarantees().producesSingleTokens())
            return null;

        // Alright, the original query for the Hits lends itself to enhancement.
        // Create the Query that will do the metadata filtering portion. (Token filtering is done through the TextPattern above)
        BooleanQuery.Builder fqb = new BooleanQuery.Builder();
        boolean usedFilter = false;
        if (params.filterQuery() != null) {
            fqb.add(params.filterQuery(), Occur.FILTER);
            usedFilter = true;
        }

        // Decode the grouping properties, and the values for those properties in the requested group.
        // So we can enhance the BooleanQuery and TextPattern with these  criteria
        List<PropertyValue> vals = viewGroupVal.valuesList();
        List<HitProperty> props = groupByProp.propsList();

        int i = 0;
        for (HitProperty p: props) {
            if (p instanceof HitPropertyHitText prop) {
                String valueForAnnotation = vals.get(i).toString();
                Annotation annot = prop.getAnnotation();
                MatchSensitivity sensitivity = prop.getSensitivity();

                tp = new TextPatternAnd(tp, new TextPatternTerm(valueForAnnotation, annot.name(), sensitivity));
            } else if (p instanceof HitPropertyDoc || p instanceof HitPropertyDocumentId) {
                Object value = vals.get(i).value();
                int luceneDocId = value instanceof Integer ?
                        ((int) value) :
                        BlsUtils.getDocIdFromPid(index, (String) value);
                fqb.add(new SingleDocIdFilter(luceneDocId), Occur.FILTER);
                usedFilter = true;
            } else if (p instanceof HitPropertyDocumentStoredField) {
                // https://github.com/apache/lucene/commit/0bc41356955cbf0144aa37203c6269256cf62555#diff-8d710e550a9661ad8a40b284a1f2ddc26a3b58477bf55d52eeed3f2f0576385cL169
                fqb.add(SortedDocValuesField.newSlowSetQuery(((HitPropertyDocumentStoredField) p).fieldName(),new BytesRef((String) vals.get(i).value())),
                        Occur.FILTER);
                usedFilter = true;
            } else {
                logger.debug("Cannot merge group specifier into query: {} with value {}", p,
                        vals.get(i));
                return null;
            }

            ++i;
        }

        // All specifiers merged!
        // Construct the query that will get us our hits.
        SearchEmpty search = index.search(params.getAnnotatedField(), params.useCache());
        Query filter = usedFilter ? fqb.build() : null;
        BLSpanQuery query = tp.toQuery(search.queryInfo(), filter, params.getAdjustRelationHits(), params.getWithSpans());
        SearchHits hits = search.find(query, params.searchSettings());
        if (params.hitsSortSettings() != null) {
            hits = hits.sort(params.hitsSortSettings().sortBy());
        }
        if (params.sampleSettings() != null) {
            hits = hits.sample(params.sampleSettings());
        }
        return hits;
    }

    private static Pair<SearchCacheEntry<?>, Hits> getHitsFromGroup(WebserviceParams params, String viewGroup)
            throws InterruptedException, ExecutionException, InvalidQuery, BlsException {
        BlackLabIndex index = params.blIndex();
        PropertyValue viewGroupVal = PropertyValue.deserialize(index, params.getAnnotatedField(), viewGroup);
        if (viewGroupVal == null)
            throw new BadRequest("ERROR_IN_GROUP_VALUE", "Cannot deserialize group value: " + viewGroup);
        SearchCacheEntry<HitGroups> jobHitGroups = params.hitsGroupedStats().executeAsync();
        HitGroups hitGroups = jobHitGroups.get();
        HitGroup group = hitGroups.get(viewGroupVal);
        if (group == null)
            throw new BadRequest("GROUP_NOT_FOUND", "Group not found: " + viewGroup);

        Hits hits;
        // Groups don't always store their backing hits (see HitGroupsTokenFrequencies for example)
        // When the group has some hits available, show those (the rest may have been culled on purpose due to maximum result limitations)
        // Only launch a separate search when there are ZERO hits stored in the group
        if (group.storedResults().size() > 0) {
            // Some hits available: return those.
            hits = group.storedResults();
        } else {
            // No results were actually stored. Fire a separate query to retrieve them.
            SearchHits findHitsFromOnlyRequestedGroup = getQueryForHitsInSpecificGroupOnly(params, viewGroupVal,
                    hitGroups);
            if (findHitsFromOnlyRequestedGroup != null) {
                // place the group-contents query in the cache and return the results.
                SearchCacheEntry<ResultsStats> cacheEntry = findHitsFromOnlyRequestedGroup.count().executeAsync();
                hits = (findHitsFromOnlyRequestedGroup.executeAsync()).get();
                return Pair.of(cacheEntry, hits);
            }

            // This is a special case:
            // Since the group we got from the cached results didn't contain the hits, we need to get the hits from their original query
            // and then group them here (using a different code path, since the normal code path  doesn't always store the hits due to performance).
            // And, since retrieving just the hits for one group couldn't be done (findHitsFromOnlyRequestedGroup == null), we need to unfortunately get all hits.
            SearchHitGroupsFromHits searchGroups = (SearchHitGroupsFromHits) params.hitsSample()
                    .groupWithStoredHits(hitGroups.groupCriteria(), Results.NO_LIMIT);
            // now run the separate grouping search, making sure not to actually store the hits.
            // Sorting of the resultant groups is not applied, but is also not required because the groups aren't shown, only their contents.
            // If a later query requests the groups in a sorted order, the cache will ensure these results become the input to that query anyway, so worst case we just deferred the work.
            jobHitGroups = searchGroups.executeAsync(); // place groups with hits in search cache
            hits = jobHitGroups
                    .get() //get grouped results
                    .get(viewGroupVal) // get group
                    .storedResults(); // get results in group
        }

        // NOTE: sortBy is automatically applied to regular results, but not to results within groups
        // See ResultsGrouper::init (uses hits.getByOriginalOrder(i)) and DocResults::constructor
        // Also see SearchParams (hitsSortSettings, docSortSettings, hitGroupsSortSettings, docGroupsSortSettings)
        // There is probably no reason why we can't just sort/use the sort of the input results, but we need some more testing to see if everything is correct if we change this
        String sortBy = params.getSortProps().orElse(null);
        HitProperty sortProp = HitProperty.deserialize(hits, sortBy, params.getContext());
        if (sortProp != null)
            hits = hits.sorted(sortProp);

        return Pair.of(jobHitGroups, hits);
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

    public Hits getHits() {
        return hits;
    }

    public void finishSearch() {
        WindowSettings windowSettings = params.windowSettings();
        if (!hits.resultsStats().waitUntil().processedAtLeast(windowSettings.first()))
            throw new BadRequest("HIT_NUMBER_OUT_OF_RANGE", "Non-existent hit number specified.");

        cacheEntryWindow = null;
        if (!viewingGroup) {
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
            window = hits.window(windowSettings.first(), windowSettings.size());
        }

        totalTokens = -1;
        CorpusSize subcorpusSize = null;
        if (params.getIncludeSubcorpusSize()) {
            subcorpusSize = hits.perDocResults(Results.NO_LIMIT)
                    .subcorpusSize();
            // Determine total number of tokens in result set
            totalTokens = subcorpusSize.getTotalCount().getTokens();
        }

        // Find KWICs/concordances from forward index or original XML
        // (note that on large indexes, this can actually take significant time)
        long startTimeKwicsMs = System.currentTimeMillis();
        ContextSettings contextSettings = params.contextSettings();
        concordanceContext = ConcordanceContext.get(window, contextSettings.concType(), contextSettings.size());
        kwicTimeMs = System.currentTimeMillis() - startTimeKwicsMs;

        Map<Integer, Document> luceneDocs = new HashMap<>();
        BlackLabIndex index = params.blIndex();
        docIdToPid = WebserviceOperations.collectDocsAndPids(index, window, luceneDocs);
        Collection<MetadataField> metadataFieldsToList = WebserviceOperations.getMetadataToWrite(params);
        docInfos = WebserviceOperations.getDocInfos(index, luceneDocs, metadataFieldsToList);

        docFields = WebserviceOperations.getDocFields(index);
        metaDisplayNames = WebserviceOperations.getMetaDisplayNames(index);

        SearchTimings searchTimings = getSearchTimings();
        summaryNumHits = WebserviceOperations.numResultsSummaryHits(
                getHitsStats(), getDocsStats(),
                params.getWaitForTotal(), searchTimings, subcorpusSize);
        MatchInfoDefs matchInfoDefs = hits.matchInfoDefs();
        Set<AnnotatedField> otherFields = new HashSet<>();
        for (MatchInfo.Def def : matchInfoDefs.currentList()) {
            otherFields.add(def.getField());
            if (def.getTargetField() != null)
                otherFields.add(def.getTargetField());
        }
        otherFields.remove(hits.field());
        summaryCommonFields = WebserviceOperations.summaryCommonFields(params,
                getIndexStatus(), searchTimings, matchInfoDefs, null, window.windowStats(),
                hits.field(), otherFields);
        listOfHits = WebserviceOperations.listOfHits(params, window, getConcordanceContext(),
                getDocIdToPid());
    }

    public long getSearchTime() {
        return (cacheEntryWindow == null ? cacheEntry.timer().time() : cacheEntryWindow.timer().time()) + kwicTimeMs;
    }

    public long getCountTime() {
        return cacheEntry.threwException() ? -1 : cacheEntry.timer().time();
    }

    public Hits getWindow() {
        return window;
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

    public Index.IndexStatus getIndexStatus() {
        return indexStatus;
    }

    public WebserviceParams getParams() {
        return params;
    }

    public ResultSummaryNumHits getSummaryNumHits() {
        return summaryNumHits;
    }

    public ResultSummaryCommonFields getSummaryCommonFields() {
        return summaryCommonFields;
    }

    public ResultListOfHits getListOfHits() {
        return listOfHits;
    }

    public BlackLabIndex getIndex() {
        return params.blIndex();
    }
}
