package nl.inl.blacklab.resultproperty;

import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.search.results.hits.Hits;

/**
 * A hit property for sorting on hit token position. Usually to be combined with
 * sorting on document id, for a fast and reproducible sort.
 */
public class HitPropertyHitPosition extends HitProperty {

    public static final String ID = "hitposition";

    HitPropertyHitPosition(HitPropertyHitPosition prop, Hits hits, LeafReaderContext lrc, boolean toGlobal, boolean invert) {
        super(prop, hits, lrc, toGlobal, invert);
    }
    
    public HitPropertyHitPosition() {
        super();
    }

    @Override
    public HitProperty copyWith(Hits newHits, LeafReaderContext lrc, boolean toGlobal, boolean invert) {
        return new HitPropertyHitPosition(this, newHits, lrc, toGlobal, invert);
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
