package nl.inl.blacklab.search.results;

import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;

/**
 * A group of results, with its group identity and the results themselves, that
 * you can access randomly (i.e. you can obtain a list of Hit objects)
 */
public class HitGroup extends Group<Hit> {
    public static HitGroup empty(QueryInfo queryInfo, PropertyValue groupIdentity, long totalSize) {
        return new HitGroup(queryInfo, groupIdentity, totalSize);
    }

    public static HitGroup fromList(QueryInfo queryInfo, PropertyValue groupIdentity, HitsInternal storedResults,
            MatchInfoDefs matchInfoDefs, long totalSize) {
        return new HitGroup(queryInfo, groupIdentity, storedResults, matchInfoDefs, totalSize);
    }

    public static HitGroup fromHits(PropertyValue groupIdentity, Hits storedResults, long totalSize) {
        return new HitGroup(groupIdentity, storedResults, totalSize);
    }

    protected HitGroup(QueryInfo queryInfo, PropertyValue groupIdentity, long totalSize) {
        this(groupIdentity, Hits.empty(queryInfo), totalSize);
    }

    /**
     * Wraps a list of Hit objects with the HitGroup interface.
     *
     * NOTE: the list is not copied!
     *
     * @param queryInfo query info
     * @param storedResults the hits we actually stored
     * @param matchInfoDefs captured groups for hits in this group
     * @param totalSize total group size
     */
    protected HitGroup(QueryInfo queryInfo, PropertyValue groupIdentity, HitsInternal storedResults,
            MatchInfoDefs matchInfoDefs, long totalSize) {
        super(groupIdentity, Hits.list(queryInfo, storedResults, matchInfoDefs), totalSize);
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
    protected HitGroup(PropertyValue groupIdentity, Hits storedResults, long totalSize) {
        super(groupIdentity, storedResults, totalSize);
    }
    
    @Override
    public Hits storedResults() {
        return (Hits)super.storedResults();
    }
}
