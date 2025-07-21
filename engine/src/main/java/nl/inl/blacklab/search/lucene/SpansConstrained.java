package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Arrays;

import org.apache.lucene.queries.spans.FilterSpans;

import nl.inl.blacklab.search.fimatch.ForwardIndexAccessorLeafReader;
import nl.inl.blacklab.search.fimatch.ForwardIndexDocument;
import nl.inl.blacklab.search.matchfilter.MatchFilter;

public class SpansConstrained extends BLFilterSpans<BLSpans> {

    /** The constraint with which we're filtering the clause */
    private final MatchFilter constraint;

    /** The hit query context, which contains captured group information */
    private HitQueryContext context;

    /** The current match info (captured groups, relations, etc.) */
    private MatchInfo[] matchInfo;

    /** Maps from term strings to term indices for each annotation. */
    private final ForwardIndexAccessorLeafReader fiAccessor;

    /** Where to get forward index tokens for the current doc */
    private ForwardIndexDocument currentFiDoc;

    public SpansConstrained(BLSpans in, MatchFilter constraint, ForwardIndexAccessorLeafReader fiAccessor) {
        super(in);
        this.constraint = constraint;
        this.fiAccessor = fiAccessor;
    }

    @Override
    protected void passHitQueryContextToClauses(HitQueryContext context) {
        super.passHitQueryContextToClauses(context);
        this.context = context;
        constraint.setHitQueryContext(context);
    }

    @Override
    protected FilterSpans.AcceptStatus accept(BLSpans candidate) throws IOException {
        if (matchInfo == null) {
            matchInfo = new MatchInfo[context.numberOfMatchInfos()];
        } else {
            Arrays.fill(matchInfo, null);
        }
        // We can only get match info for our candidate spans, but that should be enough
        // (our constraint may only reference captured groups within own clause)
        candidate.getMatchInfo(matchInfo);

        // Make sure we have the right forward index doc
        if (currentFiDoc == null || currentFiDoc.getSegmentDocId() != candidate.docID())
            currentFiDoc = fiAccessor.advanceForwardIndexDoc(candidate.docID());

        // OPT: if there are duplicate hits (including matchInfo), we'll
        //   evaluate the same constraint multiple times. Could be prevented
        //   by caching the previous results, but might not be worth it.
        if (constraint.evaluate(currentFiDoc, matchInfo).isTruthy())
            return FilterSpans.AcceptStatus.YES;
        return FilterSpans.AcceptStatus.NO;
    }

    @Override
    public String toString() {
        return "SpansConstrained(" + in + ", " + constraint + ")";
    }
}
