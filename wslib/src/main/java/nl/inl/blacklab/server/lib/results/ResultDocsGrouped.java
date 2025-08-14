package nl.inl.blacklab.server.lib.results;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.search.results.CorpusSize;
import nl.inl.blacklab.search.results.WindowStats;
import nl.inl.blacklab.search.results.docs.DocGroup;
import nl.inl.blacklab.search.results.docs.DocGroups;
import nl.inl.blacklab.search.results.docs.DocResults;
import nl.inl.blacklab.search.results.stats.ResultsStats;
import nl.inl.blacklab.searches.SearchCacheEntry;
import nl.inl.blacklab.server.index.Index;
import nl.inl.blacklab.server.lib.SearchTimings;
import nl.inl.blacklab.server.lib.WebserviceParams;

public class ResultDocsGrouped {

    private final WebserviceParams params;

    private final DocGroups groups;

    private final WindowStats ourWindow;

    private ResultSummaryNumDocs numResultDocs;

    private ResultSummaryNumHits numResultHits;

    private final ResultSummaryCommonFields summaryFields;

    private final List<CorpusSize> corpusSizes;

    ResultDocsGrouped(WebserviceParams params) throws InvalidQuery {
        this.params = params;

        // Make sure we have the hits search, so we can later determine totals.
        SearchCacheEntry<ResultsStats> originalHitsSearch = null;
        if (params.hasPattern()) {
            originalHitsSearch = params.hitsSample().hitCount().executeAsync();
        }
        // Get the window we're interested in
        DocResults docResults = params.docs().execute();
        SearchCacheEntry<DocGroups> groupSearch = params.docsGrouped().executeAsync();
        try {
            groups = groupSearch.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // preserve interrupted status
            throw WebserviceOperations.translateSearchException(e);
        } catch (ExecutionException e) {
            throw WebserviceOperations.translateSearchException(e);
        }

        // Search is done; construct the results object

        long first = params.getFirstResultToShow();
        if (first < 0)
            first = 0;
        long number = params.getNumberOfResultsToShow();
        if (number < 0 || number > params.getSearchManager().config().getParameters().getPageSize().getMax())
            number = params.getSearchManager().config().getParameters().getPageSize().getDefault();
        long numberOfGroupsInWindow = 0;
        numberOfGroupsInWindow = number;
        if (first + number > groups.size())
            numberOfGroupsInWindow = groups.size() - first;
        ourWindow = new WindowStats(first + number < groups.size(), first, number, numberOfGroupsInWindow);

        ResultsStats hitsStats, docsStats;
        hitsStats = originalHitsSearch == null ? null : originalHitsSearch.peek();
        docsStats = params.docsCount().executeAsync().peek();

        // The list of groups found
        boolean hasPattern = params.hasPattern();
        DocProperty metadataGroupProperties = groups.groupCriteria();
        DocResults subcorpus = params.subcorpus().execute();
        CorpusSize subcorpusSize = subcorpus.subcorpusSize();

        SearchTimings timings = new SearchTimings(groupSearch.timer().time(), 0);
        Index.IndexStatus indexStatus = params.getIndexManager().getIndex(params.getCorpusName()).getStatus();
        summaryFields = WebserviceOperations.summaryCommonFields(params,
                indexStatus, timings, null, groups, ourWindow, docResults.field(),
                Collections.emptyList());

        numResultDocs = null;
        numResultHits = null;
        if (hitsStats == null) {
            numResultDocs = WebserviceOperations.numResultsSummaryDocs(false, docResults, timings,
                    subcorpusSize);
        } else {
            numResultHits = WebserviceOperations.numResultsSummaryHits(
                    hitsStats, docsStats, true, timings, subcorpusSize);
        }

        corpusSizes = new ArrayList<>();
        if (hasPattern) {
            for (long i = ourWindow.first(); i <= ourWindow.last(); ++i) {
                DocGroup group = groups.get(i);
                // Find size of corresponding subcorpus group
                CorpusSize size = WebserviceOperations.findSubcorpusSize(params, subcorpus.query(),
                        metadataGroupProperties, group.identity());
                corpusSizes.add(size);
            }
        }
    }

    public WebserviceParams getParams() {
        return params;
    }

    public ResultSummaryNumDocs getNumResultDocs() {
        return numResultDocs;
    }

    public ResultSummaryNumHits getNumResultHits() {
        return numResultHits;
    }

    public ResultSummaryCommonFields getSummaryFields() {
        return summaryFields;
    }

    public List<CorpusSize> getCorpusSizes() {
        return corpusSizes;
    }

    public DocGroups getGroups() {
        return groups;
    }

    public WindowStats getOurWindow() {
        return ourWindow;
    }
}
