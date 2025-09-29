package nl.inl.blacklab.resultproperty;

import java.util.List;

import org.apache.lucene.document.IntPoint;
import org.apache.lucene.search.DocValuesTermsQuery;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.FieldType;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.util.PropertySerializeUtil;

/**
 * A hit property for grouping on a stored field in the corresponding Lucene
 * document.
 */
public class HitPropertyDocumentStoredField extends HitProperty {

    public static final String ID = DocPropertyStoredField.ID;

    final String fieldName;

    private final DocPropertyStoredField docPropStoredField;

    HitPropertyDocumentStoredField(HitPropertyDocumentStoredField prop, Hits hits, boolean invert) {
        super(prop, hits, invert);
        this.fieldName = prop.fieldName;
        this.docPropStoredField = prop.docPropStoredField;
        assert docPropStoredField != null;
    }

    public HitPropertyDocumentStoredField(BlackLabIndex index, String fieldName, String friendlyName) {
        super();
        this.fieldName = fieldName;
        this.docPropStoredField = new DocPropertyStoredField(index, fieldName, friendlyName);
    }

    public HitPropertyDocumentStoredField(BlackLabIndex index, String fieldName) {
        this(index, fieldName, fieldName);
    }

    @Override
    public HitProperty copyWith(Hits newHits, boolean invert) {
        return new HitPropertyDocumentStoredField(this, newHits, invert);
    }

    @Override
    public Class<? extends PropertyValue> getValueType() {
        return PropertyValueString.class;
    }

    @Override
    public PropertyValueString get(long result) {
        return DocPropertyStoredField.fromArray(docPropStoredField.get(hits.doc(result)));
    }

    @Override
    public int compare(long a, long b) {
        final int docA = hits.doc(a);
        final int docB = hits.doc(b);
        return reverse ?
                docPropStoredField.compare(docB, docA) :
                docPropStoredField.compare(docA, docB);
    }

    @Override
    public String name() {
        return "document: " + docPropStoredField.name();
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
        HitPropertyDocumentStoredField other = (HitPropertyDocumentStoredField) obj;
        if (fieldName == null) {
            if (other.fieldName != null)
                return false;
        } else if (!fieldName.equals(other.fieldName))
            return false;
        return true;
    }

    @Override
    public DocProperty docPropsOnly() {
        return reverse ? docPropStoredField.reverse() : docPropStoredField;
    }

    @Override
    public PropertyValue docPropValues(PropertyValue value) {
        return value;
    }
    
    @Override
    public boolean isDocPropOrHitText() {
        return true;
    }
    
    public String fieldName() {
        return fieldName;
    }

    public Query termQuery(BlackLabIndex index, String value) {
        MetadataField metadataField = index.metadataField(fieldName);
        if (metadataField.type() == FieldType.NUMERIC) {
            return IntPoint.newSetQuery(fieldName, List.of(Integer.parseInt(value)));
        } else {
            return new DocValuesTermsQuery(fieldName, value);
            // TermQuery doesn't work here! Why!?
            // (tested with field authorCombined in CHN;
            //  if that field is not indexed but does have docvalues,
            //  that would explain it, but no field in BlackLab should
            //  have that - all are indexed and have docvalues?)
            //return new TermQuery(new Term(fieldName, value));
        }
    }
}
