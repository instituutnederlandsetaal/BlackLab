package nl.inl.blacklab.search.results.docs;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.ResultProperty;
import nl.inl.blacklab.search.results.HitOrDocGroup;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.stats.ResultsStats;

/**
 * A group of DocResult objects, plus the "group identity". For example, if
 * you're grouping on author name, the group identity might be the string "Harry
 * Mulisch".
 */
public class DocGroup implements HitOrDocGroup {
    
    public static DocGroup fromList(QueryInfo queryInfo, PropertyValue groupIdentity, List<DocResult> storedResults, long totalDocuments, long totalTokens) {
        return new DocGroup(queryInfo, groupIdentity, storedResults, totalDocuments, totalTokens);
    }

    protected final PropertyValue groupIdentity;

    private final DocResults storedResults;

    private final ResultsStats resultStats;

    private final long totalSize;

    private final long numberOfStoredResults;

    private final long totalTokens;

    private long storedHits;

    protected DocGroup(QueryInfo queryInfo, PropertyValue groupIdentity, List<DocResult> storedResults, long totalDocuments, long totalTokens) {
        this.groupIdentity = groupIdentity;
        this.storedResults = DocResults.fromList(queryInfo, storedResults, null, null);
        resultStats = this.storedResults.resultsStats();
        numberOfStoredResults = this.storedResults.size();
        this.totalSize = totalDocuments;

        this.totalTokens = totalTokens;
        storedHits = 0;
        for (DocResult result: storedResults) {
            storedHits += result.numberOfStoredResults();
        }
    }

    @Override
    public DocResults storedResults() {
        return storedResults;
    }

    public long numberOfStoredHits() {
        return storedHits;
    }

    public long totalTokens() {
        return totalTokens;
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
        return numberOfStoredResults;
    }

    public long size() {
        return totalSize;
    }

    public int compareTo(HitOrDocGroup o) {
        return identity().compareTo(o.identity());
    }

    public ResultsStats resultsStats() {
        return resultStats;
    }

    @Override
    public String toString() {
        return "DocGroup(id=" + identity() + ", size=" + size() + ")";
    }

}
