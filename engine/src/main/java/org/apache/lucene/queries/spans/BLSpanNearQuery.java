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
package org.apache.lucene.queries.spans;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.index.Terms;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Weight;

import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.BLSpanWeight;
import nl.inl.blacklab.search.lucene.BLSpans;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.lucene.SpanGuarantees;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * Matches spans which are near one another. One can specify <i>slop</i>, the maximum number of
 * intervening unmatched positions, as well as whether matches are required to be in-order.
 */
public class BLSpanNearQuery extends BLSpanQuery implements Cloneable {

    /**
     * A builder for SpanNearQueries
     */
    public static class Builder {
        private final boolean ordered;
        private final String field;
        private final List<BLSpanQuery> clauses = new LinkedList<>();
        private int slop;

        /**
         * Construct a new builder
         *
         * @param field   the field to search in
         * @param ordered whether or not clauses must be in-order to match
         */
        public Builder(String field, boolean ordered) {
            this.field = field;
            this.ordered = ordered;
        }

        /**
         * Add a new clause
         */
        public Builder addClause(BLSpanQuery clause) {
            if (!Objects.equals(clause.getField(), field))
                throw new IllegalArgumentException(
                        "Cannot add clause " + clause + " to SpanNearQuery for field " + field);
            this.clauses.add(clause);
            return this;
        }

        /**
         * Add a gap after the previous clause of a defined width
         */
        public Builder addGap(int width) {
            if (!ordered)
                throw new IllegalArgumentException("Gaps can only be added to ordered near queries");
            this.clauses.add(new SpanGapQuery(clauses.get(0).queryInfo(), field, width));
            return this;
        }

        /**
         * Set the slop for this query
         */
        public Builder setSlop(int slop) {
            this.slop = slop;
            return this;
        }

        /**
         * Build the query
         */
        public BLSpanNearQuery build() {
            return new BLSpanNearQuery(clauses.toArray(new BLSpanQuery[clauses.size()]), slop, ordered);
        }
    }

    /**
     * Returns a {@link Builder} for an ordered query on a particular field
     */
    public static Builder newOrderedNearQuery(String field) {
        return new Builder(field, true);
    }

    /**
     * Returns a {@link Builder} for an unordered query on a particular field
     */
    public static Builder newUnorderedNearQuery(String field) {
        return new Builder(field, false);
    }

    protected List<BLSpanQuery> clauses;
    protected int slop;
    protected boolean inOrder;

    protected String field;

    /**
     * Construct a SpanNearQuery. Matches spans matching a span from each clause, with up to <code>
     * slop</code> total unmatched positions between them. <br>
     * When <code>inOrder</code> is true, the spans from each clause must be in the same order as in
     * <code>clauses</code> and must be non-overlapping. <br>
     * When <code>inOrder</code> is false, the spans from each clause need not be ordered and may
     * overlap.
     *
     * @param clausesIn the clauses to find near each other, in the same field, at least 2.
     * @param slop      The slop value
     * @param inOrder   true if order is important
     */
    public BLSpanNearQuery(BLSpanQuery[] clausesIn, int slop, boolean inOrder) {
        super(clausesIn[0].queryInfo());
        this.clauses = new ArrayList<>(clausesIn.length);
        for (BLSpanQuery clause: clausesIn) {
            if (this.field == null) { // check field
                this.field = clause.getField();
            } else if (clause.getField() != null && !clause.getField().equals(field)) {
                throw new IllegalArgumentException("Clauses must have same field.");
            }
            this.clauses.add(clause);
        }
        this.slop = slop;
        this.inOrder = inOrder;
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        return new BLSpanNearQuery(clauses.toArray(new BLSpanQuery[0]), slop, inOrder);
    }

    /**
     * Return the clauses whose spans are matched.
     */
    public BLSpanQuery[] getClauses() {
        return clauses.toArray(new BLSpanQuery[clauses.size()]);
    }

    /**
     * Return the maximum number of intervening unmatched positions permitted.
     */
    public int getSlop() {
        return slop;
    }

    /**
     * Return true if matches are required to be in-order.
     */
    public boolean isInOrder() {
        return inOrder;
    }

    @Override
    public String getField() {
        return field;
    }

    @Override
    public String getRealField() {
        return clauses.get(0).getRealField();
    }

    @Override
    public String toString(String field) {
        StringBuilder buffer = new StringBuilder();
        buffer.append("spanNear([");
        Iterator<BLSpanQuery> i = clauses.iterator();
        while (i.hasNext()) {
            BLSpanQuery clause = i.next();
            buffer.append(clause.toString(field));
            if (i.hasNext()) {
                buffer.append(", ");
            }
        }
        buffer.append("], ");
        buffer.append(slop);
        buffer.append(", ");
        buffer.append(inOrder);
        buffer.append(")");
        return buffer.toString();
    }

    public static Map<Term, TermStates> getTermStates(List<BLSpanWeight> weights) {
        Map<Term, TermStates> terms = new TreeMap<>();
        for(SpanWeight w : weights) {
            w.extractTermStates(terms);
        }
        return terms;
    }

    @Override
    public BLSpanWeight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        List<BLSpanWeight> subWeights = new ArrayList<>();
        for (BLSpanQuery q: clauses) {
            subWeights.add(q.createWeight(searcher, scoreMode, boost));
        }
        return new SpanNearWeight(subWeights, searcher, scoreMode.needsScores() ? getTermStates(subWeights) : null,
                boost);
    }

    @Override
    public long reverseMatchingCost(IndexReader reader) {
        return 0;
    }

    @Override
    public int forwardMatchingCost() {
        return Integer.MAX_VALUE;
    }

    /**
     * Creates SpanNearQuery scorer instances
     *
     * @lucene.internal
     */
    public class SpanNearWeight extends BLSpanWeight {

        final List<BLSpanWeight> subWeights;

        public SpanNearWeight(List<BLSpanWeight> subWeights, IndexSearcher searcher, Map<Term, TermStates> terms,
                float boost) throws IOException {
            super(BLSpanNearQuery.this, searcher, terms, boost);
            this.subWeights = subWeights;
        }

        @Override
        public void extractTermStates(Map<Term, TermStates> contexts) {
            for (BLSpanWeight w: subWeights) {
                w.extractTermStates(contexts);
            }
        }

        @Override
        public BLSpans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {

            Terms terms = context.reader().terms(field);
            if (terms == null) {
                return null; // field does not exist
            }

            ArrayList<BLSpans> subSpans = new ArrayList<>(clauses.size());
            for (BLSpanWeight w: subWeights) {
                BLSpans subSpan = w.getSpans(context, requiredPostings);
                if (subSpan != null) {
                    subSpans.add(subSpan);
                } else {
                    return null; // all required
                }
            }

            // all NearSpans require at least two subSpans
            return (!inOrder) ? new BLNearSpansUnordered(slop, subSpans) : new BLNearSpansOrdered(slop, subSpans);
        }

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
            for (Weight w: subWeights) {
                if (w.isCacheable(ctx) == false)
                    return false;
            }
            return true;
        }
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        boolean actuallyRewritten = false;
        List<BLSpanQuery> rewrittenClauses = new ArrayList<>();
        for (int i = 0; i < clauses.size(); i++) {
            BLSpanQuery c = clauses.get(i);
            BLSpanQuery query = (BLSpanQuery) c.rewrite(reader);
            actuallyRewritten |= query != c;
            rewrittenClauses.add(query);
        }
        if (actuallyRewritten) {
            try {
                BLSpanNearQuery rewritten = (BLSpanNearQuery) clone();
                rewritten.clauses = rewrittenClauses;
                return rewritten;
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }
        return this;
    }

    @Override
    public void visit(QueryVisitor visitor) {
        if (visitor.acceptField(getField()) == false) {
            return;
        }
        QueryVisitor v = visitor.getSubVisitor(BooleanClause.Occur.MUST, this);
        for (BLSpanQuery clause: clauses) {
            clause.visit(v);
        }
    }

    @Override
    public boolean equals(Object other) {
        return sameClassAs(other) && equalsTo(getClass().cast(other));
    }

    private boolean equalsTo(BLSpanNearQuery other) {
        return inOrder == other.inOrder && slop == other.slop && clauses.equals(other.clauses);
    }

    @Override
    public int hashCode() {
        int result = classHash();
        result ^= clauses.hashCode();
        result += slop;
        int fac = 1 + (inOrder ? 8 : 4);
        return fac * result;
    }

    private static class SpanGapQuery extends BLSpanQuery {

        private final String field;
        private final int width;

        public SpanGapQuery(QueryInfo queryInfo, String field, int width) {
            super(queryInfo);
            this.field = field;
            this.width = width;
        }

        @Override
        public String getField() {
            return field;
        }

        @Override
        public String getRealField() {
            return field;
        }

        @Override
        public void visit(QueryVisitor visitor) {
            visitor.visitLeaf(this);
        }

        @Override
        public String toString(String field) {
            return "SpanGap(" + field + ":" + width + ")";
        }

        @Override
        public BLSpanQuery rewrite(IndexReader reader) throws IOException {
            return this;
        }

        @Override
        public int forwardMatchingCost() {
            return Integer.MAX_VALUE;
        }

        @Override
        public long reverseMatchingCost(IndexReader reader) {
            return 0;
        }

        @Override
        public BLSpanWeight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
            return new SpanGapWeight(searcher, boost);
        }

        private class SpanGapWeight extends BLSpanWeight {

            SpanGapWeight(IndexSearcher searcher, float boost) throws IOException {
                super(SpanGapQuery.this, searcher, null, boost);
            }

            @Override
            public void extractTermStates(Map<Term, TermStates> contexts) {
                // no terms to extract
            }

            @Override
            public BLSpans getSpans(LeafReaderContext ctx, Postings requiredPostings) throws IOException {
                return new GapSpans(width);
            }

            @Override
            public boolean isCacheable(LeafReaderContext ctx) {
                return true;
            }
        }

        @Override
        public boolean equals(Object other) {
            return sameClassAs(other) && equalsTo(getClass().cast(other));
        }

        private boolean equalsTo(SpanGapQuery other) {
            return width == other.width && field.equals(other.field);
        }

        @Override
        public int hashCode() {
            int result = classHash();
            result -= 7 * width;
            return result * 15 - field.hashCode();
        }
    }

    static class GapSpans extends BLSpans {

        int doc = -1;
        int pos = -1;
        final int width;

        GapSpans(int width) {
            super(SpanGuarantees.NONE/*@@@ TODO */);
            this.width = width;
        }

        @Override
        public int nextStartPosition() throws IOException {
            return ++pos;
        }

        @Override
        protected void passHitQueryContextToClauses(HitQueryContext context) {
            // @@@ TODO
        }

        @Override
        public void getMatchInfo(MatchInfo[] matchInfo) {
            // @@@ TODO
        }

        @Override
        public boolean hasMatchInfo() {
            // @@@ TODO
            return false;
        }

        public int skipToPosition(int position) throws IOException {
            return pos = position;
        }

        @Override
        public int startPosition() {
            return pos;
        }

        @Override
        public int endPosition() {
            return pos + width;
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public void collect(SpanCollector collector) throws IOException {
            // @@@ TODO
        }

        @Override
        public int docID() {
            return doc;
        }

        @Override
        public int nextDoc() throws IOException {
            pos = -1;
            return ++doc;
        }

        @Override
        public int advance(int target) throws IOException {
            pos = -1;
            return doc = target;
        }

        @Override
        public long cost() {
            return 0;
        }

        @Override
        public RelationInfo getRelationInfo() {
            // @@@ TODO
            return null;
        }

        @Override
        public float positionsCost() {
            return 0;
        }
    }
}
