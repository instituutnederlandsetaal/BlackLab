package nl.inl.blacklab.resultproperty;

/**
 * A hit property for grouping per document id.
 * 
 * NOTE: prefer using HitPropertyDoc, which includes the actual 
 * Doc instance. 
 */
public class HitPropertyDocumentId extends HitProperty {

    public static final String ID = "docid";

    HitPropertyDocumentId(HitPropertyDocumentId prop, PropContext context, boolean invert) {
        super(prop, context, invert);
    }

    public HitPropertyDocumentId() {
        super();
    }

    @Override
    public Class<? extends PropertyValue> getValueType() {
        return PropertyValueInt.class;
    }

    @Override
    public HitProperty copyWith(PropContext context, boolean invert) {
        return new HitPropertyDocumentId(this, context, invert);
    }

    @Override
    public PropertyValueInt get(long hitIndex) {
        return new PropertyValueInt(context.resultDocIdForHit(hitIndex));
    }

    @Override
    public String name() {
        return "document: id";
    }

    @Override
    public int compare(long indexA, long indexB) {
        // no need to add docBase here, we're just comparing
        final int docA = context.hits().doc(indexA);
        final int docB = context.hits().doc(indexB);
        return reverse ? docB - docA : docA - docB;
    }

    @Override
    public String serialize() {
        return serializeReverse() + ID;
    }

    @Override
    public DocProperty docPropsOnly() {
        DocPropertyId result = new DocPropertyId();
        return reverse ? result.reverse() : result;
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
