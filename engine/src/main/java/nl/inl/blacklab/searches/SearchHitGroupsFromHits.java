package nl.inl.blacklab.searches;

import java.util.Objects;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.HitPropertyContextPart;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.hitresults.HitGroupScorer;
import nl.inl.blacklab.search.results.hitresults.HitGroups;
import nl.inl.blacklab.search.results.hitresults.HitGroupsTokenFrequencies;

/**
 * A search operation that yields groups of hits.
 */
public class SearchHitGroupsFromHits extends SearchHitGroups {

    private final SearchHits source;

    private final HitProperty property;

    private final HitGroupScorer scorer;

    private final long maxResultsToStorePerGroup;

    private final boolean mustStoreHits;

    /**
     * A hit-grouping search.
     *
     * NOTE: When using the fast path, backing hits are not stored in the groups.
     * This saves a large amount of memory and time, but transforms the query into more of a statistical view on the data
     * because the individual hits are lost. If this is a problem, set mustStoreHits to true.
     *
     * @param queryInfo query info
     * @param hitsSearch search to group hits from
     * @param groupBy what to group by
     * @param maxResultsToStorePerGroup maximum number of results to store (if any are stored)
     * @param mustStoreHits if true, up to maxResultsToStorePerGroup hits will be stored. If false, no hits may be
     *                      stored, depending on how the grouping is performed.
     */
    public SearchHitGroupsFromHits(QueryInfo queryInfo, SearchHits hitsSearch, HitProperty groupBy,
            long maxResultsToStorePerGroup, boolean mustStoreHits, HitGroupScorer scorer) {
        super(queryInfo);
        this.source = hitsSearch;
        this.property = groupBy;
        this.maxResultsToStorePerGroup = maxResultsToStorePerGroup;
        this.mustStoreHits = mustStoreHits;
        this.scorer = scorer;
    }

    /**
     * Execute the search operation, returning the final response.
     *
     * @return result of the operation
     * @throws InvalidQuery if the query is invalid
     */
    @Override
    public HitGroups executeInternal(ActiveSearch<HitGroups> activeSearch) throws InvalidQuery {
        HitProperty prop = property;
        if (prop instanceof HitPropertyContextPart hpcp) {
            // fast path expects HitText. ContextPart can mean the same as HitText; if so, transform to HitText.
            if (hpcp.isHitText())
                prop = hpcp.asHitText();
        }
        if (HitGroupsTokenFrequencies.canUse(mustStoreHits, source, prop)) {
            // Any token query, group by hit text or doc metadata! Choose faster path that just "looks up"
            // token frequencies in the forward index(es).
            return HitGroupsTokenFrequencies.get(source, prop);
        } else {
            // Do we need to store the hits per group, or can we find them later using a group-specific query?
            boolean storeHits = !mustStoreHits && prop.canRefineQuery() || source.getCombinedSpanFilterQuery() == null;
            boolean hitsInCache = queryInfo().index().cache().containsKey(source);
            if (storeHits || hitsInCache) {
                // We need to store the hits, or the hits are already cached.
                // Just find all the hits and group them.
                return executeChildSearch(activeSearch, source).group(property, maxResultsToStorePerGroup, scorer);
            } else {
                // We don't need to store the hits. Group directly from the query and only keep the stats and the
                // queries needed to get hits in each group.
                // Calculate the grouping results by iterating over the hits without storing them.
                return HitGroups.withoutStoredHits(source, prop, scorer);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        SearchHitGroupsFromHits that = (SearchHitGroupsFromHits) o;
        return maxResultsToStorePerGroup == that.maxResultsToStorePerGroup && mustStoreHits == that.mustStoreHits && source.equals(that.source) && property.equals(that.property);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), source, property, maxResultsToStorePerGroup, mustStoreHits);
    }

    @Override
    public String toString() {
        return toString("group", source, property, maxResultsToStorePerGroup);
    }
}
