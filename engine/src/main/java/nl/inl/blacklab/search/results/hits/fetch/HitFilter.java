package nl.inl.blacklab.search.results.hits.fetch;

import java.util.Map;

import org.apache.lucene.index.LeafReaderContext;

import com.ibm.icu.text.CollationKey;

import nl.inl.blacklab.search.results.hits.Hits;

public interface HitFilter {
    boolean test(long hitIndex);

    HitFilter forSegment(Hits hits, LeafReaderContext lrc, Map<String, CollationKey> collationCache);
}
