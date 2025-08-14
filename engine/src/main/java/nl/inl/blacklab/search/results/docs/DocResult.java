package nl.inl.blacklab.search.results.docs;

import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.PropertyValueDoc;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.hitresults.HitGroup;
import nl.inl.blacklab.search.results.hitresults.HitResults;

/**
 * A document result, containing a Lucene document from the index and a
 * collection of Hit objects.
 */
public class DocResult extends HitGroup {
    
    public static DocResult fromDoc(QueryInfo queryInfo, PropertyValueDoc doc, float score, long totalNumberOfHits) {
        return new DocResult(queryInfo, doc, score, totalNumberOfHits);
    }
    
    public static DocResult fromHits(PropertyValueDoc doc, HitResults storedHits, long totalNumberOfHits) {
        return new DocResult(doc, storedHits, totalNumberOfHits);
    }
    
    private final float score;

    protected DocResult(QueryInfo queryInfo, PropertyValueDoc doc, float score, long numberOfHits) {
        super(queryInfo, doc, numberOfHits);
        this.score = score;
    }

    /**
     * Construct a DocResult.
     *
     * @param doc the Lucene document id
     * @param storedHits hits in the document stored in this result
     * @param totalNumberOfHits total number of hits in this document
     */
    protected DocResult(PropertyValue doc, HitResults storedHits, long totalNumberOfHits) {
        super(doc, storedHits, totalNumberOfHits);
        this.score = 0.0f;
    }

    public float score() {
        return score;
    }
    
    @Override
    public PropertyValueDoc identity() {
        return (PropertyValueDoc)super.identity();
    }

    public int docId() {
        return identity().value();
    }
    
}
