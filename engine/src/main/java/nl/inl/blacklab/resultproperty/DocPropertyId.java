package nl.inl.blacklab.resultproperty;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.results.docs.DocResult;

/**
 * For grouping DocResult objects by decade based on a stored field containing a
 * year.
 */
public class DocPropertyId extends DocProperty {

    DocPropertyId(DocPropertyId prop, LeafReaderContext lrc, boolean invert) {
        super(prop, lrc, invert);
    }

    public DocPropertyId() {
    }

    @Override
    public Class<? extends PropertyValue> getValueType() {
        return PropertyValueInt.class;
    }

    @Override
    public PropertyValueInt get(DocResult result) {
        return new PropertyValueInt(result.identity().value() + (lrc == null ? 0 : lrc.docBase));
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
        int idA = a.identity().value();
        int idB = b.identity().value();
        return reverse ? idB - idA : idA - idB;
    }

    @Override
    public String name() {
        return "id";
    }

    public static DocPropertyId deserialize() {
        return new DocPropertyId();
    }

    @Override
    public String serialize() {
        return serializeReverse() + "id";
    }

    @Override
    public DocPropertyId copyWith(LeafReaderContext lrc, boolean invert) {
        return new DocPropertyId(this, lrc, invert);
    }

    @Override
    public Query query(BlackLabIndex index, PropertyValue value) {
        return null;
    }

}
