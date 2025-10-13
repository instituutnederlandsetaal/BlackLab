package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.search.spans.FilterSpans;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.search.spans.TermSpans;

/**
 * Wrap a TermSpans so it's a BLSpans.
 */
final class TermSpansWrapper extends BLFilterSpans<Spans> {

    TermSpansWrapper(TermSpans in) {
        super(in, SpanGuarantees.TERM);
        if (in == null)
            throw new IllegalArgumentException("Clause may not be null");
    }

    @Override
    public String toString() {
        return "WRAP(" + in + ")";
    }

    /**
     * Returns YES if the candidate should be an accepted match, NO if it should not, and
     * NO_MORE_IN_CURRENT_DOC if iteration should move on to the next document.
     */
    @Override
    protected FilterSpans.AcceptStatus accept(Spans candidate) throws IOException {
        return FilterSpans.AcceptStatus.YES;
    }
}
