package nl.inl.blacklab.search.results.hitresults;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.ResultProperty;
import nl.inl.blacklab.search.results.HitOrDocGroup;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.hits.Group;
import nl.inl.blacklab.search.results.hits.Hits;
import nl.inl.blacklab.search.results.stats.MaxStats;
import nl.inl.blacklab.search.results.stats.ResultsStats;
import nl.inl.blacklab.search.results.stats.ResultsStatsSaved;
import nl.inl.blacklab.search.textpattern.CompleteQuery;

/**
 * A group of results, with its group identity and the results themselves, that
 * you can access randomly (i.e. you can obtain a list of Hit objects)
 */
public class HitGroup implements HitOrDocGroup {

    /** Below this number of hits, just store them in the group. Above, if we have a query to determine them,
     *  don't store them and reconstruct them only if needed. */
    public static final long THRESHOLD_STORE_HITS = 100_000;

    /** The grouping value, which all results in the group have in common.
     *  (i.e. if you group by hit text, a group's identity is the hit text these hits all have) */
    protected final PropertyValue groupIdentity;

    /** Stats about number of hits processed/counted */
    private ResultsStats hitsStats;

    /** Stats about number of docs processed/counted, or null if not yet known (i.e. need hits first) */
    private ResultsStats docsStats;

    /** Results in this group */
    private HitResults storedResults;

    /** Total size of the group */
    private final long totalHits;

    /** The query to find our hits, if we don't know them yet */
    private final CompleteQuery hitsInGroupQuery;

    /** Do we need to fetch hits if storedResults() is called? */
    private boolean needToFetchHits;

    /** This group's score, as calculated by some scorer function. */
    private final Double score;

    public static List<HitGroup> listFromBasicGroups(QueryInfo queryInfo, Map<PropertyValue, Group> groupings,
            CompleteQuery query, HitProperty groupedBy, boolean storeResults, HitGroupScorer scorer) {
        List<HitGroup> groups = new ArrayList<>(groupings.size());
        for (Map.Entry<PropertyValue, Group> e : groupings.entrySet()) {
            PropertyValue groupId = e.getKey();
            Group grouped = e.getValue();
            HitGroup group;
            CompleteQuery hitsInGroupQuery = grouped.getHitsInGroupQuery();
            if (hitsInGroupQuery == null && query != null && groupedBy != null) {
                hitsInGroupQuery = groupedBy.refine(queryInfo.index(), query, groupId).orElseThrow();
            }
            if (storeResults || hitsInGroupQuery == null) {
                // Store results
                group = new HitGroup(groupId, HitResults.list(queryInfo, grouped.getStoredHits()),
                        grouped.getTotalNumberOfHits(), -1, hitsInGroupQuery, scorer);
            } else {
                // Don't store results.
                group = withoutResults(queryInfo, groupId, grouped.getTotalNumberOfHits(),
                        grouped.getTotalNumberOfDocs(), MaxStats.NOT_EXCEEDED, hitsInGroupQuery,
                        scorer);
            }
            groups.add(group);
        }
        return groups;
    }

    public static HitGroup withoutResults(QueryInfo queryInfo, PropertyValue groupIdentity,
            long totalSize, int totalDocuments, MaxStats maxStats, CompleteQuery hitsInGroupQuery,
            HitGroupScorer scorer) {
        HitResultsList results = new HitResultsList(queryInfo,
                Hits.empty(new Hits.HitsContext(queryInfo.field())), 0,
                totalSize, totalDocuments, maxStats);
        return new HitGroup(groupIdentity, results, totalSize, -1, hitsInGroupQuery, scorer);
    }

    /**
     * Wraps a list of Hit objects with the HitGroup interface.
     * NOTE: the list is not copied!
     *
     * @param groupIdentity identity of the group
     * @param storedResults the hits
     * @param totalHits     total group size
     * @param scorer
     */
    protected HitGroup(PropertyValue groupIdentity, HitResults storedResults, long totalHits, int totalDocs, CompleteQuery hitsInGroupQuery,
            HitGroupScorer scorer) {
        this.groupIdentity = groupIdentity;
        this.totalHits = totalHits;
        assert storedResults != null;
        if (storedResults.size() > 0) {
            hitsStats = storedResults.resultsStats().save();
            docsStats = storedResults.docsStats().save();
        } else {
            hitsStats = new ResultsStatsSaved(totalHits);
            docsStats = totalDocs < 0 ? null : new ResultsStatsSaved(totalDocs);
        }
        if (storedResults.size() > THRESHOLD_STORE_HITS && hitsInGroupQuery != null) {
            // Very large group of hits, and we can reconstruct them later if needed using the query.
            this.storedResults = HitResults.empty(storedResults.queryInfo());
            this.hitsInGroupQuery = hitsInGroupQuery;
            needToFetchHits = true;
        } else {
            // Limited number of hits; keep them in memory.
            this.storedResults = storedResults;
            this.hitsInGroupQuery = hitsInGroupQuery;
            needToFetchHits = storedResults.size() == 0 && hitsInGroupQuery != null;
        }

        score = scorer == HitGroupScorer.NONE ? null : scorer.score(this.groupIdentity, this.totalHits);

        // We should either know our hits, or have a query to find them later
        assert hitsInGroupQuery == null || storedResults.size() == 0;
    }

    @Override
    public synchronized HitResults storedResults() {
        if (needToFetchHits) {
            needToFetchHits = false;
            storedResults = storedResults.queryInfo().index().find(storedResults.queryInfo().field(), hitsInGroupQuery);
            hitsStats = storedResults.resultsStats();
            docsStats = storedResults.docsStats();
        }
        return storedResults;
    }

    public synchronized ResultsStats resultsStats() {
        return hitsStats;
    }
    public synchronized ResultsStats docsStats() {
        if (docsStats == null)
            docsStats = storedResults().docsStats();
        return docsStats;
    }

    public Map<ResultProperty, PropertyValue> getGroupProperties(List<? extends ResultProperty> criteria) {
        List<PropertyValue> valuesForGroup = identity().valuesList();
        Map<ResultProperty, PropertyValue> properties = new LinkedHashMap<>(criteria.size());
        for (int j = 0; j < criteria.size(); ++j) {
            properties.put(criteria.get(j), valuesForGroup.get(j));
        }
        return properties;
    }

    public PropertyValue identity() {
        return groupIdentity;
    }

    public long numberOfStoredResults() {
        return storedResults.size();
    }

    public long size() {
        return totalHits;
    }

    public Double score() {
        return score;
    }

    public int compareTo(HitOrDocGroup o) {
        return identity().compareTo(o.identity());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(id=" + identity() + ", size=" + size() + ")";
    }
}
