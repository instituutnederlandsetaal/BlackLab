package nl.inl.blacklab.resultproperty;

import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.NonNull;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.SingleDocIdFilter;

/**
 * A hit property for grouping per document.
 */
public class HitPropertyDoc extends HitProperty {

    public static final String ID = "doc";

    private final BlackLabIndex index;

    HitPropertyDoc(HitPropertyDoc prop, PropContext context, boolean invert) {
        super(prop, context, invert);
        this.index = context.hits().index();
    }

    public HitPropertyDoc(BlackLabIndex index) {
        super();
        this.index = index;
    }

    @Override
    public boolean canRefineQuery() {
        return true;
    }

    @Override
    @NonNull protected RefiningQuery refineQuery(RefiningQuery original, PropertyValue val) {
        int luceneDocId = val.value() instanceof Integer ? ((int) val.value()) :
                original.index().getDocIdFromPid(val.value().toString());
        return original.withAddedFilter(new SingleDocIdFilter(luceneDocId));
    }

    @Override
    public HitProperty copyWith(PropContext context, boolean invert) {
        return new HitPropertyDoc(this, context, invert);
    }

    @Override
    public Class<? extends PropertyValue> getValueType() {
        return PropertyValueDoc.class;
    }

    @Override
    public PropertyValueDoc get(long hitIndex) {
        return new PropertyValueDoc(context.resultDocIdForHit(hitIndex));
    }

    @Override
    public String name() {
        return "document";
    }

    @Override
    public int compare(long indexA, long indexB) {
        // no need to add docBase here, because we're just comparing
        int docA = context.hits().doc(indexA);
        int docB = context.hits().doc(indexB);
        return reverse ? docB - docA : docA - docB;
    }

    @Override
    public String serialize() {
        return serializeReverse() + ID;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof HitPropertyDoc))
            return false;
        if (!super.equals(o))
            return false;
        HitPropertyDoc that = (HitPropertyDoc) o;
        return Objects.equals(index, that.index);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), index);
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
