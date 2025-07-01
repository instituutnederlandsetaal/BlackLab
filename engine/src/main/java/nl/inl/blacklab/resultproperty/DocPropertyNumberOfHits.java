package nl.inl.blacklab.resultproperty;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.results.DocResult;

/**
 * For grouping DocResult objects on the number of hits. This would put
 * documents with 1 hit in a group, documents with 2 hits in another group, etc.
 */
public class DocPropertyNumberOfHits extends DocProperty {

    public static final String ID = "numhits";

    DocPropertyNumberOfHits(DocPropertyNumberOfHits prop, boolean invert) {
        super(prop, invert);
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
     * @return 0 if equal, negative if a &lt; b, positive if a &gt; b.
     */
    @Override
    public int compare(DocResult a, DocResult b) {
        int cmpResult = Long.compare(a.size(), b.size());
        return reverse ? -cmpResult : cmpResult;
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
    public DocProperty reverse() {
        return new DocPropertyNumberOfHits(this, true);
    }

    @Override
    public Query query(BlackLabIndex index, PropertyValue value) {
        return null;
    }

}
