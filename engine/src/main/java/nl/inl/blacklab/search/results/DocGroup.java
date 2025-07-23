package nl.inl.blacklab.search.results;

import java.util.List;

import nl.inl.blacklab.resultproperty.PropertyValue;

/**
 * A group of DocResult objects, plus the "group identity". For example, if
 * you're grouping on author name, the group identity might be the string "Harry
 * Mulisch".
 */
public class DocGroup extends Group {
    
    public static DocGroup fromList(QueryInfo queryInfo, PropertyValue groupIdentity, List<DocResult> storedResults, long totalDocuments, long totalTokens) {
        return new DocGroup(queryInfo, groupIdentity, storedResults, totalDocuments, totalTokens);
    }

    private final long totalTokens;

    private int storedHits;

    protected DocGroup(QueryInfo queryInfo, PropertyValue groupIdentity, List<DocResult> storedResults, long totalDocuments, long totalTokens) {
        super(groupIdentity, DocResults.fromList(queryInfo, storedResults, null, null), totalDocuments);
        this.totalTokens = totalTokens;
        storedHits = 0;
        for (DocResult result: storedResults) {
            storedHits += result.numberOfStoredResults();
        }
    }

    @Override
    public DocResults storedResults() {
        return (DocResults) super.storedResults();
    }

    public long numberOfStoredHits() {
        return storedHits;
    }

    public long totalTokens() {
        return totalTokens;
    }

}
