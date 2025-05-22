package nl.inl.blacklab.resultproperty;

import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.util.PropertySerializeUtil;

/**
 * A hit property for grouping on by decade based on a stored field in the
 * corresponding Lucene document containing a year.
 */
public class HitPropertyDocumentDecade extends HitProperty {

    public static final String ID = DocPropertyDecade.ID;

    /** The value we store when the decade is unknown */
    public static final int UNKNOWN_VALUE = 10_000_000;

    static HitPropertyDocumentDecade deserializeProp(BlackLabIndex index, String fieldName) {
        return new HitPropertyDocumentDecade(index, index.metadataField(fieldName));
    }

    private final BlackLabIndex index;

    final IndexReader reader;

    final String fieldName;

    private final DocPropertyDecade docPropertyDocumentDecade;

    HitPropertyDocumentDecade(HitPropertyDocumentDecade prop, Hits hits, boolean invert) {
        super(prop, hits, invert);
        this.index = prop.index;
        this.reader = index.reader();
        this.fieldName = prop.fieldName;
        this.docPropertyDocumentDecade = prop.docPropertyDocumentDecade;
    }

    public HitPropertyDocumentDecade(BlackLabIndex index, MetadataField field) {
        super();
        this.index = index;
        this.reader = index.reader();
        this.fieldName = field.name();
        this.docPropertyDocumentDecade = new DocPropertyDecade(index, fieldName);
    }

    @Override
    public Class<? extends PropertyValue> getValueType() {
        return docPropertyDocumentDecade.getValueType();
    }

    @Override
    public HitProperty copyWith(Hits newHits, boolean invert) {
        return new HitPropertyDocumentDecade(this, newHits, invert);
    }

    @Override
    public PropertyValueDecade get(long hitIndex) {
        return docPropertyDocumentDecade.get(hits.doc(hitIndex));
    }

    @Override
    public int compare(long indexA, long indexB) {
        return docPropertyDocumentDecade.compare(
            hits.doc(indexA),
            hits.doc(indexB)
        ) * (reverse ? -1 : 1);
    }

    @Override
    public String name() {
        return "document: " + docPropertyDocumentDecade.name();
    }

    @Override
    public String serialize() {
        return serializeReverse() + PropertySerializeUtil.combineParts(ID, fieldName);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((fieldName == null) ? 0 : fieldName.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        HitPropertyDocumentDecade other = (HitPropertyDocumentDecade) obj;
        if (fieldName == null) {
            if (other.fieldName != null)
                return false;
        } else if (!fieldName.equals(other.fieldName))
            return false;
        return true;
    }

    @Override
    public DocProperty docPropsOnly() {
        return reverse ? docPropertyDocumentDecade.reverse() : docPropertyDocumentDecade;
    }

    @Override
    public PropertyValue docPropValues(PropertyValue value) {
        return value;
    }

    @Override
    public boolean isDocPropOrHitText() {
        return true;
    }
}
