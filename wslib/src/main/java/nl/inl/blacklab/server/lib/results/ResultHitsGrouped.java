package nl.inl.blacklab.server.lib.results;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.document.Document;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;
import nl.inl.blacklab.search.results.CorpusSize;
import nl.inl.blacklab.search.results.ResultGroups;
import nl.inl.blacklab.search.results.WindowStats;
import nl.inl.blacklab.search.results.docs.DocResults;
import nl.inl.blacklab.search.results.hitresults.HitGroup;
import nl.inl.blacklab.search.results.hitresults.HitGroups;
import nl.inl.blacklab.search.results.hitresults.HitResults;
import nl.inl.blacklab.search.results.stats.ResultsStats;
import nl.inl.blacklab.searches.SearchCacheEntry;
import nl.inl.blacklab.searches.SearchHitGroups;
import nl.inl.blacklab.searches.SearchHits;
import nl.inl.blacklab.server.lib.SearchTimings;
import nl.inl.blacklab.server.lib.requests.RequestHitsGrouped;
import nl.inl.util.BlockTimer;

public class ResultHitsGrouped {

    private final RequestHitsGrouped reqGroup;

    private final HitGroups groups;

    private final WindowStats window;

    private final ResultsStats hitsStats;

    private ResultsStats docsStats;

    private final DocProperty metadataGroupProperties;

    private final CorpusSize subcorpusSize;

    private final List<ResultHitGroup> groupInfos;

    private Map<String, ResultDocInfo> docInfos;

    private final Query subcorpusQuery;

    private final ResultSummaryCommonFields summaryFields;

    private final ResultSummaryNumHits summaryNumHits;

    /**
     * Get the groups for this request.
     *
     * Exceptions cleanly mapping to http error
     * responses are thrown if any part of the request cannot be fulfilled.
     *
     * @param reqGroup grouping request
     */
    ResultHitsGrouped(RequestHitsGrouped reqGroup) throws InvalidQuery {
        this.reqGroup = reqGroup;

        SearchHits searchHits = reqGroup.requestHits().getSearch();
        HitResults hitResults = searchHits.execute(); // we need these later to get the match info defs
        SearchHitGroups searchHitGroups = searchHits
                .groupStats(reqGroup.groupBy(), reqGroup.maxHitsToStorePerGroup(),
                        reqGroup.groupScorer())
                .sort(reqGroup.sortGroupsBy());
        groups = searchHitGroups.execute();

        // apply window settings
        long first = Math.max(reqGroup.windowSettings().first(), 0);
        long requestedWindowSize = reqGroup.windowSettings().size();
        long totalResults = groups.size();
        long actualWindowSize = first + requestedWindowSize > totalResults ? totalResults - first
                : requestedWindowSize;
        window = new WindowStats(first + requestedWindowSize < totalResults, first, requestedWindowSize,
                actualWindowSize);

        hitsStats = groups.hitsStats();
        docsStats = groups.docsStats();
        if (docsStats == null)
            docsStats = searchHits.docCount().execute();

        // The list of groups found
        metadataGroupProperties = groups.groupCriteria().docPropsOnly();
        DocResults subcorpus;
        if (reqGroup.requestHits().includeSubcorpusSize()) {
            subcorpus = reqGroup.subcorpus().execute();
            subcorpusQuery = subcorpus.query();
            subcorpusSize = subcorpus.subcorpusSize();
        } else {
            subcorpus = null;
            subcorpusQuery = null;
            subcorpusSize = null;
        }

        /* Gather group values per property:
         * In the case we're grouping by multiple values, the DocPropertyMultiple and PropertyValueMultiple will
         * contain the sub properties and values in the same order.
         */
        List<HitProperty> prop = groups.groupCriteria().propsList();

        long last = Math.min(first + requestedWindowSize, groups.size());

        Map<Integer, Document> luceneDocs = new HashMap<>();
        groupInfos = new ArrayList<>();
        try (BlockTimer ignored = BlockTimer.create("Serializing groups to JSON")) {
            for (long i = first; i < last; ++i) {
                HitGroup group = groups.get(i);
                groupInfos.add(new ResultHitGroup(reqGroup, groups, group, metadataGroupProperties,
                        subcorpus, luceneDocs));
            }
        }

        docInfos = null;
        if (reqGroup.includeGroupContents()) {
            Collection<MetadataField> metadataToInclude = reqGroup.requestHits().metadataToInclude();
            docInfos = WebserviceOperations.getDocInfos(reqGroup.index(), luceneDocs, metadataToInclude);
        }

        // Determine time taken
        SearchCacheEntry<HitGroups> search = searchHitGroups.executeAsync();
        SearchTimings timings = new SearchTimings(search.timer().time(), 0);

        MatchInfoDefs matchInfoDefs = hitResults.getHits().matchInfoDefs();
        Set<AnnotatedField> otherFields = new HashSet<>();
        for (MatchInfo.Def def : matchInfoDefs.currentList()) {
            if (def.getTargetField() != null)
                otherFields.add(def.getTargetField());
        }

        ResultGroups groups1 = getGroups();
        WindowStats window1 = getWindow();
        AnnotatedField searchField = groups.field();
        ResultsStats hitsStats1 = getHitsStats();
        ResultsStats docsStats1 = getDocsStats();
        summaryNumHits = new ResultSummaryNumHits(hitsStats1, docsStats1, true, timings, getSubcorpusSize());
        summaryFields = new ResultSummaryCommonFields(reqGroup.patternOriginal(), timings, matchInfoDefs, groups1, window1, searchField,
                otherFields, reqGroup.requestHits().sampleParams(), reqGroup.paramsForResponse(),
                null, summaryNumHits
        );
    }

    public HitGroups getGroups() {
        return groups;
    }

    public WindowStats getWindow() {
        return window;
    }

    public ResultsStats getHitsStats() {
        return hitsStats;
    }

    public ResultsStats getDocsStats() {
        return docsStats;
    }

    public DocProperty getMetadataGroupProperties() {
        return metadataGroupProperties;
    }

    public CorpusSize getSubcorpusSize() {
        return subcorpusSize;
    }

    public List<ResultHitGroup> getGroupInfos() {
        return groupInfos;
    }

    public Map<String, ResultDocInfo> getDocInfos() {
        return docInfos;
    }

    public RequestHitsGrouped getReqGroup() {
        return reqGroup;
    }

    public ResultSummaryCommonFields getSummaryFields() {
        return summaryFields;
    }

    public ResultSummaryNumHits getSummaryNumHits() {
        return summaryNumHits;
    }

    public Query getSubcorpusQuery() {
        return subcorpusQuery;
    }
}
