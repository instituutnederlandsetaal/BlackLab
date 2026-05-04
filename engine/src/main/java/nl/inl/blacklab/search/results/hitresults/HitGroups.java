package nl.inl.blacklab.search.results.hitresults;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.tuple.Pair;

import com.ibm.icu.text.CollationKey;

import nl.inl.blacklab.Constants;
import nl.inl.blacklab.resultproperty.HitGroupProperty;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.ResultGroups;
import nl.inl.blacklab.search.results.ResultsList;
import nl.inl.blacklab.search.results.SampleParameters;
import nl.inl.blacklab.search.results.SearchSettings;
import nl.inl.blacklab.search.results.WindowStats;
import nl.inl.blacklab.search.results.hits.Group;
import nl.inl.blacklab.search.results.hits.HitsAbstract;
import nl.inl.blacklab.search.results.hits.fetch.HitPublisher;
import nl.inl.blacklab.search.results.hits.fetch.HitSubscriberGrouper;
import nl.inl.blacklab.search.results.stats.MaxStats;
import nl.inl.blacklab.search.results.stats.ResultsStats;
import nl.inl.blacklab.search.results.stats.ResultsStatsPassive;
import nl.inl.blacklab.search.results.stats.ResultsStatsSaved;
import nl.inl.blacklab.search.textpattern.CompleteQuery;
import nl.inl.blacklab.searches.SearchHits;

/**
 * Groups results on the basis of a list of criteria.
 *
 * This class allows random access to the groups, and each group provides random
 * access to the hits. Note that this means that all hits found must be
 * retrieved, which may be infeasible for large results sets.
 */
public class HitGroups extends ResultsList<HitGroup> implements ResultGroups, Iterable<HitGroup> {

    /**
     * Construct HitGroups from a list of HitGroup instances.
     *
     * @param queryInfo query info
     * @param results list of groups
     * @param groupCriteria what hits would be grouped by
     * @param sampleParameters how groups were sampled, or null if not applicable
     * @param windowStats what groups window this is, or null if not applicable
     * @param hitsStats hits statistics
     * @param docsStats docs statistics
     * @return grouped hits
     */
    public static HitGroups fromList(QueryInfo queryInfo, List<HitGroup> results, HitProperty groupCriteria,
            SampleParameters sampleParameters, WindowStats windowStats, ResultsStats hitsStats, ResultsStats docsStats) {
        return new HitGroups(queryInfo, results, groupCriteria, sampleParameters, windowStats, hitsStats, docsStats);
    }

    public static List<HitGroup> fromBasicGroup(QueryInfo queryInfo, Map<PropertyValue, Group> groupings) {
        List<HitGroup> groups = new ArrayList<>(groupings.size());
        for (Map.Entry<PropertyValue, Group> e : groupings.entrySet()) {
            PropertyValue groupId = e.getKey();
            Group grouped = e.getValue();
            HitGroup group = HitGroup.fromList(queryInfo, groupId, grouped.getStoredHits(), grouped.getTotalNumberOfHits(), grouped.getHitsInGroupQuery());
            groups.add(group);
        }
        return groups;
    }

    public static List<HitGroup> fromBasicGroupNoResults(QueryInfo queryInfo, Map<PropertyValue, Group> groupings,
            CompleteQuery query, HitProperty groupedBy) {
        List<HitGroup> groups = new ArrayList<>(groupings.size());
        for (Map.Entry<PropertyValue, Group> e : groupings.entrySet()) {
            PropertyValue groupId = e.getKey();
            Group grouped = e.getValue();
            CompleteQuery hitsInGroupQuery = groupedBy.refine(queryInfo.index(), query, groupId)
                    .orElseThrow();
            HitGroup group = HitGroup.withoutResults(queryInfo, groupId, grouped.getTotalNumberOfHits(),
                    grouped.getTotalNumberOfDocs(), MaxStats.NOT_EXCEEDED, hitsInGroupQuery);

            groups.add(group);
        }
        return groups;
    }


    private final HitProperty groupBy;

    /**
     * The groups.
     * Note that we keep the groups both in the ResultsList.results object for
     * the ordering and access by index as well as in this map to access by group
     * identity. Ideally this wouldn't be necessary, but we need direct access to
     * the ordering for e.g. paging.
     */
    private final Map<PropertyValue, HitGroup> groups;

    /** Maximum number of groups (limited by number of entries allowed in a HashMap) */
    public static final int MAX_NUMBER_OF_GROUPS = Constants.JAVA_MAX_HASHMAP_SIZE;

    /** Number of groups. */
    private final ResultsStatsSaved resultsStats;

    /**
     * Total number of hits in the source set of hits.
     * Note that unlike other Hits instances (samples/sorts/windows), we should safely be able to copy these from our source, 
     * because hits are always fully read before constructing groups.
     */
    protected final ResultsStatsSaved hitsStats;

    /**
     * Total number of documents in the source set of hits.
     * Note that unlike other Hits instances (samples/sorts/windows), we should safely be able to copy these from our source, 
     * because hits are always fully read before constructing groups.
     */
    protected final ResultsStatsSaved docsStats;

    /**
     * Size of the largest group.
     */
    private long largestGroupSize = 0;

    private final WindowStats windowStats;

    private final SampleParameters sampleParameters;

    /** How many hits are stored in this object. Used to determine what to keep in the cache
     *  (bigger resultsets won't be kept for as long as smaller resultsets) */
    private long resultObjects;

    protected HitGroups(QueryInfo queryInfo, List<HitGroup> groups, HitProperty groupCriteria, SampleParameters sampleParameters, WindowStats windowStats, ResultsStats hitsStats, ResultsStats docsStats) {
        super(queryInfo);
        this.groupBy = groupCriteria;
        this.windowStats = windowStats;
        this.sampleParameters = sampleParameters;
        this.groups = new HashMap<>();
        for (HitGroup group: groups) {
            if (group.size() > largestGroupSize)
                largestGroupSize = group.size();
            results.add(group);
            this.groups.put(group.identity(), group);
        }
        for (HitGroup group: groups) {
            if (group.numberOfStoredResults() == 0 && group.size() > 0) {
                // Not yet loaded. Count these for half, because they may eventually be loaded.
                // TODO: improve this! Needs to update if group loads hits, without being slow
                //   (i.e. don't recalculate every time cache checks this search), but group cannot
                //    easily notify us because the same group may be part of multiple grouping (i.e. sorted, ...)
                resultObjects += group.size() / 2;
            } else {
                resultObjects += group.numberOfStoredResults() + 1;
            }
        }

        // Make a copy so we don't keep any references to the source hits
        resultsStats = new ResultsStatsSaved(groups.size(), groups.size(), hitsStats.maxStats());
        this.hitsStats = hitsStats.save();
        this.docsStats = docsStats.save();
    }

    public static HitGroups withoutStoredHits(SearchHits source, HitProperty groupBy) {
        SearchSettings searchSettings = source.searchSettings();
        ResultsStatsPassive hitsStats = new ResultsStatsPassive(searchSettings.maxHitsToProcess(), searchSettings.maxHitsToCount());
        ResultsStatsPassive docsStats = new ResultsStatsPassive();
        QueryInfo queryInfo = source.queryInfo();
        List<HitPublisher> publishers = HitResultsFromQuery.getHitPublishers(queryInfo,
                source.getCombinedSpanFilterQuery(), searchSettings, hitsStats, docsStats);
        Map<PropertyValue, Group> groups = new ConcurrentHashMap<>();
        Map<String, CollationKey> collationCache = new ConcurrentHashMap<>();
        HitsAbstract.performPerPublisher(publishers,
                () -> new HitSubscriberGrouper(collationCache, groupBy, 0, groups, source.getCompleteQuery()), false);
        List<HitGroup> hitGroups = fromBasicGroupNoResults(queryInfo, groups, source.getCompleteQuery(), groupBy);
        return new HitGroups(queryInfo, hitGroups, groupBy, null, null,
                hitsStats.save(), docsStats.save());
    }

    @Override
    public ResultsStats resultsStats() {
        return resultsStats;
    }

    @Override
    public HitProperty groupCriteria() {
        return groupBy;
    }

    @Override
    public boolean ensureResultsRead(long number) {
        return size() >= number; // all results have been read
    }

    public HitGroups sort(HitGroupProperty sortProp) {
        ensureResultsRead(-1);
        List<HitGroup> sorted = new ArrayList<>(this.results);
        sorted.sort(sortProp);
        // Sorted contains the same hits as us, so we can pass on our result statistics.
        return HitGroups.fromList(queryInfo(), sorted, groupBy, null, null, hitsStats, docsStats);
    }
    
    /**
     * Take a sample of hits by wrapping an existing Hits object.
     *
     * @param sampleParameters sample parameters
     * @return the sample
     */
    public HitGroups sample(SampleParameters sampleParameters) {
        List<HitGroup> sample = doSample(this, sampleParameters);
        Pair<ResultsStatsSaved, ResultsStatsSaved> stats = getStatsOfSample(sample, this.hitsStats.maxStats(), this.docsStats.maxStats());
        return HitGroups.fromList(queryInfo(), sample, groupCriteria(), sampleParameters, null, stats.getLeft(), stats.getRight());
    }

    /**
     * Get the total number of hits
     *
     * @return the number of hits
     */
    @Override
    public long sumOfGroupSizes() {
        return hitsStats.countedTotal();
    }

    /**
     * Return the size of the largest group
     *
     * @return size of the largest group
     */
    @Override
    public long largestGroupSize() {
        return largestGroupSize;
    }

    @Override
    public String toString() {
        return "ResultsGrouper with " + size() + " groups";
    }

    public HitGroup get(PropertyValue identity) {
        return groups.get(identity);
    }

    public SampleParameters sampleParameters() {
        return sampleParameters;
    }

    public HitGroups window(long first, long number) {
        List<HitGroup> resultsWindow = doWindow(this, first, number);
        boolean hasNext = resultsStats().processedAtLeast(first + resultsWindow.size() + 1);
        WindowStats windowStats = new WindowStats(hasNext, first, number, resultsWindow.size());
        return HitGroups.fromList(queryInfo(), resultsWindow, groupBy, null, windowStats, this.hitsStats, this.docsStats); // copy actual totals. Window should be "transparent"
    }

    public HitGroups filter(HitGroupProperty property, PropertyValue value) {
        List<HitGroup> list = this.results.stream().filter(group -> property.get(group).equals(value)).toList();
        Pair<ResultsStatsSaved, ResultsStatsSaved> stats = getStatsOfSample(list, this.hitsStats.maxStats(), this.docsStats.maxStats());
        return HitGroups.fromList(queryInfo(), list, groupCriteria(), null, null, stats.getLeft(), stats.getRight());
    }

    @Override
    public long numberOfResultObjects() {
        return resultObjects;
    }

    /** 
     * Get document stats for these groups.
     * NOTE: docsCounted will return -1 if this HitGroups instance is a sample and hasn't got all hits stored 
     * (it is impossible to count accurately in that case as one document may be in more than one group)
     * @return stats 
     */
    public ResultsStats docsStats() {
        return docsStats;
    }
    
    public ResultsStats hitsStats() {
        return hitsStats;
    }

    /**
     * Compute total number of hits & documents in the sample
     * NOTE: docsStats might return -1 for totalDocsCounted if not all hits are stored/retrieved
     *  
     * @param sample a sample of the full results set
     * @param maxHitsStatsOfSource copied from source of sample. Since if the source hit the limits, then it follows that the sample is also limited
     * @param maxDocsStatsOfSource copied from source of sample. Since if the source hit the limits, then it follows that the sample is also limited
     * @return hitsStats in left, docsStats in right
     */
    private static Pair<ResultsStatsSaved, ResultsStatsSaved> getStatsOfSample(List<HitGroup> sample, MaxStats maxHitsStatsOfSource, MaxStats maxDocsStatsOfSource) {
        long hitsCounted = 0;
        long hitsRetrieved = 0;
        long docsRetrieved = 0;

        for (HitGroup h: sample) {
            hitsCounted += h.size();
            hitsRetrieved += h.resultsStats().processedTotal();
            docsRetrieved += h.docsStats().processedTotal();
        }
        boolean allHitsRetrieved = hitsRetrieved == hitsCounted;
        return Pair.of(new ResultsStatsSaved(hitsRetrieved, hitsCounted, maxHitsStatsOfSource), new ResultsStatsSaved(docsRetrieved, allHitsRetrieved ? docsRetrieved : -1, maxDocsStatsOfSource));
    }
}
