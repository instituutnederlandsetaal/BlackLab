package nl.inl.blacklab.search.results.hits.fetch;

import org.apache.lucene.index.LeafReaderContext;

/**
 * Receives and collects (via HitProcessor) hits from multiple segments, keeping track of totals.
 *
 * Implementations must be thread-safe, as segments may be processed in parallel.
 */
public interface HitCollector {

    /**
     * Get hit processor for this segment.
     */
    HitCollectorSegment getHitProcessor(LeafReaderContext lrc);
}
