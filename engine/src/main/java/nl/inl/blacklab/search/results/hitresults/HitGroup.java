package nl.inl.blacklab.search.results.hitresults;

import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.results.Group;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.hits.Hits;
import nl.inl.blacklab.search.results.stats.MaxStats;

/**
 * A group of results, with its group identity and the results themselves, that
 * you can access randomly (i.e. you can obtain a list of Hit objects)
 */
public class HitGroup extends Group {
    public static HitGroup empty(QueryInfo queryInfo, PropertyValue groupIdentity, long totalSize) {
        return new HitGroup(queryInfo, groupIdentity, totalSize);
    }

    public static HitGroup fromList(QueryInfo queryInfo, PropertyValue groupIdentity, Hits storedResults,
            long totalSize) {
        return new HitGroup(queryInfo, groupIdentity, storedResults, totalSize);
    }

    public static HitGroup withoutResults(QueryInfo queryInfo, PropertyValue groupIdentity,
            long totalHits, int totalDocuments, MaxStats maxStats) {
        return new HitGroup(groupIdentity,
                new HitResultsList(queryInfo, Hits.empty(queryInfo.field(), null),
                        totalHits, totalDocuments, maxStats), totalHits);
    }

    public static HitGroup fromHits(PropertyValue groupIdentity, HitResults storedResults, long totalSize) {
        return new HitGroup(groupIdentity, storedResults, totalSize);
    }

    protected HitGroup(QueryInfo queryInfo, PropertyValue groupIdentity, long totalSize) {
        this(groupIdentity, HitResults.empty(queryInfo), totalSize);
    }

    /**
     * Wraps a list of Hit objects with the HitGroup interface.
     *
     * NOTE: the list is not copied!
     *
     * @param queryInfo query info
     * @param storedResults the hits we actually stored
     * @param totalSize total group size
     */
    protected HitGroup(QueryInfo queryInfo, PropertyValue groupIdentity, Hits storedResults, long totalSize) {
        super(groupIdentity, HitResults.list(queryInfo, storedResults), totalSize);
    }

    /**
     * Wraps a list of Hit objects with the HitGroup interface.
     *
     * NOTE: the list is not copied!
     *
     * @param groupIdentity identity of the group
     * @param storedResults the hits
     * @param totalSize total group size
     */
    protected HitGroup(PropertyValue groupIdentity, HitResults storedResults, long totalSize) {
        super(groupIdentity, storedResults, totalSize);
    }

    @Override
    public HitResults storedResults() {
        return (HitResults)super.storedResults();
    }
}
