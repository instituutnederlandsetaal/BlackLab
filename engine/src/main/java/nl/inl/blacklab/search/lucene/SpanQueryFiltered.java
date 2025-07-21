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
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.Weight;

/**
 * Filters a SpanQuery.
 */
public class SpanQueryFiltered extends BLSpanQueryAbstract {

    /** If set, this query determines hits from which documents will be kept. */
    private final Query filter;

    /**
     * Filter a SpanQuery.
     *
     * @param source the query to filter
     * @param filter the filter query
     */
    public SpanQueryFiltered(BLSpanQuery source, Query filter) {
        super(source);
        this.filter = filter;
        this.guarantees = source.guarantees();
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        List<BLSpanQuery> rewritten = rewriteClauses(reader);
        Query rewrittenFilter = filter.rewrite(reader);
        if (rewrittenFilter instanceof MultiTermQuery) {
            // Wrap it so it is rewritten to a BooleanQuery and we avoid the
            // "doesn't implement createWeight" problem.
            rewrittenFilter = new BLSpanMultiTermQueryWrapper<>(queryInfo, (MultiTermQuery) rewrittenFilter).rewrite(reader);
        }
        return rewritten == null ? this : new SpanQueryFiltered(rewritten.get(0), rewrittenFilter);
    }

    @Override
    public boolean matchesEmptySequence() {
        return clauses.get(0).matchesEmptySequence();
    }

    @Override
    public BLSpanQuery noEmpty() {
        return new SpanQueryFiltered(clauses.get(0).noEmpty(), filter);
    }

    @Override
    public BLSpanWeight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        BLSpanWeight weight = clauses.get(0).createWeight(searcher, scoreMode, boost);
        Query rewrite = filter.rewrite(searcher.getIndexReader());
        if (rewrite instanceof MultiTermQuery) {
            // Wrap it so it is rewritten to a BooleanQuery and we avoid the
            // "doesn't implement createWeight" problem.
            rewrite = new BLSpanMultiTermQueryWrapper<>(queryInfo, (MultiTermQuery) rewrite).rewrite(searcher.getIndexReader());
        }
        if (rewrite instanceof MatchNoDocsQuery)
            rewrite = new TermQuery(new Term("_nonexistentfield_", "_nonexistentvalue_")); // HACK. This "fixes" the 'Query does not implement createWeight issue'
        Weight filterWeight = rewrite.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, boost);
        return new SpanWeightFiltered(weight, filterWeight, searcher, scoreMode.needsScores() ? getTermStates(weight) : null, boost);
    }

    class SpanWeightFiltered extends BLSpanWeight {

        final BLSpanWeight weight;

        final Weight filterWeight;

        public SpanWeightFiltered(BLSpanWeight weight, Weight filterWeight, IndexSearcher searcher,
                Map<Term, TermStates> terms, float boost) throws IOException {
            super(SpanQueryFiltered.this, searcher, terms, boost);
            this.weight = weight;
            this.filterWeight = filterWeight;
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
            BLSpans result = weight.getSpans(context, requiredPostings);
            if (result == null)
                return null;
            Scorer scorer = filterWeight.scorer(context);
            return scorer == null ? null : new SpansFiltered(result, scorer);
        }

    }

    @Override
    public String toString(String field) {
        return "FILTER(" + clausesToString(field) + ", " + filter + ")";
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
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((filter == null) ? 0 : filter.hashCode());
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
        SpanQueryFiltered other = (SpanQueryFiltered) obj;
        if (filter == null) {
            if (other.filter != null)
                return false;
        } else if (!filter.equals(other.filter))
            return false;
        return true;
    }

}
