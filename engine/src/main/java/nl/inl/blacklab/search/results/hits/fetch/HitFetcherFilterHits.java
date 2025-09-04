package nl.inl.blacklab.search.results.hits.fetch;

import java.util.Map;

import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.search.results.hits.Hits;

/**
 * Fetches hits from a (lazy) Hits object, in parallel if possible.
 */
public class HitFetcherFilterHits extends HitFetcherAbstract {

    private final Hits source;

    public HitFetcherFilterHits(Hits source) {
        super(source.field(), null);
        this.source = source;
    }

    @Override
    public void fetchHits(HitFilter filter, HitCollector hitCollector) {
        super.fetchHits(filter, hitCollector);
        Map<LeafReaderContext, Hits> hitsPerSegment = source.hitsPerSegment();
        if (hitsPerSegment != null) {
            // Fetch per segment
            for (Map.Entry<LeafReaderContext, Hits> entry: hitsPerSegment.entrySet()) {
                // Hit processor: gathers the hits from this segment and (when there's enough) adds them
                // to the global view.
                LeafReaderContext lrc = entry.getKey();
                Hits segmentHits = entry.getValue();
                addFetchTask(hitCollector, lrc, segmentHits);
            }
        } else {
            // We don't have per-segment hits. Just iterate over the list in a single thread.
            addFetchTask(hitCollector, null, source);
        }
        if (segmentReaders.isEmpty()) {
            done = true;
            hitCollector.setDone();
        }
    }

    private void addFetchTask(HitCollector hitCollector, LeafReaderContext lrc, Hits segmentHits) {
        // Spans reader: fetch hits from segment and feed them to the hit processor.
        HitFetcherSegment.State state = new HitFetcherSegment.State(
                lrc,
                hitQueryContext,
                filter.forSegment(segmentHits, lrc, collationCache),
                hitCollector.getHitProcessor(lrc),
                requestedHitsToProcess,
                requestedHitsToCount,
                hitCollector.resultsStats(),
                hitCollector.docsStats(),
                collationCache);
        segmentReaders.add(new HitFetcherSegmentFilterHits(segmentHits, state));
    }
}
