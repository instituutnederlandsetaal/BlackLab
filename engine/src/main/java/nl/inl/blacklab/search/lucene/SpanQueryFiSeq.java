package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreMode;

import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessorLeafReader;
import nl.inl.blacklab.search.fimatch.Nfa;
import nl.inl.blacklab.search.fimatch.NfaState;
import nl.inl.blacklab.search.fimatch.NfaTwoWay;

/**
 * Find hits that match the specified NFA, starting from the specified anchor
 * query (either the start of end of those hits, with a possible offset added),
 * in the specified direction.
 */
public class SpanQueryFiSeq extends BLSpanQueryAbstract {

    public static final int DIR_TO_LEFT = -1;

    public static final int DIR_TO_RIGHT = 1;

    public static final boolean START_OF_ANCHOR = true;

    public static final boolean END_OF_ANCHOR = false;

    public static SpanGuarantees createGuarantees(SpanGuarantees clause, SpanGuarantees nfaQuery, int direction, boolean startOfAnchor) {
        return new SpanGuaranteesAdapter() {
            @Override
            public boolean hitsAllSameLength() {
                return clause.hitsAllSameLength() && nfaQuery.hitsAllSameLength();
            }

            @Override
            public int hitsLengthMin() {
                if (startOfAnchor && direction == DIR_TO_LEFT || !startOfAnchor && direction == DIR_TO_RIGHT) {
                    // Non-overlapping; add the two values
                    return clause.hitsLengthMin() + nfaQuery.hitsLengthMin();
                }
                // Overlapping; use the largest value
                return Math.max(clause.hitsLengthMin(), nfaQuery.hitsLengthMin());
            }

            @Override
            public int hitsLengthMax() {
                if (startOfAnchor && direction == DIR_TO_LEFT || !startOfAnchor && direction == DIR_TO_RIGHT) {
                    // Non-overlapping; add the two values
                    return clause.hitsLengthMax() + nfaQuery.hitsLengthMax();
                }
                // Overlapping; use the largest value
                return Math.min(clause.hitsLengthMax(), nfaQuery.hitsLengthMax());
            }

            @Override
            public boolean hitsStartPointSorted() {
                if (direction == DIR_TO_RIGHT)
                    return clause.hitsStartPointSorted();
                return clause.hitsStartPointSorted() && nfaQuery.hitsAllSameLength();
            }

            @Override
            public boolean hitsEndPointSorted() {
                if (direction == DIR_TO_LEFT)
                    return clause.hitsEndPointSorted();
                return clause.hitsEndPointSorted() && nfaQuery.hitsAllSameLength();
            }

            @Override
            public boolean hitsHaveUniqueStart() {
                if (direction == DIR_TO_RIGHT)
                    return clause.hitsHaveUniqueStart();
                return clause.hitsHaveUniqueStart() && nfaQuery.hitsAllSameLength() || nfaQuery.hitsHaveUniqueStart();
            }

            @Override
            public boolean hitsHaveUniqueEnd() {
                if (direction == DIR_TO_LEFT)
                    return clause.hitsHaveUniqueEnd();
                return clause.hitsHaveUniqueEnd() && nfaQuery.hitsAllSameLength() || nfaQuery.hitsHaveUniqueEnd();
            }
        };
    }

    final boolean startOfAnchor;

    /** Our NFA, both in our own direction and the opposite direction. */
    final NfaTwoWay nfa;

    private final BLSpanQuery nfaQuery;

    final int direction;

    final ForwardIndexAccessor fiAccessor;

    /**
     *
     * @param anchor hits to use as anchor to start NFA matching
     * @param startOfAnchor if true, use the starts of anchor hits; if false, use
     *            the ends
     * @param nfa the NFA to use for matching
     * @param nfaQuery the query that generated the NFA, so we can still use its
     *            guarantee methods for optimization
     * @param direction the direction to match in (-1 = right-to-left, 1 =
     *            left-to-right; use DIR_TO_RIGHT / DIR_TO_LEFT)
     * @param fiAccessor maps between term strings and term indices for each
     *            annotation
     */
    public SpanQueryFiSeq(BLSpanQuery anchor, boolean startOfAnchor, NfaTwoWay nfa, BLSpanQuery nfaQuery, int direction,
            ForwardIndexAccessor fiAccessor) {
        super(anchor);
        this.startOfAnchor = startOfAnchor;
        this.nfa = nfa;
        this.nfaQuery = nfaQuery;
        this.direction = direction;
        this.fiAccessor = fiAccessor;
        this.guarantees = createGuarantees(anchor.guarantees(), nfaQuery.guarantees(), direction, startOfAnchor);
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        BLSpanQuery rewritten = clauses.get(0).rewrite(reader);
        if (rewritten != clauses.get(0)) {
            return new SpanQueryFiSeq(rewritten, startOfAnchor, nfa, nfaQuery, direction, fiAccessor);
        }
        return this;
    }

    @Override
    public BLSpanWeight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {

        // Finalize our NFA, so it looks up annotation numbers for its annotation names.
        nfa.finish();
        nfa.lookupAnnotationNumbers(fiAccessor, new IdentityHashMap<>());

        BLSpanWeight anchorWeight = clauses.get(0).createWeight(searcher, scoreMode, boost);
        Map<Term, TermStates> contexts = scoreMode.needsScores() ? getTermStates(anchorWeight) : null;
        return new SpanWeightFiSeq(anchorWeight, searcher, contexts, boost);
    }

    class SpanWeightFiSeq extends BLSpanWeight {

        final BLSpanWeight anchorWeight;

        public SpanWeightFiSeq(BLSpanWeight anchorWeight, IndexSearcher searcher, Map<Term, TermStates> terms,
                float boost) throws IOException {
            super(SpanQueryFiSeq.this, searcher, terms, boost);
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
            NfaState startingState = nfa.getNfa().getStartingState();
            return new SpansFiSeq(anchorSpans, startOfAnchor, startingState, direction, fiLeafReader, guarantees);
        }
    }

    @Override
    public String toString(String field) {
        return "FISEQ(" + clausesToString(field) + ", " + nfa.getNfa() + ", " + direction + ")";
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

    /**
     * Append a new NFA fragment to (a copy of) our existing NFA, and return a new
     * SpanQueryFiSeq using it.
     *
     * @param originalQuery query that yielded this NFA
     * @return newly created, expanded SpanQueryFiSeq
     */
    public SpanQueryFiSeq appendNfa(BLSpanQuery originalQuery) {
        NfaTwoWay newNfa = nfa.copy();
        newNfa.append(originalQuery.getNfaTwoWay(fiAccessor, direction));
        boolean addToRight = direction == DIR_TO_RIGHT;
        SpanQuerySequence newNfaQuery = SpanQuerySequence.sequenceInternalize(nfaQuery, originalQuery, addToRight);
        return new SpanQueryFiSeq(clauses.get(0), startOfAnchor, newNfa, newNfaQuery, direction, fiAccessor);
    }

    public ForwardIndexAccessor getFiAccessor() {
        return fiAccessor;
    }

    @Override
    public Nfa getNfa(ForwardIndexAccessor fiAccessor, int direction) {
        // Convert our anchor to an NFA.
        Nfa anchorNfa = clauses.get(0).getNfa(fiAccessor, direction);

        // Is the direction we want the same as the current direction?
        if (direction == this.direction) {
            // Yes, same direction. Append original NFA to new anchor NFA.
            anchorNfa.append(nfa.getNfa().copy());
            return anchorNfa;
        }
        // Reverse direction. Reverse our original NFA and append the anchor NFA.
        Nfa result = nfa.getNfaReverse().copy();
        result.append(anchorNfa);
        return result;
    }

    @Override
    public boolean canMakeNfa() {
        return clauses.get(0).canMakeNfa();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        // NOTE: we don't need to use nfa or fiAccessor because they follow from nfaQuery
        result = prime * result + direction;
        result = prime * result + ((nfaQuery == null) ? 0 : nfaQuery.hashCode());
        result = prime * result + (startOfAnchor ? 1231 : 1237);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        SpanQueryFiSeq other = (SpanQueryFiSeq) obj;
        // NOTE: we don't need to use nfa or fiAccessor because they follow from nfaQuery
        if (direction != other.direction)
            return false;
        if (nfaQuery == null) {
            if (other.nfaQuery != null)
                return false;
        } else if (!nfaQuery.equals(other.nfaQuery))
            return false;
        if (startOfAnchor != other.startOfAnchor)
            return false;
        return true;
    }




}
