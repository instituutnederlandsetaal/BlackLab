package nl.inl.blacklab.search.results.hits.fetch;

import nl.inl.blacklab.search.results.hits.EphemeralHit;
import nl.inl.blacklab.search.results.hits.Hits;

/**
 * Fetches hits from a single segment's Hits object, reporting them to a HitProcessor.
 */
class HitFetcherSegmentHits extends HitFetcherSegmentAbstract {
    Hits hits;

    long hitIndex = -1;

    public HitFetcherSegmentHits(
            Hits hits,
            State state) {
        super(state);
        this.hits = hits;
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
            hits.getEphemeral(hitIndex, hit);
            return true;
        }
    }

    protected void runCleanup() {
        this.hits = null;
    }
}
