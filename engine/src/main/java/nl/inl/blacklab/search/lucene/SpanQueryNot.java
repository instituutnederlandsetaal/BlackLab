package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreMode;

import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.Nfa;
import nl.inl.blacklab.search.fimatch.NfaState;
import nl.inl.util.LuceneUtil;

/**
 * Returns all tokens that do not occur in the matches of the specified query.
 *
 * Each token is returned as a single hit.
 */
public class SpanQueryNot extends BLSpanQueryAbstract {

    public static SpanGuarantees createGuarantees() {
        return new SpanGuaranteesAdapter(SpanGuarantees.TERM) {
            @Override
            public boolean okayToInvertForOptimization() {
                // Yes, inverting is actually an improvement
                return true;
            }

            @Override
            public boolean isSingleTokenNot() {
                return true;
            }
        };
    }

    public SpanQueryNot(BLSpanQuery query) {
        super(query);
        this.guarantees = createGuarantees();
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        BLSpanQuery rewritten = clauses.get(0).rewrite(reader);

        // Can we cancel out a double not?
        if (rewritten.guarantees().okayToInvertForOptimization())
            return rewritten.inverted(); // yes

        // No, must remain a NOT
        if (rewritten == clauses.get(0)) {
            return this;
        }

        return new SpanQueryNot(rewritten);
    }

    @Override
    public BLSpanQuery inverted() {
        return clauses.get(0); // Just return our clause, dropping the NOT operation
    }

	@Override
	public BLSpanWeight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
		BLSpanQuery query = clauses.get(0);
        BLSpanWeight weight = query == null ? null : query.createWeight(searcher, scoreMode, boost);
        return new SpanWeightNot(weight, searcher, scoreMode.needsScores() ? getTermStates(weight) : null, boost);
	}

    class SpanWeightNot extends BLSpanWeight {

        final BLSpanWeight weight;

        public SpanWeightNot(BLSpanWeight weight, IndexSearcher searcher, Map<Term, TermStates> terms, float boost)
                throws IOException {
            super(SpanQueryNot.this, searcher, terms, boost);
            this.weight = weight;
        }

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
            return weight == null || weight.isCacheable(ctx);
        }

        @Override
        public void extractTermStates(Map<Term, TermStates> contexts) {
            if (weight != null)
                weight.extractTermStates(contexts);
        }

        @Override
        public BLSpans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
            BLSpans spans = weight == null ? null : weight.getSpans(context, requiredPostings);
            if (spans == null)
                return new SpansNGrams(context.reader(), baseFieldName, 1, 1);
            return new SpansNot(context.reader(), baseFieldName, spans);
        }

    }

    @Override
    public String toString(String field) {
        return "NOT(" + (clauses.get(0) == null ? "" : clausesToString(field)) + ")";
    }

    @Override
    public Nfa getNfa(ForwardIndexAccessor fiAccessor, int direction) {
        Nfa nfa = clauses.get(0).getNfa(fiAccessor, direction);
        //nfa.finish();
        nfa.invert();
        NfaState not = nfa.getStartingState();
        return new Nfa(not, List.of(not)); // ignore the dangling arrows in the clause we've inverted
    }

    @Override
    public boolean canMakeNfa() {
        return clauses.get(0).canMakeNfa();
    }

    @Override
    public long reverseMatchingCost(IndexReader reader) {
        // Should be rewritten, but if it can't, calculate a rough indication of the number of token hits
        long freq = clauses.get(0).reverseMatchingCost(reader);
        return LuceneUtil.getSumTotalTermFreq(reader, getRealField()) - freq;
    }

    @Override
    public int forwardMatchingCost() {
        return clauses.get(0).forwardMatchingCost() + 1;
    }

    // no hashCode() and equals() because super class version is sufficient

}
