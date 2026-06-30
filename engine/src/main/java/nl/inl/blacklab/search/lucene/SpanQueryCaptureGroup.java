package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreMode;

/**
 * Captures its clause as a captured group.
 */
public class SpanQueryCaptureGroup extends BLSpanQueryAbstract {

    public static final String STR_INTERNAL_CAPTURE_GROUP_INDICATOR = "__@";

    public static final String MATCHINFO_SUFFIX_INTERNAL = STR_INTERNAL_CAPTURE_GROUP_INDICATOR + "internal";

    final String name;

    /**
     * How to adjust the left edge of the captured hits while matching. (necessary
     * because we try to internalize constant-length neighbouring clauses into our
     * clause to speed up matching)
     */
    final int leftAdjust;

    /**
     * How to adjust the right edge of the captured hits while matching. (necessary
     * because we try to internalize constant-length neighbouring clauses into our
     * clause to speed up matching)
     */
    final int rightAdjust;

    /**
     * Construct SpanQueryCaptureGroup object.
     *
     * @param query the query to capture a group from
     * @param name captured group name, or null to generate a unique internal capture group name
     */
    public SpanQueryCaptureGroup(BLSpanQuery query, String name) {
        this(query, name, 0, 0);
    }

    /**
     * Construct SpanQueryCaptureGroup object.
     * 
     * @param query the query to capture a group from
     * @param name captured group name, or null to generate a unique internal capture group name
     * @param leftAdjust how to adjust the captured group's start position
     * @param rightAdjust how to adjust the captured group's end position
     */
    public SpanQueryCaptureGroup(BLSpanQuery query, String name, int leftAdjust, int rightAdjust) {
        super(query);
        this.name = name == null ? SpanQueryCaptureGroup.generateCaptureName() : name;
        this.leftAdjust = leftAdjust;
        this.rightAdjust = rightAdjust;
        this.guarantees = query.guarantees();
    }

    static final AtomicLong NEXT_ID = new AtomicLong(0);

    /**
     * Return a unique name for an internal capture group (that is used during matching but not returned to the user)
     *
     * @param clause clause we're capturing
     * @return unique capture name
     */
    public static String generateCaptureName() {
        return "CAP-" + Long.toHexString(NEXT_ID.getAndIncrement()) + MATCHINFO_SUFFIX_INTERNAL;
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        List<BLSpanQuery> rewritten = rewriteClauses(reader);
        return rewritten == null ? this : new SpanQueryCaptureGroup(rewritten.get(0), name, leftAdjust, rightAdjust);
    }

    @Override
    public boolean matchesEmptySequence() {
        return clauses.get(0).matchesEmptySequence();
    }

    @Override
    public BLSpanQuery noEmpty() {
        if (!matchesEmptySequence())
            return this;
        return new SpanQueryCaptureGroup(clauses.get(0).noEmpty(), name, leftAdjust, rightAdjust);
    }

    @Override
    public BLSpanWeight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        BLSpanWeight weight = clauses.get(0).createWeight(searcher, scoreMode, boost);
        return new SpanWeightCaptureGroup(weight, searcher, scoreMode.needsScores() ? getTermStates(weight) : null, boost);
    }

    public String getCaptureName() {
        return name;
    }

    public BLSpanQuery getClause() {
        return clauses.get(0);
    }

    public BLSpanQuery copyWith(BLSpanQuery query) {
        return new SpanQueryCaptureGroup(query, name, leftAdjust, rightAdjust);
    }

    class SpanWeightCaptureGroup extends BLSpanWeight {

        final BLSpanWeight weight;

        public SpanWeightCaptureGroup(BLSpanWeight weight, IndexSearcher searcher, Map<Term, TermStates> terms, float boost)
                throws IOException {
            super(SpanQueryCaptureGroup.this, searcher, terms, boost);
            this.weight = weight;
        }

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
            return weight.isCacheable(ctx);
        }

        @Override
        public void extractTermStates(Map<Term, TermStates> contexts) {
            weight.extractTermStates(contexts);
        }

        @Override
        public BLSpans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
            BLSpans spans = weight.getSpans(context, requiredPostings);
            if (spans == null)
                return null;
            return new SpansCaptureGroup(spans, name, leftAdjust, rightAdjust);
        }

    }

    @Override
    public String toString(String field) {
        String adj = (leftAdjust != 0 || rightAdjust != 0 ? ", " + leftAdjust + ", " + rightAdjust : "");
        return "CAPTURE(" + clausesToString(field) + ", " + name + adj + ")";
    }

    @Override
    public boolean canInternalizeNeighbour(BLSpanQuery clause, boolean onTheRight) {
        return clause.guarantees().hitsAllSameLength();
    }

    @Override
    public BLSpanQuery internalizeNeighbour(BLSpanQuery clause, boolean onTheRight) {
        if (!clause.guarantees().hitsAllSameLength())
            throw new IllegalArgumentException("Can only internalize fixed-length clause!");
        // Check how to adjust the capture group edges after internalization
        int nla = leftAdjust, nra = rightAdjust;
        int clauseLength = clause.guarantees().hitsLengthMin();
        if (onTheRight)
            nra -= clauseLength;
        else
            nla += clauseLength;
        return new SpanQueryCaptureGroup(SpanQuerySequence.sequenceInternalize(clauses.get(0), clause, onTheRight),
                name, nla, nra);
    }

    @Override
    public long reverseMatchingCost(IndexReader reader) {
        return clauses.get(0).reverseMatchingCost(reader);
    }

    @Override
    public int forwardMatchingCost() {
        return clauses.get(0).forwardMatchingCost();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        SpanQueryCaptureGroup that = (SpanQueryCaptureGroup) o;
        return leftAdjust == that.leftAdjust && rightAdjust == that.rightAdjust && Objects.equals(name,
                that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), name, leftAdjust, rightAdjust);
    }
}
