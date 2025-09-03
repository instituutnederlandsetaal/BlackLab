package nl.inl.blacklab.search.results.hits.fetch;

import java.util.Map;

import org.apache.lucene.index.LeafReaderContext;

import com.ibm.icu.text.CollationKey;

import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropContext;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.results.hits.Hits;

public class HitFilterPropertyValue implements HitFilter {
    private final HitProperty filterProp;
    private final PropertyValue filterValue;

    public HitFilterPropertyValue(HitProperty filterProp, PropertyValue filterValue) {
        this.filterProp = filterProp;
        this.filterValue = filterValue;
    }

    @Override
    public boolean test(long hitIndex) {
        return filterProp.get(hitIndex).equals(filterValue);
    }

    @Override
    public HitFilter forSegment(Hits hits, LeafReaderContext lrc, Map<String, CollationKey> collationCache) {
        HitProperty prop = filterProp.copyWith(PropContext.segmentHits(hits, lrc, collationCache));
        return new HitFilterPropertyValue(prop, filterValue);
    }
}
