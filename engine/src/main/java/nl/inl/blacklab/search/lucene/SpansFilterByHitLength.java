package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.queries.spans.FilterSpans;

/**
 * Filter hits by hit length.
 *
 * Useful if a query contains an AND with a "any token" repetition clause.
 * This can happen when searching for relations, e.g. [] --nmod--> []
 * Filtering by length is more efficient than using SpansNGrams.
 */
public class SpansFilterByHitLength extends BLFilterSpans<BLSpans> {

    /** Minimum hit length, or 0 if no minimum */
    int min;

    /** Maximum hit length, or {@link BLSpans#MAX_UNLIMITED} if no maximum. */
    int max;

    /**
     * Filter hits by hit length
     *
     * @param in the spans to filter
     * @param min minimum hit length, or 0 if no minimum
     * @param max maximum hit length, or {@link BLSpans#MAX_UNLIMITED} if no maximum
     */
    public SpansFilterByHitLength(BLSpans in, int min, int max) {
        super(in);
        this.min = min;
        this.max = max;
    }

    @Override
    protected FilterSpans.AcceptStatus accept(BLSpans candidate) throws IOException {
        int l = candidate.endPosition() - candidate.startPosition();
        return l >= min && l <= max ? FilterSpans.AcceptStatus.YES : FilterSpans.AcceptStatus.NO;
    }

    @Override
    public String toString() {
        return "SpansFilterByHitLength(" + in + ", " + min + ", " + max + ")";
    }
}
