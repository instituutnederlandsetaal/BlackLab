package nl.inl.blacklab.search.results.hits.fetch;

import java.util.Map;

import org.apache.lucene.index.LeafReaderContext;

import com.ibm.icu.text.CollationKey;

import nl.inl.blacklab.search.results.hits.Hits;

/** A predicate we can use to filter hits */
public interface HitFilter {

    /** Should we accept or reject this hit?
     *
     * @param hitIndex index of the hit
     * @return true to accept, false to reject
     */
    boolean accept(long hitIndex);

    /**
     * Customize a HitFilter for a specific segment.
     *
     * @param hits (segment) hits object to use
     * @param lrc the segment, or null if these are global hits
     * @param collationCache cache for collation keys
     * @return a HitFilter that can be used for this segment
     */
    HitFilter forSegment(Hits hits, LeafReaderContext lrc, Map<String, CollationKey> collationCache);

}
