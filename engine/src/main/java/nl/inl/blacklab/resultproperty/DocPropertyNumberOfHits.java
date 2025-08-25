package nl.inl.blacklab.resultproperty;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.results.docs.DocResult;

/**
 * For grouping DocResult objects on the number of hits. This would put
 * documents with 1 hit in a group, documents with 2 hits in another group, etc.
 */
public class DocPropertyNumberOfHits extends DocProperty {

    public static final String ID = "numhits";

    DocPropertyNumberOfHits(DocPropertyNumberOfHits prop, PropContext context, boolean invert) {
        super(prop, context, invert);
    }
    
    public DocPropertyNumberOfHits() {
        // NOP
    }

    @Override
    public Class<? extends PropertyValue> getValueType() {
        return PropertyValueInt.class;
    }

    @Override
    protected boolean sortDescendingByDefault() {
        return true;
    }

    @Override
    public PropertyValueInt get(DocResult result) {
        return new PropertyValueInt(result.size());
    }

    /**
     * Compares two docs on this property
     * 
     * @param a first doc
     * @param b second doc
     * @return 0 if equal, negative if a < b, positive if a > b.
     */
    @Override
    public int compare(DocResult a, DocResult b) {
        return reverse ?
                Long.compare(b.size(), a.size()) :
                Long.compare(a.size(), b.size());
    }

    @Override
    public String name() {
        return "number of hits";
    }

    @Override
    public String serialize() {
        return serializeReverse() + ID;
    }

    @Override
    public DocPropertyNumberOfHits copyWith(PropContext context, boolean invert) {
        return new DocPropertyNumberOfHits(this, context, invert);
    }

    @Override
    public Query query(BlackLabIndex index, PropertyValue value) {
        return null;
    }

}
