package nl.inl.blacklab.search.results.hits.fetch;

import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.search.lucene.BLSpanWeight;
import nl.inl.blacklab.search.results.hits.EphemeralHit;

/** 
 * Helper class for use with {@link HitFetcherQuery}.
 * <p>
 * HitFetcherQuery constructs one HitFetcherQuerySegment instance per segment ({@link LeafReaderContext}) of
 * the index. The HitFetcherQuerySegment will then produce results for the segment, periodically merging them back to
 * the global resultset passed in.
 */
public class HitFetcherSegmentQuery extends HitFetcherSegmentAbstract {

    HitsSpans hitsSpans;

    long hitIndex = -1;

    HitFetcherSegmentQuery(
        BLSpanWeight weight,
        State state) {
        super(state);
        this.hitsSpans = new HitsSpans(weight, state.lrc, state.hitQueryContext);
    }

    protected void runPrepare() {
    }

    protected boolean runGetHit(EphemeralHit hit) {
        hitIndex++;
        if (hitsSpans.sizeAtLeast(hitIndex + 1)) {
            // We have already fetched this hit
            hitsSpans.getEphemeral(hitIndex, hit);
            return true;
        }
        return false;
    }

    protected void runCleanup() {
        this.hitsSpans = null;
    }
}
