package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.queries.spans.BLSpanOrQuery;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.SegmentCacheable;
import org.apache.lucene.queries.spans.SpanWeight;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.Nfa;
import nl.inl.blacklab.search.fimatch.NfaState;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * A SpanQuery for an AND NOT query. Produces all spans matching all the
 * "include" parts, except for those that match any span in the "exclude" part.
 */
public class SpanQueryAndNot extends BLSpanQuery {

    public static SpanGuarantees createGuarantees(List<SpanGuarantees> include, boolean hasExclude) {
        return new SpanGuaranteesAdapter() {
            @Override
            public boolean okayToInvertForOptimization() {
                // Inverting is "free" if it will still be an AND NOT query (i.e. will have a positive component).
                return producesSingleTokens() && hasExclude;
            }

            @Override
            public boolean isSingleAnyToken() {
                return include.stream().allMatch(SpanGuarantees::isSingleAnyToken) && !hasExclude;
            }

            @Override
            public boolean isSingleTokenNot() {
                return producesSingleTokens() && include.isEmpty();
            }

            @Override
            public boolean hitsAllSameLength() {
                if (include.isEmpty())
                    return true;
                for (SpanGuarantees clause : include) {
                    if (clause.hitsAllSameLength())
                        return true; // if any clause has fixed-length hits, so does the whole query
                }
                return true;
            }

            @Override
            public int hitsLengthMin() {
                if (include.isEmpty())
                    return 1;
                int l = 0;
                for (SpanGuarantees clause : include) {
                    if (clause.hitsLengthMin() > l)
                        l = clause.hitsLengthMin();
                }
                return l;
            }

            @Override
            public int hitsLengthMax() {
                if (include.isEmpty())
                    return 1;
                int l = Integer.MAX_VALUE;
                for (SpanGuarantees clause : include) {
                    if (clause.hitsLengthMax() < l)
                        l = clause.hitsLengthMax();
                }
                return l;
            }

            @Override
            public boolean hitsEndPointSorted() {
                return hitsStartPointSorted() && hitsAllSameLength();
            }

            @Override
            public boolean hitsStartPointSorted() {
                return true;
            }

            @Override
            public boolean hitsHaveUniqueStart() {
                if (include.isEmpty())
                    return true;
                for (SpanGuarantees clause : include) {
                    if (clause.hitsHaveUniqueStart())
                        return true;
                }
                return true;
            }

            @Override
            public boolean hitsHaveUniqueEnd() {
                if (include.isEmpty())
                    return true;
                for (SpanGuarantees clause : include) {
                    if (clause.hitsHaveUniqueEnd())
                        return true;
                }
                return true;
            }

            @Override
            public boolean hitsHaveUniqueStartEnd() {
                if (include.isEmpty()) {
                    // pure not query always produces unique hits (all tokens that are not part of the matches)
                    return true;
                }
                for (SpanGuarantees clause : include) {
                    if (!clause.hitsHaveUniqueStartEnd()) {
                        // At least one clause has multiple hits with same start/end, therefore the resulting matches
                        // may not be unique
                        return false;
                    }
                }
                // All clauses have unique spans, so we can guarantee the resulting matches are
                return true;
            }

            @Override
            public boolean hitsHaveUniqueStartEndAndInfo() {
                if (include.isEmpty()) {
                    // pure not query always produces unique hits (all tokens that are not part of the matches)
                    return true;
                }
                for (SpanGuarantees clause : include) {
                    if (!clause.hitsHaveUniqueStartEndAndInfo()) {
                        // At least one clause has multiple hits with same start/end/info, therefore the resulting matches
                        // may not be unique
                        return false;
                    }
                }
                // All clauses have unique spans, so we can guarantee the resulting matches are
                return true;
            }

            @Override
            public boolean hitsCanOverlap() {
                if (include.isEmpty()) {
                    // pure not query always produces nonoverlapping hits
                    // (all tokens that are not part of the matches)
                    return false;
                }
                if (!hitsHaveUniqueStartEnd()) {
                    // Even if one of the clauses has non-overlapping hits,
                    // if another clause has duplicate starts/ends, this query will
                    // still produce duplicates (it combinatorically combines
                    // any duplicates to ensure we get all combinations of match info)
                    return true;
                }
                for (SpanGuarantees clause : include) {
                    if (!clause.hitsCanOverlap()) {
                        // At least one clause has nonoverlapping hits,
                        // so the resulting hits can never be overlapping.
                        return false;
                    }
                }
                // All clauses have overlapping hits.
                return true;
            }
        };
    }

    private final List<BLSpanQuery> include;

    private final List<BLSpanQuery> exclude;

    private SpansAndFilterFactory filterFactory = null;

    public SpanQueryAndNot(List<BLSpanQuery> include, List<BLSpanQuery> exclude) {
        super(include != null && !include.isEmpty() ? include.get(0).queryInfo : exclude != null && !exclude.isEmpty() ? exclude.get(0).queryInfo : null);
        this.include = include == null ? new ArrayList<>() : include;
        this.exclude = exclude == null ? new ArrayList<>() : exclude;
        if (this.include.size() == 0 && this.exclude.size() == 0)
            throw new IllegalArgumentException("AND(NOT)/RSPAN query without clauses");
        checkAllCompatibleFields(this.include);
        checkAllCompatibleFields(this.exclude);

        List<SpanGuarantees> clauseGuarantees = SpanGuarantees.from(this.include);
        this.guarantees = createGuarantees(clauseGuarantees, !this.exclude.isEmpty());
    }

    /**
     * Set how to filter hits.
     *
     * @param filterFactory the filter factory to create our hits filters
     */
    public void setFilter(SpansAndFilterFactory filterFactory) {
        this.filterFactory = filterFactory;
        if (!exclude.isEmpty())
            throw new BlackLabRuntimeException("Cannot combine exclude and filter!");
    }

    @Override
    public BLSpanQuery inverted() {
        if (exclude.isEmpty()) {
            // In this case, it's better to just wrap this in TextPatternNot,
            // so it will be recognized by other rewrite()s.
            return super.inverted();
        }

        // ! ( (a & b) & !(c & d) ) --> !a | !b | (c & d)
        List<BLSpanQuery> inclNeg = new ArrayList<>();
        for (BLSpanQuery tp : include) {
            inclNeg.add(tp.inverted());
        }
        if (exclude.size() == 1)
            inclNeg.add(exclude.get(0));
        else
            inclNeg.add(new SpanQueryAndNot(exclude, null));
        return new BLSpanOrQuery(inclNeg.toArray(new BLSpanQuery[0]));
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {

        // If any of our clauses capture a group, lift them out of the AND.
        List<SpanQueryCaptureGroup> savedCaptures = new ArrayList<>();
        List<BLSpanQuery> includeWithoutCaptures = new ArrayList<>(include.size());
        for (BLSpanQuery cl: include) {
            if (cl instanceof SpanQueryCaptureGroup) {
                // Remember capture and replace it with its clause
                savedCaptures.add((SpanQueryCaptureGroup) cl);
                includeWithoutCaptures.add(((SpanQueryCaptureGroup) cl).getClause());
            } else {
                includeWithoutCaptures.add(cl);
            }
        }

        // Flatten nested AND queries, and invert negative-only clauses.
        // This doesn't change the query because the AND operator is associative.
        List<BLSpanQuery> flatCl = new ArrayList<>();
        List<BLSpanQuery> flatNotCl = new ArrayList<>();
        boolean anyRewritten = flattenInvert(reader, includeWithoutCaptures, exclude, flatCl, flatNotCl);

        // Rewrite clauses, and again flatten/invert if necessary.
        List<BLSpanQuery> rewrCl = new ArrayList<>();
        List<BLSpanQuery> rewrNotCl = new ArrayList<>();
        anyRewritten = anyRewritten | rewriteFlattenInvert(reader, flatCl, flatNotCl, rewrCl, rewrNotCl);

        if (rewrCl.isEmpty()) {
            // All-negative; node should be rewritten to OR.
            if (rewrNotCl.size() == 1)
                return rewrNotCl.get(0).inverted().rewrite(reader);
            return (new BLSpanOrQuery(rewrNotCl.toArray(new BLSpanQuery[0]))).inverted().rewrite(reader);
        }

        // Deal with any "match n-grams" clauses (any token repetitions).
        int[] minMax = new int[] { 0, MAX_UNLIMITED };
        boolean mustFilterOnHitLength = false;
        if (rewrCl.size() > 1) {
            // If there's more than one positive clause, remove the super general "match all n-grams" clause.
            // Also replace any "match n-grams" clauses with a more efficient filter on hit length.
            List<BLSpanQuery> rewrClNew = rewrCl.stream().filter(cl -> {
                if (cl instanceof SpanQueryAnyToken any) {
                    // Any token repetition clause. Filter it out and keep track
                    // of min/max, so we can filter hits on those lengths later if needed.
                    if (any.getMin() > minMax[0])
                        minMax[0] = any.getMin();
                    if (any.getMax() < minMax[1])
                        minMax[1] = any.getMax();
                    return false;
                }
                return true;
            }).collect(Collectors.toList());
            if (!rewrClNew.equals(rewrCl))
                anyRewritten = true;
            rewrCl = rewrClNew;
            if (minMax[0] > 0 || minMax[1] < MAX_UNLIMITED) {
                // We filtered out any token repetition clause(s),
                // so we must filter hits on min/max length at the end.
                mustFilterOnHitLength = true;
            }
            if (rewrCl.isEmpty()) {
                // All clauses were any token repetitions.
                return reapplyCaptures(savedCaptures,
                        new SpanQueryAnyToken(queryInfo, minMax[0], minMax[1], includeWithoutCaptures.get(0).getRealField()));
            }
        }

        // Combine the rewritten clauses into a single query, and filter on length if necessary.
        BLSpanQuery result;
        if (!anyRewritten && exclude.isEmpty()) {
            // Nothing needs to be rewritten.
            result = this;
        } else if (rewrCl.size() == 1 && rewrNotCl.isEmpty()) {
            // Single positive clause
            result = rewrCl.get(0);
        } else {
            // Combination of positive and possibly negative clauses
            if (rewrCl.size() == 1)
                result = rewrCl.get(0);
            else {
                result = new SpanQueryAndNot(rewrCl, null);
                if (filterFactory != null) {
                    ((SpanQueryAndNot) result).setFilter(filterFactory);
                    if (!rewrNotCl.isEmpty()) {
                        // Apply the exclusions after filtering (exclude+filter not supported in one operation)
                        result = new SpanQueryAndNot(List.of(result), rewrNotCl);
                    }
                }
            }
            if (!rewrNotCl.isEmpty()) {
                // Add the negative clauses, using an inverted position filter
                BLSpanQuery excludeResult = rewrNotCl.size() == 1 ? rewrNotCl.get(0)
                        : new BLSpanOrQuery(rewrNotCl.toArray(new BLSpanQuery[0]));
                result = new SpanQueryPositionFilter(result, excludeResult,
                        SpanQueryPositionFilter.Operation.MATCHES, true).rewrite(reader);
            }
        }
        if (mustFilterOnHitLength) {
            if (minMax[0] == minMax[1] && result.guarantees().hitsAllSameLength() &&
                    result.guarantees().hitsLengthMin() == minMax[0]) {
                // We can just return the result, it already guarantees the right length.
            } else {
                // We must filter by hit length
                result = new SpanQueryFilterByHitLength(result, minMax[0], minMax[1]).rewrite(reader);
            }
        }
        // Re-apply any captures we "lifted" out of our clauses.
        return reapplyCaptures(savedCaptures, result);
    }

    /**
     * We've lifted out some captures. Re-apply them to the resulting query.
     *
     * @param captures list of captures
     * @param query   query to apply captures to
     * @return query with captures applied
     */
    private BLSpanQuery reapplyCaptures(List<SpanQueryCaptureGroup> captures, BLSpanQuery query) {
        for (SpanQueryCaptureGroup capture : captures) {
            query = capture.copyWith(query);
        }
        return query;
    }

    private boolean flattenInvert(IndexReader reader, List<BLSpanQuery> include,
            List<BLSpanQuery> exclude, List<BLSpanQuery> rinclude, List<BLSpanQuery> rexclude) throws IOException {
        return _rewriteFlattenInvert(reader, include, exclude, false, rinclude, rexclude);
    }

    private boolean rewriteFlattenInvert(IndexReader reader, List<BLSpanQuery> include,
            List<BLSpanQuery> exclude, List<BLSpanQuery> rinclude, List<BLSpanQuery> rexclude)
            throws IOException {
        return _rewriteFlattenInvert(reader, include, exclude, true, rinclude, rexclude);
    }

    private boolean _rewriteFlattenInvert(IndexReader reader, List<BLSpanQuery> include,
            List<BLSpanQuery> exclude, boolean rewrite, List<BLSpanQuery> rinclude, List<BLSpanQuery> rexclude)
            throws IOException {
        boolean anyRewritten = false;
        boolean isNot = false; // first we do the included clauses, then the excluded ones
        for (List<BLSpanQuery> cl : Arrays.asList(include, exclude)) {
            for (BLSpanQuery orig : cl) {
                List<BLSpanQuery> clPos = isNot ? rexclude : rinclude;
                List<BLSpanQuery> clNeg = isNot ? rinclude : rexclude;
                BLSpanQuery child = orig;
                if (rewrite) {
                    child = orig.rewrite(reader);
                    if (child != orig)
                        anyRewritten = true;
                }
                // can we flatten? (same query type and same options)
                boolean flatten = canFlatten(child);
                if (!flatten && child.guarantees().isSingleTokenNot()) {
                    // "Switch sides": invert the clause, and
                    // swap the lists we add clauses to.
                    child = child.inverted();
                    List<BLSpanQuery> temp = clPos;
                    clPos = clNeg;
                    clNeg = temp;
                    anyRewritten = true;
                    flatten = canFlatten(child);
                }
                if (flatten) {
                    // Flatten.
                    // Child AND operation we want to flatten into this AND operation.
                    // Replace the child, incorporating its children into this AND operation.
                    clPos.addAll(((SpanQueryAndNot) child).include);
                    clNeg.addAll(((SpanQueryAndNot) child).exclude);
                    anyRewritten = true;
                } else {
                    // Just add it.
                    clPos.add(child);
                }
            }
            isNot = true; // continue with the excluded clauses
        }
        return anyRewritten;
    }

    private boolean canFlatten(BLSpanQuery child) {
        return child instanceof SpanQueryAndNot &&
                Objects.equals(((SpanQueryAndNot) child).filterFactory, filterFactory);
    }

    @Override
    public void visit(QueryVisitor visitor) {
        if (visitor.acceptField(getField())) {
            include.forEach(cl -> cl.visit(visitor.getSubVisitor(Occur.MUST, this)));
            exclude.forEach(cl -> cl.visit(visitor.getSubVisitor(Occur.MUST_NOT, this)));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        SpanQueryAndNot that = (SpanQueryAndNot) o;
        return Objects.equals(filterFactory, that.filterFactory) && Objects.equals(include, that.include)
                && Objects.equals(exclude, that.exclude);
    }

    @Override
    public int hashCode() {
        return Objects.hash(include, exclude, filterFactory);
    }

    @Override
    public BLSpanWeight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        if (!exclude.isEmpty())
            throw new BlackLabRuntimeException("Query should've been rewritten! (exclude clauses left)");

        List<BLSpanWeight> weights = new ArrayList<>();
        for (BLSpanQuery clause : include) {
            weights.add(clause.createWeight(searcher, scoreMode, boost));
        }
        Map<Term, TermStates> contexts = scoreMode.needsScores() ? getTermStates(weights.toArray(new SpanWeight[0])) : null;
        return new SpanWeightAnd(weights, searcher, contexts, boost);
    }

    class SpanWeightAnd extends BLSpanWeight {

        final List<BLSpanWeight> weights;

        public SpanWeightAnd(List<BLSpanWeight> weights, IndexSearcher searcher, Map<Term, TermStates> terms, float boost)
                throws IOException {
            super(SpanQueryAndNot.this, searcher, terms, boost);
            this.weights = weights;
        }

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
            for (final SegmentCacheable w : weights) {
                if (!w.isCacheable(ctx))
                    return false;
            }
            return true;
        }

        @Override
        public void extractTermStates(Map<Term, TermStates> contexts) {
            for (BLSpanWeight weight : weights) {
                weight.extractTermStates(contexts);
            }
        }

        @Override
        public BLSpans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
            List<BLSpans> spans = new ArrayList<>();
            for (BLSpanWeight w : weights) {
                BLSpans s = w.getSpans(context, requiredPostings);
                if (s == null)
                    return null; // if no hits in one of the clauses, no hits in AND query
                spans.add(s);
            }

            if (filterFactory != null) {
                return new SpansAndFiltered(spans, filterFactory);
            } else {
                BLSpans combined = BLSpans.ensureSortedUnique(spans.get(0));
                for (int i = 1; i < weights.size(); i++) {
                    BLSpans clause = BLSpans.ensureSortedUnique(spans.get(i));
                    if (combined.guarantees().hitsHaveUniqueStartEnd() && clause.guarantees()
                            .hitsHaveUniqueStartEnd()) {
                        // No duplicate start/end (with different match info); use the faster version.
                        combined = new SpansAndSimple(combined, clause);
                    } else {
                        // We need to use the slower version that takes duplicate spans into account and produces all
                        // combinations.
                        combined = new SpansAnd(combined, clause);
                    }
                }
                return combined;
            }
        }
    }

    @Override
    public String toString(String field) {
        String type = filterFactory == null ?
                "AND" :
                "AND_" + filterFactory.name() + ", ";
        if (exclude.isEmpty())
            return type + "(" + clausesToString(field, include) + ")";
        return type + "(" + clausesToString(field, include) + ", " + clausesToString(field, exclude, "!") + ")";
    }

    @Override
    public String getField() {
        if (!include.isEmpty())
            return include.get(0).getField();
        if (!exclude.isEmpty())
            return exclude.get(0).getField();
        throw new BlackLabRuntimeException("Query has no clauses");
    }

    @Override
    public String getRealField() {
        if (!include.isEmpty())
            return include.get(0).getRealField();
        if (!exclude.isEmpty())
            return exclude.get(0).getRealField();
        throw new BlackLabRuntimeException("Query has no clauses");
    }

    @Override
    public Nfa getNfa(ForwardIndexAccessor fiAccessor, int direction) {
        if (!exclude.isEmpty())
            throw new BlackLabRuntimeException("Query should've been rewritten! (exclude clauses left)");
        List<NfaState> nfaClauses = new ArrayList<>();
        for (BLSpanQuery clause : include) {
            Nfa nfa = clause.getNfa(fiAccessor, direction);
            nfaClauses.add(nfa.getStartingState());
        }
        NfaState andAcyclic = NfaState.and(false, nfaClauses);
        return new Nfa(andAcyclic, List.of(andAcyclic));
    }

    @Override
    public boolean canMakeNfa() {
        if (!exclude.isEmpty())
            return false;
        for (BLSpanQuery clause : include) {
            if (!clause.canMakeNfa())
                return false;
        }
        return true;
    }

    @Override
    public long reverseMatchingCost(IndexReader reader) {
        // Excludes should have been rewritten, so we only look at includes.
        // We return the least frequent clause since we can skip over the more
        // frequent ones, or match them using the forward index.
        long cost = Integer.MAX_VALUE;
        for (BLSpanQuery clause : include) {
            cost = Math.min(cost, clause.reverseMatchingCost(reader));
        }
        return cost;
    }

    @Override
    public int forwardMatchingCost() {
        // Add the costs of our clauses.
        int cost = 1;
        for (BLSpanQuery cl : include) {
            cost += cl.forwardMatchingCost();
        }
        return cost * 2 / 3; // we expect to be able to short-circuit AND in a significant number of cases
    }

    @Override
    public void setQueryInfo(QueryInfo queryInfo) {
        super.setQueryInfo(queryInfo);
        for (BLSpanQuery cl: include) {
            cl.setQueryInfo(queryInfo);
        }
        for (BLSpanQuery cl: exclude) {
            cl.setQueryInfo(queryInfo);
        }
    }
}
