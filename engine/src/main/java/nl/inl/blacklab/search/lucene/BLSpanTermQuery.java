package nl.inl.blacklab.search.lucene;

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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexReaderContext;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.queries.spans.SpanTermQuery;
import org.apache.lucene.queries.spans.SpanTermQuery.SpanTermWeight;
import org.apache.lucene.queries.spans.TermSpans;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.Nfa;
import nl.inl.blacklab.search.fimatch.NfaState;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * BL-specific subclass of SpanTermQuery that changes what getField() returns
 * (the annotated field name instead of the full Lucene field name) in order to be
 * able to combine queries in different Lucene fields using AND and OR. Also
 * makes sure the SpanWeight returned by createWeight() produces a BLSpans, not
 * a regular Spans.
 */
public class BLSpanTermQuery extends BLSpanQuery {

    public static final int FIXED_FORWARD_MATCHING_COST = 200;

    public static BLSpanTermQuery from(QueryInfo queryInfo, SpanTermQuery q) {
        return new BLSpanTermQuery(queryInfo, q);
    }

    public static SpanGuarantees createGuarantees() {
        return SpanGuarantees.TERM;
    }

    final SpanTermQuery query;

    private final TermStates termStates;

    private boolean hasForwardIndex = false;

    private boolean hasForwardIndexDetermined = false;

    /**
     * Construct a SpanTermQuery matching the named term's spans.
     *
     * @param term term to search
     */
    public BLSpanTermQuery(QueryInfo queryInfo, Term term) {
        super(queryInfo);
        query = new SpanTermQuery(term);
        termStates = null;
        this.guarantees = createGuarantees();
    }

    BLSpanTermQuery(QueryInfo queryInfo, SpanTermQuery termQuery) {
        this(queryInfo, termQuery.getTerm());
    }

    /**
     * Expert: Construct a SpanTermQuery matching the named term's spans, using the
     * provided TermStates.
     *
     * @param term term to search
     * @param termStates TermStates to use to search the term
     */
    public BLSpanTermQuery(Term term, TermStates termStates, QueryInfo queryInfo) {
        super(queryInfo);
        query = new SpanTermQuery(term, termStates);
        this.termStates = termStates;
    }

    @Override
    public String getRealField() {
        return query.getTerm().field();
    }

    public Term getTerm() {
        return query.getTerm();
    }

    @Override
    public BLSpanWeight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        final TermStates context;
        final IndexReaderContext topContext = searcher.getTopReaderContext();
        if (termStates == null || !termStates.wasBuiltFor(topContext)) {
        	boolean needsStats=true;
            context = TermStates.build(searcher, query.getTerm(),needsStats);
        } else {
            context = termStates;
        }
        Map<Term, TermStates> contexts = scoreMode.needsScores() ? Collections.singletonMap(query.getTerm(), context) : null;
        final SpanTermWeight weight = query.new SpanTermWeight(context, searcher, contexts, boost);
        return new BLSpanWeight(this, searcher, contexts, boost) {
            @Override
            public void extractTermStates(Map<Term, TermStates> contexts) {
                weight.extractTermStates(contexts);
            }

            @Override
            public BLSpans getSpans(LeafReaderContext ctx, Postings requiredPostings) throws IOException {
                TermSpans spans = (TermSpans)weight.getSpans(ctx, requiredPostings);
                return spans == null ? null : BLSpans.wrapTermSpans(spans);
            }

            @Override
            public boolean isCacheable(LeafReaderContext ctx) {
                return weight.isCacheable(ctx);
            }
        };
    }

    @Override
    public void visit(QueryVisitor visitor) {
        if (visitor.acceptField(getTerm().field())) {
            visitor.consumeTerms(this, getTerm());
        }
    }

    @Override
    public String toString(String field) {
        return "TERM(" + query + ")";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((query == null) ? 0 : query.hashCode());
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
        BLSpanTermQuery other = (BLSpanTermQuery) obj;
        if (query == null) {
            if (other.query != null)
                return false;
        } else if (!query.equals(other.query))
            return false;
        return true;
    }

    @Override
    public Nfa getNfa(ForwardIndexAccessor fiAccessor, int direction) {
        Term term = query.getTerm();
        String propertyValue = term.text();
        NfaState state = NfaState.token(term.field(), propertyValue, null);
        return new Nfa(state, List.of(state));
    }

    @Override
    public boolean canMakeNfa() {
        if (!hasForwardIndexDetermined) {
            // Does our annotation have a forward index?
            String[] comp = AnnotatedFieldNameUtil.getNameComponents(query.getTerm().field());
            String fieldName = comp[0];
            String annotationName = comp[1];
            hasForwardIndex = queryInfo.index().annotatedField(fieldName).annotation(annotationName).hasForwardIndex();
            hasForwardIndexDetermined = true;
        }
        return hasForwardIndex;
    }

    @Override
    public long reverseMatchingCost(IndexReader reader) {
        try {
            return reader.totalTermFreq(query.getTerm());
        } catch (IOException e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    @Override
    public int forwardMatchingCost() {
        return FIXED_FORWARD_MATCHING_COST;
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) {
        return this;
    }

}
