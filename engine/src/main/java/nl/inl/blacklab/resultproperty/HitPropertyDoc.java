package nl.inl.blacklab.resultproperty;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.results.Hits;

/**
 * A hit property for grouping per document.
 */
public class HitPropertyDoc extends HitProperty {

    public static final String ID = "doc";

    private final BlackLabIndex index;

    HitPropertyDoc(HitPropertyDoc prop, Hits hits, boolean invert) {
        super(prop, hits, invert);
        this.index = hits.queryInfo().index();
    }

    public HitPropertyDoc(BlackLabIndex index) {
        super();
        this.index = index;
    }

    @Override
    public HitProperty copyWith(Hits newHits, boolean invert) {
        return new HitPropertyDoc(this, newHits, invert);
    }

    @Override
    public Class<? extends PropertyValue> getValueType() {
        return PropertyValueDoc.class;
    }

    @Override
    public PropertyValueDoc get(long hitIndex) {
        return new PropertyValueDoc(index, hits.doc(hitIndex));
    }

    @Override
    public String name() {
        return "document";
    }

    @Override
    public int compare(long indexA, long indexB) {
        int docA = hits.doc(indexA);
        int docB = hits.doc(indexB);
        return reverse ? docB - docA : docA - docB;
    }

    @Override
    public String serialize() {
        return serializeReverse() + ID;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((index == null) ? 0 : index.hashCode());
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
        HitPropertyDoc other = (HitPropertyDoc) obj;
        if (index == null) {
            if (other.index != null)
                return false;
        } else if (!index.equals(other.index))
            return false;
        return true;
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
