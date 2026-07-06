package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreMode;

import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessorLeafReader;
import nl.inl.blacklab.search.fimatch.NfaState;
import nl.inl.blacklab.search.fimatch.NfaTwoWay;

/**
 * Find collocations using the forward index.
 */
public class SpanQueryCollocations extends BLSpanQueryAbstract {

    public static SpanGuarantees createGuarantees(SpanGuarantees clause, SpanGuarantees nfaQuery, int direction, boolean startOfAnchor) {
        return new SpanGuaranteesAdapter() {
            @Override
            public boolean hitsAllSameLength() {
                return clause.hitsAllSameLength() && nfaQuery.hitsAllSameLength();
            }

            @Override
            public int hitsLengthMin() {
                if (startOfAnchor && direction == SpanQueryFiSeq.DIR_BACKWARD ||
                        !startOfAnchor && direction == SpanQueryFiSeq.DIR_FORWARD) {
                    // Non-overlapping; add the two values
                    return clause.hitsLengthMin() + nfaQuery.hitsLengthMin();
                }
                // Overlapping; use the largest value
                return Math.max(clause.hitsLengthMin(), nfaQuery.hitsLengthMin());
            }

            @Override
            public int hitsLengthMax() {
                if (startOfAnchor && direction == SpanQueryFiSeq.DIR_BACKWARD ||
                        !startOfAnchor && direction == SpanQueryFiSeq.DIR_FORWARD) {
                    // Non-overlapping; add the two values
                    return clause.hitsLengthMax() + nfaQuery.hitsLengthMax();
                }
                // Overlapping; use the largest value
                return Math.min(clause.hitsLengthMax(), nfaQuery.hitsLengthMax());
            }

            @Override
            public boolean hitsStartPointSorted() {
                if (direction == SpanQueryFiSeq.DIR_FORWARD)
                    return clause.hitsStartPointSorted();
                return clause.hitsStartPointSorted() && nfaQuery.hitsAllSameLength();
            }

            @Override
            public boolean hitsEndPointSorted() {
                if (direction == SpanQueryFiSeq.DIR_BACKWARD)
                    return clause.hitsEndPointSorted();
                return clause.hitsEndPointSorted() && nfaQuery.hitsAllSameLength();
            }

            @Override
            public boolean hitsHaveUniqueStart() {
                if (direction == SpanQueryFiSeq.DIR_FORWARD)
                    return clause.hitsHaveUniqueStart();
                return clause.hitsHaveUniqueStart() && nfaQuery.hitsAllSameLength() || nfaQuery.hitsHaveUniqueStart();
            }

            @Override
            public boolean hitsHaveUniqueEnd() {
                if (direction == SpanQueryFiSeq.DIR_BACKWARD)
                    return clause.hitsHaveUniqueEnd();
                return clause.hitsHaveUniqueEnd() && nfaQuery.hitsAllSameLength() || nfaQuery.hitsHaveUniqueEnd();
            }
        };
    }

    /** if true, use the starts of anchor hits; if false, use the ends */
    final boolean startOfAnchor;

    /** Our NFA, both in our own direction and the opposite direction. */
    final NfaTwoWay nfa;

    /** the query that generated the NFA, so we can still use its guarantee methods for optimization */
    private final BLSpanQuery nfaQuery;

    /** the direction to match in (DIR_FORWARD / DIR_BACKWARD) */
    final int direction;

    /** minimum gap between keyword and collocate */
    final int gapMin;

    /** maximum gap between keyword and collocate */
    final int gapMax;

    /** maps between term strings and term indices for each annotation */
    final ForwardIndexAccessor fiAccessor;

    /**
     *
     * @param keyword hits to use as keywords to find collocates
     * @param startOfAnchor if true, use the starts of keyword hits; if false, use the ends
     * @param nfa the NFA to use for finding collocates
     * @param nfaQuery the query that generated the NFA, so we can still use its
     *            guarantee methods for optimization
     * @param direction the direction to match in (DIR_FORWARD / DIR_BACKWARD)
     * @param gapMin minimum gap between keyword and collocate
     * @param gapMax maximum gap between keyword and collocate
     * @param fiAccessor maps between term strings and term indices for each annotation
     */
    public SpanQueryCollocations(BLSpanQuery keyword, boolean startOfAnchor, NfaTwoWay nfa, BLSpanQuery nfaQuery,
            int direction, int gapMin, int gapMax, ForwardIndexAccessor fiAccessor) {
        super(keyword);
        this.startOfAnchor = startOfAnchor;
        this.nfa = nfa;
        this.nfaQuery = nfaQuery;
        this.direction = direction;
        this.gapMin = gapMin;
        this.gapMax = gapMax;
        this.fiAccessor = fiAccessor;
        this.guarantees = createGuarantees(keyword.guarantees(), nfaQuery.guarantees(), direction, startOfAnchor);
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        BLSpanQuery rewritten = clauses.get(0).rewrite(reader);
        if (rewritten != clauses.get(0)) {
            return new SpanQueryCollocations(rewritten, startOfAnchor, nfa, nfaQuery, direction, gapMin, gapMax, fiAccessor);
        }
        return this;
    }

    @Override
    public BLSpanWeight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {

        // Finalize our NFA, so it looks up the indexes for its annotations.
        nfa.finish();
        nfa.lookupAnnotationIndexes(fiAccessor);

        BLSpanWeight anchorWeight = clauses.get(0).createWeight(searcher, scoreMode, boost);
        Map<Term, TermStates> contexts = scoreMode.needsScores() ? getTermStates(anchorWeight) : null;
        return new SpanWeightFiSeq(anchorWeight, searcher, contexts, boost);
    }

    class SpanWeightFiSeq extends BLSpanWeight {

        final BLSpanWeight anchorWeight;

        public SpanWeightFiSeq(BLSpanWeight anchorWeight, IndexSearcher searcher, Map<Term, TermStates> terms,
                float boost) throws IOException {
            super(SpanQueryCollocations.this, searcher, terms, boost);
            this.anchorWeight = anchorWeight;
        }

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
            // TODO: check if the NFA is cacheable. The forward index is an immutable segment structure,
            //    isn't it..? But right now, there's also still a global forward index API which might
            //    cause trouble...
            return false; // anchorWeight.isCacheable(ctx);
        }

        @Override
        public void extractTermStates(Map<Term, TermStates> contexts) {
            anchorWeight.extractTermStates(contexts);
        }

        @Override
        public BLSpans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
            BLSpans anchorSpans = anchorWeight.getSpans(context, requiredPostings);
            if (anchorSpans == null)
                return null;
            ForwardIndexAccessorLeafReader fiLeafReader = fiAccessor.getForwardIndexAccessorLeafReader(context);
            NfaState startingState = nfa.getNfa().getStartingState().forSegment(context);
            return new SpansCollocations(anchorSpans, startOfAnchor, startingState, direction, gapMin, gapMax, fiLeafReader, guarantees);
        }
    }

    @Override
    public String toString(String field) {
        return "COLLOCATIONS(" + clausesToString(field) + ", " + nfa.getNfa() + ", " + direction + ", " + gapMin + ", " + gapMax + ")";
    }

    @Override
    public boolean matchesEmptySequence() {
        return false; // can't be used if clause matches empty sequence, we need anchors
    }

    @Override
    public BLSpanQuery noEmpty() {
        return this;
    }

    @Override
    public long reverseMatchingCost(IndexReader reader) {
        return clauses.get(0).reverseMatchingCost(reader);
    }

    @Override
    public int forwardMatchingCost() {
        return clauses.get(0).forwardMatchingCost() + nfaQuery.forwardMatchingCost();
    }

    public int getDirection() {
        return direction;
    }

    public ForwardIndexAccessor getFiAccessor() {
        return fiAccessor;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        SpanQueryCollocations that = (SpanQueryCollocations) o;
        return startOfAnchor == that.startOfAnchor && direction == that.direction &&
                gapMin == that.gapMin && gapMax == that.gapMax && Objects.equals(nfaQuery, that.nfaQuery);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), startOfAnchor, nfaQuery, direction, gapMin, gapMax);
    }
}
