package nl.inl.blacklab.search.results.hits.fetch;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.lucene.index.LeafReaderContext;

import com.ibm.icu.text.CollationKey;

import nl.inl.blacklab.search.results.hits.Hits;

/**
 * Fetches hits from a (lazy) Hits object, in parallel if possible.
 */
public class HitFetcherFilterHits extends HitFetcherAbstract {

    private final Hits source;

    private final HitFilter filter;

    private final Map<String, CollationKey> collationCache;

    public HitFetcherFilterHits(Hits source, HitFilter filter) {
        super(source.field(), null);
        this.source = source;
        this.filter = filter;
        this.collationCache = new ConcurrentHashMap<>();
    }

    @Override
    public void fetchHits(HitCollector hitCollector) {
        this.hitCollector = hitCollector;
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
                hitCollector.getHitProcessor(lrc),
                requestedHitsToProcess,
                requestedHitsToCount,
                hitCollector.resultsStats(),
                hitCollector.docsStats());
        segmentReaders.add(new HitFetcherSegmentFilterHits(segmentHits, filter, collationCache, state));
    }
}
