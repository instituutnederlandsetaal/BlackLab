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
import nl.inl.blacklab.search.fimatch.NfaTwoWay;

/**
 * Find collocations using the forward index.
 */
public class SpanQueryCollocations extends BLSpanQueryAbstract {

    public static SpanGuarantees createGuarantees(SpanGuarantees nfaQuery) {
        return new SpanGuaranteesAdapter() {
            @Override
            public boolean hitsAllSameLength() {
                return nfaQuery.hitsAllSameLength();
            }

            @Override
            public int hitsLengthMin() {
                return nfaQuery.hitsLengthMin();
            }

            @Override
            public int hitsLengthMax() {
                return nfaQuery.hitsLengthMax();
            }

            @Override
            public boolean hitsStartPointSorted() {
                return false;
            }

            @Override
            public boolean hitsEndPointSorted() {
                return false;
            }

            @Override
            public boolean hitsHaveUniqueStart() {
                return false; // same collocate may be found near different keywords
            }

            @Override
            public boolean hitsHaveUniqueEnd() {
                return false; // same collocate may be found near different keywords
            }
        };
    }

    /** Our NFA, both in our own direction and the opposite direction. */
    final NfaTwoWay nfa;

    /** the query that generated the NFA, so we can still use its guarantee methods for optimization */
    private final BLSpanQuery nfaQuery;

    public record CollocationContext(SequenceGap before, SequenceGap after) {}

    /** minimum gap when collocate before keyword */
    final CollocationContext collocationContext;

    /** maps between term strings and term indices for each annotation */
    final ForwardIndexAccessor fiAccessor;

    /**
     *
     * @param keyword hits to use as keywords to find collocates
     * @param nfa the NFA to use for finding collocates
     * @param nfaQuery the query that generated the NFA, so we can still use its
     *            guarantee methods for optimization
     * @param collocationContext the context for collocations, including gaps before and after the keyword
     * @param fiAccessor maps between term strings and term indices for each annotation
     */
    public SpanQueryCollocations(BLSpanQuery keyword, NfaTwoWay nfa, BLSpanQuery nfaQuery,
            CollocationContext collocationContext,
            ForwardIndexAccessor fiAccessor) {
        super(keyword);
        this.nfa = nfa;
        this.nfaQuery = nfaQuery;
        this.collocationContext = collocationContext;
        this.fiAccessor = fiAccessor;
        this.guarantees = createGuarantees(nfaQuery.guarantees());
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        BLSpanQuery rewritten = clauses.get(0).rewrite(reader);
        if (rewritten != clauses.get(0)) {
            return new SpanQueryCollocations(rewritten, nfa, nfaQuery, collocationContext, fiAccessor);
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
        public BLSpans getSpans(final LeafReaderContext lrc, Postings requiredPostings) throws IOException {
            BLSpans anchorSpans = anchorWeight.getSpans(lrc, requiredPostings);
            if (anchorSpans == null)
                return null;
            return new SpansCollocations(anchorSpans, guarantees, collocationContext, lrc, fiAccessor, nfa);
        }
    }

    @Override
    public String toString(String field) {
        return "COLLOCATIONS(" + clausesToString(field) + ", " + nfa.getNfa() + ", " + collocationContext + ")";
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
        return Objects.equals(nfaQuery, that.nfaQuery) && Objects.equals(collocationContext, that.collocationContext);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), nfaQuery, collocationContext);
    }
}
