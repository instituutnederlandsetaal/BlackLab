package org.apache.lucene.queries.spans;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.BLSpanTermQuery;
import nl.inl.blacklab.search.lucene.BLSpanWeight;
import nl.inl.blacklab.search.lucene.BLSpans;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.lucene.SpanGuarantees;
import nl.inl.blacklab.search.lucene.SpanGuaranteesAdapter;
import nl.inl.blacklab.search.lucene.SpanQueryAnd;
import nl.inl.blacklab.search.lucene.SpanQueryNoHits;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.search.Weight;
import org.apache.lucene.util.PriorityQueue;

import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.Nfa;
import nl.inl.blacklab.search.fimatch.NfaState;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.BLSpanTermQuery;
import nl.inl.blacklab.search.lucene.BLSpanWeight;
import nl.inl.blacklab.search.lucene.BLSpans;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.lucene.SpanGuarantees;
import nl.inl.blacklab.search.lucene.SpanGuaranteesAdapter;
import nl.inl.blacklab.search.lucene.SpanQueryAnd;
import nl.inl.blacklab.search.lucene.SpanQueryNoHits;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * Matches the union of its clauses.
 */
public final class BLSpanOrQuery extends BLSpanQuery {

    public static SpanGuarantees createGuarantees(List<SpanGuarantees> clauses) {
        return new SpanGuaranteesAdapter() {

            /**
             * If we know all our hits have the same length, this will be that length. If
             * not, or if we don't know, this will be -1.
             */
            int fixedHitLength = -1;

            @Override
            public boolean hitsAllSameLength() {
                if (fixedHitLength >= 0)
                    return true;
                if (clauses.isEmpty())
                    return true;
                int l = clauses.get(0).hitsLengthMin();
                for (SpanGuarantees clause: clauses) {
                    if (!clause.hitsAllSameLength() || clause.hitsLengthMin() != l)
                        return false;
                }
                fixedHitLength = l; // save for next time
                return true;
            }

            @Override
            public int hitsLengthMin() {
                if (fixedHitLength >= 0)
                    return fixedHitLength;
                int n = Integer.MAX_VALUE;
                for (SpanGuarantees cl: clauses) {
                    n = Math.min(n, cl.hitsLengthMin());
                }
                return n == Integer.MAX_VALUE ? 0 : n;
            }

            @Override
            public int hitsLengthMax() {
                if (fixedHitLength >= 0)
                    return fixedHitLength;
                int n = 0;
                for (SpanGuarantees cl : clauses) {
                    int l = cl.hitsLengthMax();
                    n = Math.max(n, l);
                    if (n == Integer.MAX_VALUE)
                        return n; // infinite
                }
                return n;
            }

            @Override
            public boolean hitsEndPointSorted() {
                return hitsAllSameLength();
            }

            @Override
            public boolean hitsStartPointSorted() {
                // Our way of merging guarantees this
                return true;
            }

            @Override
            public boolean hitsHaveUniqueStart() {
                // Cannot guarantee because we're merging from different sources.
                return false;
            }

            @Override
            public boolean hitsHaveUniqueEnd() {
                // Cannot guarantee because we're merging from different sources.
                return false;
            }

            @Override
            public boolean hitsHaveUniqueStartEnd() {
                // Cannot guarantee because we're merging from different sources.
                return false;
            }
        };
    }

    final SpanOrQuery inner;

    String field;

    private String luceneField;

    /**
     * If we know all our hits have the same length, this will be that length. If
     * not, or if we don't know, this will be -1.
     */
    private int fixedHitLength = -1;

    /** Are all our clauses simple term queries? Yes if true, not sure if false. */
    private boolean clausesAreSimpleTermsInSameAnnotation = false;

    /**
     * Construct a SpanOrQuery merging the provided clauses. All clauses must have
     * the same field.
     *
     * @param clauses clauses to OR together
     */
    public BLSpanOrQuery(BLSpanQuery... clauses) {
        super(clauses.length > 0 && clauses[0] != null ? clauses[0].queryInfo() : null);
        if (clauses.length == 0)
            throw new IllegalArgumentException("Can't create SpanOrQuery without clauses");
        inner = new SpanOrQuery(clauses);
        this.field = inner.getField();
        this.luceneField = clauses.length > 0 ? clauses[0].getRealField() : field;

        List<SpanGuarantees> clauseGuarantees = SpanGuarantees.from(clauses);
        this.guarantees = createGuarantees(clauseGuarantees);
    }

    public static BLSpanQuery from(QueryInfo queryInfo, SpanOrQuery in) {
        SpanQuery[] clauses = in.getClauses();
        if (clauses.length == 0)
            return new SpanQueryNoHits(queryInfo, queryInfo.field().mainAnnotation().mainSensitivity().luceneField());
        BLSpanQuery[] blClauses = new BLSpanQuery[clauses.length];
        String field = null;
        boolean allInSameField = true;
        boolean allSimpleTerms = true;
        for (int i = 0; i < clauses.length; i++) {
            if (allSimpleTerms && allInSameField) {
                if (!(clauses[i] instanceof SpanTermQuery || clauses[i] instanceof BLSpanTermQuery))
                    allSimpleTerms = false;
                else {
                    if (field == null)
                        field = clauses[i].getField();
                    else {
                        if (!field.equals(clauses[i].getField()))
                            allInSameField = false;
                    }
                }
            }
            blClauses[i] = BLSpanQuery.wrap(queryInfo, clauses[i]);
        }
        BLSpanOrQuery out = new BLSpanOrQuery(blClauses);
        if (allSimpleTerms && allInSameField)
            out.setClausesAreSimpleTermsInSameAnnotation(true);
        return out;
    }

    /**
     * Return the clauses whose spans are matched.
     *
     * @return the clauses
     */
    public SpanQuery[] getClauses() {
        return inner.getClauses();
    }

    /**
     * Explicitly set the field for this query. Required because some queries
     * rewrite to 0-clause or queries, and we need to be able to call
     * get[Real]Field() later.
     *
     * @param luceneField the Lucene field for this query (what getRealField()
     *            returns)
     */
    public void setField(String luceneField) {
        this.luceneField = luceneField;
        this.field = AnnotatedFieldNameUtil.getBaseName(luceneField);
    }

    @Override
    public String getField() {
        return field;
    }

    @Override
    public String getRealField() {
        return luceneField;
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {

        // Flatten nested OR queries.
        // This doesn't change the query because the OR operator is associative.
        boolean anyRewritten = false;
        boolean onlyNotClauses = true;
        boolean allClausesSingleToken = true;
        List<BLSpanQuery> rewrittenCl = new ArrayList<>();
        for (SpanQuery ch : inner.getClauses()) {
            BLSpanQuery child = (BLSpanQuery) ch;
            BLSpanQuery rewritten = child.rewrite(reader);
            if (rewritten instanceof BLSpanOrQuery) {
                // Flatten.
                // Child OR operation we want to flatten into this OR operation.
                // Replace the child, incorporating its children into this OR operation.
                for (SpanQuery cl : ((BLSpanOrQuery) rewritten).getClauses()) {
                    BLSpanQuery clause = (BLSpanQuery) cl;
                    SpanGuarantees g = clause.guarantees();
                    if (!g.hitsAllSameLength() || g.hitsLengthMax() != 1)
                        allClausesSingleToken = false;
                    if (!g.isSingleTokenNot())
                        onlyNotClauses = false;
                    rewrittenCl.add(clause);
                }
                anyRewritten = true;
            } else {
                SpanGuarantees g = rewritten.guarantees();
                if (!g.hitsAllSameLength() || g.hitsLengthMax() != 1)
                    allClausesSingleToken = false;
                if (!g.isSingleTokenNot())
                    onlyNotClauses = false;
                // Just add it.
                rewrittenCl.add(rewritten);
                if (rewritten != child)
                    anyRewritten = true;
            }
        }

        // Rewrites OR queries containing only NOT children into "NAND" queries.
        // (only possible if all OR queries have token length 1!)
        // This saves on NOT queries and helps us isolate problematic subclauses which
        // we can then rewrite to more efficient NOTCONTAINING clauses.
        if (!rewrittenCl.isEmpty() && onlyNotClauses && allClausesSingleToken) {
            // At least one clause starts with NOT.
            // Node should be rewritten to AND. Invert all clauses.
            for (int i = 0; i < rewrittenCl.size(); i++) {
                rewrittenCl.set(i, rewrittenCl.get(i).inverted());
            }
            // Note extra rewrite at the end to make sure AND NOT structure is correctly built.
            if (rewrittenCl.size() == 1)
                return rewrittenCl.get(0).inverted();

            return (new SpanQueryAnd(rewrittenCl).inverted()).rewrite(reader);
        }

        if (anyRewritten) {
            // Some clauses were rewritten.
            if (rewrittenCl.size() == 1)
                return rewrittenCl.get(0);
            BLSpanOrQuery result = new BLSpanOrQuery(rewrittenCl.toArray(new BLSpanQuery[0]));
            result.setHitsAreFixedLength(fixedHitLength);
            result.setClausesAreSimpleTermsInSameAnnotation(clausesAreSimpleTermsInSameAnnotation);
            result.setField(getRealField());
            return result;
        }

        // Node need not be rewritten; return as-is
        if (inner.getClauses().length == 1) {
            return BLSpanQuery.wrap(queryInfo, inner.getClauses()[0]);
        }
        return this;
    }

    @Override
    public void visit(QueryVisitor visitor) {
        if (visitor.acceptField(getField()) == false) {
            return;
        }
        QueryVisitor v = visitor.getSubVisitor(BooleanClause.Occur.SHOULD, this);
        for (SpanQuery q : getClauses()) {
            q.visit(v);
        }
    }

    @Override
    public boolean matchesEmptySequence() {
        if (fixedHitLength == 0)
            return true;
        if (fixedHitLength > 0)
            return false;

        for (SpanQuery cl : getClauses()) {
            BLSpanQuery clause = (BLSpanQuery) cl;
            if (clause.matchesEmptySequence())
                return true;
        }
        return false;
    }

    @Override
    public BLSpanQuery noEmpty() {
        if (!matchesEmptySequence())
            return this;
        List<BLSpanQuery> newCl = new ArrayList<>();
        for (SpanQuery cl : inner.getClauses()) {
            newCl.add(((BLSpanQuery) cl).noEmpty());
        }
        BLSpanOrQuery result = new BLSpanOrQuery(newCl.toArray(new BLSpanQuery[0]));
        result.setHitsAreFixedLength(fixedHitLength);
        result.setClausesAreSimpleTermsInSameAnnotation(clausesAreSimpleTermsInSameAnnotation);
        return result;
    }

    @Override
    public String toString(String field) {
        return "OR(" + clausesToString(field, inner.getClauses()) + ")";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((inner == null) ? 0 : inner.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        BLSpanOrQuery other = (BLSpanOrQuery) obj;
        if (inner == null) {
            if (other.inner != null)
                return false;
        } else if (!inner.equals(other.inner))
            return false;
        return true;
    }

    @Override
    public BLSpanWeight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        List<BLSpanWeight> subWeights = new ArrayList<>(inner.getClauses().length);
        for (SpanQuery q: inner.getClauses()) {
            subWeights.add(((BLSpanQuery) q).createWeight(searcher, scoreMode, boost));
        }
        Map<Term, TermStates> terms = scoreMode.needsScores() ?
                getTermStates(subWeights.toArray(new SpanWeight[0])) :
                null;
        return new SpanOrWeight(searcher, terms, subWeights, boost);
    }

    /**
     * Creates SpanOrQuery scorer instances
     *
     * @lucene.internal
     */
    public class SpanOrWeight extends BLSpanWeight {

        final List<BLSpanWeight> subWeights;

        public SpanOrWeight(
                IndexSearcher searcher,
                Map<Term, TermStates> terms,
                List<BLSpanWeight> subWeights,
                float boost)
                throws IOException {
            super(BLSpanOrQuery.this, searcher, terms, boost);
            this.subWeights = subWeights;
        }

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
            for (Weight w: subWeights) {
                if (w.isCacheable(ctx) == false) return false;
            }
            return true;
        }

        @Override
        public void extractTermStates(Map<Term, TermStates> contexts) {
            for (SpanWeight w: subWeights) {
                w.extractTermStates(contexts);
            }
        }

        @Override
        public BLSpans getSpans(final LeafReaderContext context, Postings requiredPostings)
                throws IOException {

            ArrayList<Spans> subSpans = new ArrayList<>(inner.getClauses().length);

            for (SpanWeight w : subWeights) {
                Spans spans = w.getSpans(context, requiredPostings);
                if (spans != null) {
                    subSpans.add(spans);
                }
            }

            if (subSpans.size() == 0) {
                return null;
            } else if (subSpans.size() == 1) {
                //BL we need everything to be a BLSpans, or capturing (and optimizations) won't work properly
                //   that's why we bypass ScoringWrapperSpans here.
                Spans clause = subSpans.get(0);
                if (clause instanceof BLSpans)
                    return (BLSpans) clause;
                else if (clause instanceof TermSpans)
                    return BLSpans.wrapTermSpans((TermSpans)clause);
                else
                    throw new IllegalArgumentException("BLSpanOrQuery clause must be BLSpans or TermSpans");
                //return new BLSpansWrapper(new ScoringWrapperSpans(subSpans.get(0), getSimScorer(context)));
            }

            final SpanDisiPriorityQueue byDocQueue = new SpanDisiPriorityQueue(subSpans.size());
            for (Spans spans : subSpans) {
                byDocQueue.add(new SpanDisiWrapper(spans));
            }

            SpanPositionQueue byPositionQueue =
                    new SpanPositionQueue(subSpans.size()); // when empty use -1

            return new BLSpans(SpanGuarantees.NONE) {
                Spans topPositionSpans = null;

                @Override
                public int nextDoc() throws IOException {
                    assert docID() != NO_MORE_DOCS;
                    topPositionSpans = null;
                    SpanDisiWrapper topDocSpans = byDocQueue.top();
                    int currentDoc = topDocSpans.doc;
                    do {
                        topDocSpans.doc = topDocSpans.iterator.nextDoc();
                        topDocSpans = byDocQueue.updateTop();
                    } while (topDocSpans.doc == currentDoc);
                    return topDocSpans.doc;
                }

                @Override
                public int advance(int target) throws IOException {
                    assert target >= 0 && target > docID();
                    topPositionSpans = null;
                    SpanDisiWrapper topDocSpans = byDocQueue.top();
                    do {
                        topDocSpans.doc = topDocSpans.iterator.advance(target);
                        topDocSpans = byDocQueue.updateTop();
                    } while (topDocSpans.doc < target);
                    return topDocSpans.doc;
                }

                @Override
                public int docID() {
                    SpanDisiWrapper topDocSpans = byDocQueue.top();
                    return topDocSpans.doc;
                }

                @Override
                public TwoPhaseIterator asTwoPhaseIterator() {
                    float sumMatchCost = 0; // See also DisjunctionScorer.asTwoPhaseIterator()
                    long sumApproxCost = 0;

                    for (SpanDisiWrapper w : byDocQueue) {
                        if (w.twoPhaseView != null) {
                            long costWeight = (w.cost <= 1) ? 1 : w.cost;
                            sumMatchCost += w.twoPhaseView.matchCost() * costWeight;
                            sumApproxCost += costWeight;
                        }
                    }

                    if (sumApproxCost == 0) { // no sub spans supports approximations
                        computePositionsCost();
                        return null;
                    }

                    final float matchCost = sumMatchCost / sumApproxCost;

                    return new TwoPhaseIterator(new SpanDisjunctionDISIApproximation(byDocQueue)) {
                        @Override
                        public boolean matches() throws IOException {
                            return twoPhaseCurrentDocMatches();
                        }

                        @Override
                        public float matchCost() {
                            return matchCost;
                        }
                    };
                }

                float positionsCost = -1;

                void computePositionsCost() {
                    float sumPositionsCost = 0;
                    long sumCost = 0;
                    for (SpanDisiWrapper w : byDocQueue) {
                        long costWeight = (w.cost <= 1) ? 1 : w.cost;
                        sumPositionsCost += w.spans.positionsCost() * costWeight;
                        sumCost += costWeight;
                    }
                    positionsCost = sumPositionsCost / sumCost;
                }

                @Override
                public float positionsCost() {
                    // This may be called when asTwoPhaseIterator returned null,
                    // which happens when none of the sub spans supports approximations.
                    assert positionsCost > 0;
                    return positionsCost;
                }

                int lastDocTwoPhaseMatched = -1;

                boolean twoPhaseCurrentDocMatches() throws IOException {
                    assert positionedInDoc();
                    SpanDisiWrapper listAtCurrentDoc = byDocQueue.topList();
                    // remove the head of the list as long as it does not match
                    final int currentDoc = listAtCurrentDoc.doc;
                    while (listAtCurrentDoc.twoPhaseView != null) {
                        if (listAtCurrentDoc.twoPhaseView.matches()) {
                            // use this spans for positions at current doc:
                            listAtCurrentDoc.lastApproxMatchDoc = currentDoc;
                            break;
                        }
                        // do not use this spans for positions at current doc:
                        listAtCurrentDoc.lastApproxNonMatchDoc = currentDoc;
                        listAtCurrentDoc = listAtCurrentDoc.next;
                        if (listAtCurrentDoc == null) {
                            return false;
                        }
                    }
                    lastDocTwoPhaseMatched = currentDoc;
                    topPositionSpans = null;
                    return true;
                }

                void fillPositionQueue() throws IOException { // called at first nextStartPosition
                    assert byPositionQueue.size() == 0;
                    // add all matching Spans at current doc to byPositionQueue
                    SpanDisiWrapper listAtCurrentDoc = byDocQueue.topList();
                    while (listAtCurrentDoc != null) {
                        Spans spansAtDoc = listAtCurrentDoc.spans;
                        if (lastDocTwoPhaseMatched
                                == listAtCurrentDoc.doc) { // matched by DisjunctionDisiApproximation
                            if (listAtCurrentDoc.twoPhaseView != null) { // matched by approximation
                                if (listAtCurrentDoc.lastApproxNonMatchDoc
                                        == listAtCurrentDoc.doc) { // matches() returned false
                                    spansAtDoc = null;
                                } else {
                                    if (listAtCurrentDoc.lastApproxMatchDoc != listAtCurrentDoc.doc) {
                                        if (!listAtCurrentDoc.twoPhaseView.matches()) {
                                            spansAtDoc = null;
                                        }
                                    }
                                }
                            }
                        }

                        if (spansAtDoc != null) {
                            assert spansAtDoc.docID() == listAtCurrentDoc.doc;
                            assert spansAtDoc.startPosition() == -1;
                            spansAtDoc.nextStartPosition();
                            assert spansAtDoc.startPosition() != NO_MORE_POSITIONS;
                            byPositionQueue.add(spansAtDoc);
                        }
                        listAtCurrentDoc = listAtCurrentDoc.next;
                    }
                    assert byPositionQueue.size() > 0;
                }

                @Override
                public int nextStartPosition() throws IOException {
                    assert startPosition() != NO_MORE_POSITIONS;
                    if (topPositionSpans == null) {
                        byPositionQueue.clear();
                        fillPositionQueue(); // fills byPositionQueue at first position
                        topPositionSpans = byPositionQueue.top();
                    } else {
                        topPositionSpans.nextStartPosition();
                        topPositionSpans = byPositionQueue.updateTop();
                    }
                    return topPositionSpans.startPosition();
                }

                @Override
                public int startPosition() {
                    return topPositionSpans == null ? -1 : topPositionSpans.startPosition();
                }

                @Override
                public int endPosition() {
                    return topPositionSpans == null ? -1 : topPositionSpans.endPosition();
                }

                @Override
                public int width() {
                    return topPositionSpans.width();
                }

                @Override
                public void collect(SpanCollector collector) throws IOException {
                    if (topPositionSpans != null) topPositionSpans.collect(collector);
                }

                @Override
                public String toString() {
                    return "BLspanOr(" + BLSpanOrQuery.this + ")@" + docID() + ": " + startPosition() + " - "
                            + endPosition();
                }

                long cost = -1;

                @Override
                public long cost() {
                    if (cost == -1) {
                        cost = 0;
                        for (Spans spans : subSpans) {
                            cost += spans.cost();
                        }
                    }
                    return cost;
                }

                // BL

                @Override
                public void passHitQueryContextToClauses(HitQueryContext theContext) {
                    for (Spans spans : subSpans) {
                        ((BLSpans) spans).setHitQueryContext(theContext);
                    }
                }

                @Override
                public void getMatchInfo(MatchInfo[] matchInfo) {
                    if (!childClausesCaptureMatchInfo)
                        return;
                    ((BLSpans) topPositionSpans).getMatchInfo(matchInfo);
                }

                @Override
                public boolean hasMatchInfo() {
                    return subSpans.stream().anyMatch(s -> ((BLSpans) s).hasMatchInfo());
                }

                @Override
                public RelationInfo getRelationInfo() {
                    return ((BLSpans) topPositionSpans).getRelationInfo();
                }

                @Override
                public SpanGuarantees guarantees() {
                    return BLSpanOrQuery.this.guarantees();
                }
            };
        }
    }

    // BL from here on out.
    @Override
    public Nfa getNfa(ForwardIndexAccessor fiAccessor, int direction) {
        // See if this is really just an expanded wildcard/regex query, and if so,
        // rewrite it to a NfaStateToken instead.
        Set<String> terms = new HashSet<>();
        boolean canBeTokenState = getNfaTokenStateTerms(terms);
        if (canBeTokenState) {
            // Yep. Rewrite to a large NfaStateToken.
            NfaState tokenState = NfaState.token(luceneField, terms, null);
            return new Nfa(tokenState, List.of(tokenState));
        }

        List<NfaState> states = new ArrayList<>();
//		List<NfaState> dangling = new ArrayList<>();
        for (SpanQuery cl : getClauses()) {
            BLSpanQuery clause = (BLSpanQuery) cl;
            Nfa frag = clause.getNfa(fiAccessor, direction);
            states.add(frag.getStartingState());
//			dangling.addAll(frag.getDanglingArrows());
        }
        NfaState orAcyclic = NfaState.or(false, states, guarantees().hitsAllSameLength());
        return new Nfa(orAcyclic, List.of(orAcyclic));
    }

    // BL
    /**
     * Checks if this OR node could be converted into a single NFA token state with
     * a list of terms, and collects the terms.
     *
     * @param terms terms for the NFA token state, if possible (or null if we're not
     *            interested in the terms)
     * @return true if this node can be converted into a single NFA token state
     */
    protected boolean getNfaTokenStateTerms(Set<String> terms) {
        boolean canBeTokenState = false;
        if (guarantees().hitsAllSameLength() && guarantees().hitsLengthMax() == 1) {
            canBeTokenState = true;
            if (terms == null && clausesAreSimpleTermsInSameAnnotation) {
                // We know all our clauses are simple terms, and we
                // don't care about which terms at this point. Just return true.
                return true;
            }
            String luceneField = null;
            for (SpanQuery cl : getClauses()) {
                if (!clausesAreSimpleTermsInSameAnnotation && !(cl instanceof BLSpanTermQuery)) {
                    // Not all simple term queries. Can't rewrite to token state.
                    canBeTokenState = false;
                    break;
                }
                BLSpanTermQuery blcl = (BLSpanTermQuery) cl;
                if (!clausesAreSimpleTermsInSameAnnotation) {
                    // We don't know if all our clauses are in the same annotation. Check.
                    if (luceneField == null) {
                        luceneField = blcl.getRealField();
                    } else if (!luceneField.equals(blcl.getRealField())) {
                        // Not all in the same annotation. Can't rewrite to token state.
                        canBeTokenState = false;
                        break;
                    }
                }
                if (terms != null)
                    terms.add(blcl.getTerm().text());
            }
        }
        if (canBeTokenState)
            clausesAreSimpleTermsInSameAnnotation = true; // save this result for next time
        return canBeTokenState;
    }

    // BL
    @Override
    public boolean canMakeNfa() {
        if (clausesAreSimpleTermsInSameAnnotation)
            return true;
        for (SpanQuery cl : getClauses()) {
            BLSpanQuery clause = (BLSpanQuery) cl;
            if (!clause.canMakeNfa())
                return false;
        }
        return true;
    }

    @Override
    public long reverseMatchingCost(IndexReader reader) {
        // Add the costs of our clauses, since we won't
        // be able to skip any hits.
        SpanQuery[] clauses = getClauses();
        if (clauses.length == 0)
            return 0;
        int cost = 0;
        int skip = getCostCalculationSkip(clauses.length);
        int clausesCalculated = 0;
        for (int i = 0; i < clauses.length; i++) {
            BLSpanQuery clause = (BLSpanQuery) clauses[i];
            cost += clause.reverseMatchingCost(reader);
            clausesCalculated++;
            i += skip;
        }
        return (long) cost * clauses.length / clausesCalculated;
    }

    /**
     * For great numbers of clauses, approximate by only calculating cost of some
     * terms
     *
     * @param n number of clauses
     * @return number of clauses to skip after every calculation
     */
    protected static int getCostCalculationSkip(int n) {
        return n >= 30 ? (n - 30) / 30 : 0;
    }

    @Override
    public int forwardMatchingCost() {
        SpanQuery[] clauses = getClauses();
        if (clausesAreSimpleTermsInSameAnnotation) {
            return clauses.length * BLSpanTermQuery.FIXED_FORWARD_MATCHING_COST;
        }
        boolean producesSingleState = getNfaTokenStateTerms(null);
        if (!producesSingleState) {
            // Add the costs of our clauses.
            if (clauses.length == 0)
                return 0;
            int cost = 1;
            int skip = getCostCalculationSkip(clauses.length);
            int clausesCalculated = 0;
            for (int i = 0; i < clauses.length; i++) {
                BLSpanQuery clause = (BLSpanQuery) clauses[i];
                cost += clause.forwardMatchingCost();
                clausesCalculated++;
                i += skip;
            }
            return cost * clauses.length / clausesCalculated;
        }
        return 1;
    }

    public void setHitsAreFixedLength(int i) {
        fixedHitLength = i;
    }

    public void setClausesAreSimpleTermsInSameAnnotation(boolean b) {
        clausesAreSimpleTermsInSameAnnotation = b;
    }
}
