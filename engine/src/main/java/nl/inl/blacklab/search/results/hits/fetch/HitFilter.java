package nl.inl.blacklab.search.results.hits.fetch;

import java.util.Map;

import org.apache.lucene.index.LeafReaderContext;

import com.ibm.icu.text.CollationKey;

import nl.inl.blacklab.search.results.hits.Hits;

/** A predicate we can use to filter hits */
public interface HitFilter {

    HitFilter ACCEPT_ALL = new HitFilter() {
        @Override
        public boolean accept(long hitIndex) {
            return true;
        }

        @Override
        public HitFilter forSegment(Hits hits, LeafReaderContext lrc, Map<String, CollationKey> collationCache) {
            return this;
        }
    };

    boolean accept(long hitIndex);

    HitFilter forSegment(Hits hits, LeafReaderContext lrc, Map<String, CollationKey> collationCache);

    default void disposeContext() {}
}
