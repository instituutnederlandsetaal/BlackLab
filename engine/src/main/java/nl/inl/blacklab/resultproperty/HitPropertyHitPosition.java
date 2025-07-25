package nl.inl.blacklab.resultproperty;

import nl.inl.blacklab.search.results.HitsForHitProps;

/**
 * A hit property for sorting on hit token position. Usually to be combined with
 * sorting on document id, for a fast and reproducible sort.
 */
public class HitPropertyHitPosition extends HitProperty {

    public static final String ID = "hitposition";

    HitPropertyHitPosition(HitPropertyHitPosition prop, HitsForHitProps hits, boolean invert) {
        super(prop, hits, invert);
    }
    
    public HitPropertyHitPosition() {
        super();
    }

    @Override
    public HitProperty copyWith(HitsForHitProps newHits, boolean invert) {
        return new HitPropertyHitPosition(this, newHits, invert);
    }

    @Override
    public Class<? extends PropertyValue> getValueType() {
        return PropertyValueInt.class;
    }

    @Override
    public PropertyValueInt get(long hitIndex) {
        return new PropertyValueInt(hits.start(hitIndex));
    }

    @Override
    public String name() {
        return "hit: position";
    }

    @Override
    public int compare(long indexA, long indexB) {
        int startA = hits.start(indexA);
        int startB = hits.start(indexB);
        int endA = hits.end(indexA);
        int endB = hits.end(indexB);
        
        if (startA == startB)
            return reverse ? endB - endA : endA - endB;
        return reverse ? startB - startA : startA - startB;
    }

    @Override
    public String serialize() {
        return serializeReverse() + ID;
    }
    
    @Override
    public boolean isDocPropOrHitText() {
        return false;
    }
}
