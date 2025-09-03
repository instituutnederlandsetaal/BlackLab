package nl.inl.blacklab.search.results.hits.fetch;

import java.util.Map;

import com.ibm.icu.text.CollationKey;

import nl.inl.blacklab.search.results.hits.EphemeralHit;
import nl.inl.blacklab.search.results.hits.Hits;

/**
 * Fetches hits from a single segment's Hits object, reporting them to a HitProcessor.
 */
class HitFetcherSegmentFilterHits extends HitFetcherSegmentAbstract {
    Hits hits;

    private final HitFilter filter;

    long hitIndex = -1;

    public HitFetcherSegmentFilterHits(
            Hits hits,
            HitFilter hitFilter,
            Map<String, CollationKey> collationCache,
            State state) {
        super(state);
        this.hits = hits;
        this.filter = hitFilter.forSegment(hits, state.lrc, collationCache);
    }

    @Override
    public void initialize() {
        // nothing to do
    }

    protected void runPrepare() {
        // (we already know we're at a document boundary here)
        prevDoc = -1;
    }

    protected boolean runGetHit(EphemeralHit hit) {
        while (true) {
            hitIndex++;
            if (!hits.sizeAtLeast(hitIndex + 1)) {
                // No more hits, we're done
                return false;
            }
            if (filter != null && filter.test(hitIndex)) {
                hits.getEphemeral(hitIndex, hit);
                return true;
            }
        }
    }

    protected void runCleanup() {
        this.hits = null;
    }
}
