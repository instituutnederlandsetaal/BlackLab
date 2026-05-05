package nl.inl.blacklab.resultproperty;

import java.util.List;
import java.util.Optional;

import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.BytesRef;
import org.jspecify.annotations.NonNull;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.FieldType;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.util.PropertySerializeUtil;

/**
 * A hit property for grouping on a stored field in the corresponding Lucene
 * document.
 */
public class HitPropertyDocumentStoredField extends HitProperty {

    public static final String ID = DocPropertyStoredField.ID;

    final String fieldName;

    private final DocPropertyStoredField docPropStoredField;

    HitPropertyDocumentStoredField(HitPropertyDocumentStoredField prop, PropContext context, boolean invert) {
        super(prop, context, invert);
        this.fieldName = prop.fieldName;
        this.docPropStoredField = prop.docPropStoredField.copyWith(context, false);
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
    public boolean canRefineQuery() {
        return true;
    }

    @Override
    @NonNull protected RefiningQuery refineQuery(RefiningQuery original, PropertyValue val) {
        MetadataField metadataField = original.index().metadataField(fieldName());
        return original.withAddedFilter(termQuery(metadataField, val.value().toString()));
    }

    @Override
    public HitProperty copyWith(PropContext context, boolean invert) {
        return new HitPropertyDocumentStoredField(this, context, invert);
    }

    @Override
    public Class<? extends PropertyValue> getValueType() {
        return PropertyValueString.class;
    }

    @Override
    public PropertyValueString get(long hitIndex) {
        // NOTE: DocPropertyStoredField will convert the doc id to global
        return new PropertyValueString(getString(hitIndex), context.collationCache());
    }

    @Override
    public String getString(long hitIndex) {
        String[] v = docPropStoredField.get(context.hits().doc(hitIndex));
        if (v == null || v.length == 0)
            return PropertyValue.NO_VALUE_STR;
        if (v.length == 1)
            return v[0];
        return PropertyValueString.joinValues(v);
    }

    @Override
    public int compare(long a, long b) {
        final int docA = context.hits().doc(a);
        final int docB = context.hits().doc(b);
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

    public Query termQuery(MetadataField metadataField, String value) {
        if (metadataField.type() == FieldType.NUMERIC) {
            return IntPoint.newSetQuery(fieldName, List.of(Integer.parseInt(value)));
        } else {
            // https://github.com/apache/lucene/commit/0bc41356955cbf0144aa37203c6269256cf62555#diff-8d710e550a9661ad8a40b284a1f2ddc26a3b58477bf55d52eeed3f2f0576385cL169
            return SortedDocValuesField.newSlowSetQuery(fieldName, List.of(new BytesRef(value)));
            // TermQuery doesn't work here! Why!?
            // (tested with field authorCombined in CHN;
            //  if that field is not indexed but does have docvalues,
            //  that would explain it, but no field in BlackLab should
            //  have that - all are indexed and have docvalues?)
            //return new TermQuery(new Term(fieldName, value));
        }
    }
}
