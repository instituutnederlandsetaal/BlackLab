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
import java.util.Arrays;
import java.util.List;

import org.apache.lucene.search.ConjunctionUtils;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.TwoPhaseIterator;

import nl.inl.blacklab.search.lucene.BLSpans;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.lucene.SpanGuarantees;

import nl.inl.blacklab.search.lucene.BLSpans;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.lucene.SpanGuarantees;

/**
 * Common super class for multiple sub spans required in a document.
 * <p>
 * (adapted directly from Lucene)
 */
public abstract class BLConjunctionSpans extends BLSpans {
    protected final BLSpans[] subSpans; // in query order
    protected final DocIdSetIterator conjunction; // use to move to next doc with all clauses
    protected boolean atFirstInCurrentDoc; // a first start position is available in current doc for nextStartPosition
    protected boolean oneExhaustedInCurrentDoc; // one subspans exhausted in current doc

    public BLConjunctionSpans(List<BLSpans> subSpans, SpanGuarantees guarantees) {
        super(guarantees);
        if (subSpans.size() < 2) {
            throw new IllegalArgumentException("Less than 2 subSpans.size():" + subSpans.size());
        }
        this.subSpans = subSpans.toArray(new BLSpans[0]);
        this.conjunction = intersectSpans(subSpans);
        this.atFirstInCurrentDoc = true; // ensure for doc -1 that start/end positions are -1
    }

    // Copied from ConjunctionSpans
    private static DocIdSetIterator intersectSpans(List<BLSpans> spanList) {
        if (spanList.size() < 2) {
            throw new IllegalArgumentException("Cannot make a ConjunctionDISI of less than 2 iterators");
        }
        final List<DocIdSetIterator> allIterators = new ArrayList<>();
        final List<TwoPhaseIterator> twoPhaseIterators = new ArrayList<>();
        for (Spans spans : spanList) {
            addSpans(spans, allIterators, twoPhaseIterators);
        }

        return ConjunctionUtils.createConjunction(allIterators, twoPhaseIterators);
    }

    // Copied from ConjunctionSpans
    private static void addSpans(
            Spans spans, List<DocIdSetIterator> allIterators, List<TwoPhaseIterator> twoPhaseIterators) {
        TwoPhaseIterator twoPhaseIter = spans.asTwoPhaseIterator();
        if (twoPhaseIter != null) {
            ConjunctionUtils.addTwoPhaseIterator(twoPhaseIter, allIterators, twoPhaseIterators);
        } else { // no approximation support, use the iterator as-is
            ConjunctionUtils.addIterator(spans, allIterators, twoPhaseIterators);
        }
    }

    @Override
    public int docID() {
        return conjunction.docID();
    }

    @Override
    public long cost() {
        return conjunction.cost();
    }

    @Override
    public int  nextDoc() throws IOException {
        assert docID() != NO_MORE_DOCS;
        atFirstInCurrentDoc = false;
        int doc = conjunction.nextDoc();
        if (doc == NO_MORE_DOCS)
            return NO_MORE_DOCS;
        assert Arrays.stream(subSpans).allMatch(a -> a.docID() == doc);
        return toMatchDoc();
    }

    @Override
    public int advance(int target) throws IOException {
        assert target >= 0 && target > docID();
        atFirstInCurrentDoc = false;
        int doc = conjunction.advance(target);
        if (doc == NO_MORE_DOCS)
            return NO_MORE_DOCS;
        assert Arrays.stream(subSpans).allMatch(a -> a.docID() == doc);
        return toMatchDoc();
    }

    private int toMatchDoc() throws IOException {
        oneExhaustedInCurrentDoc = false;
        while (true) {
            if (twoPhaseCurrentDocMatches()) {
                return docID();
            }
            int doc = conjunction.nextDoc();
            if (doc == NO_MORE_DOCS)
                return NO_MORE_DOCS;
            assert Arrays.stream(subSpans).allMatch(a -> a.docID() == doc);
        }
    }

    protected abstract boolean twoPhaseCurrentDocMatches() throws IOException;

    /**
     * Return a {@link TwoPhaseIterator} view of this ConjunctionSpans.
     */
    @Override
    public TwoPhaseIterator asTwoPhaseIterator() {
        float totalMatchCost = 0;
        // Compute the matchCost as the total matchCost/positionsCostant of the sub spans.
        for (BLSpans spans : subSpans) {
            TwoPhaseIterator tpi = spans.asTwoPhaseIterator();
            if (tpi != null) {
                totalMatchCost += tpi.matchCost();
            } else {
                totalMatchCost += spans.positionsCost();
            }
        }
        final float matchCost = totalMatchCost;

        return new TwoPhaseIterator(conjunction) {
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

    @Override
    public float positionsCost() {
        throw new UnsupportedOperationException(); // asTwoPhaseIterator never returns null here.
    }

    /**
     * Collect postings data from the leaves of the current Spans.
     * <p>
     * This method should only be called after nextStartPosition(), and before NO_MORE_POSITIONS has been reached.
     *
     * @param collector the SpanCollector to use
     *
     * (added from Lucene as a reasonable default that could still be overridden)
     */
    @Override
    public void collect(SpanCollector collector) throws IOException {
        assert positionedAtHit();
        for (BLSpans subSpan : subSpans) {
            subSpan.collect(collector);
        }
    }

    @Override
    protected void passHitQueryContextToClauses(HitQueryContext context) {
        for (BLSpans subSpan : subSpans) {
            subSpan.setHitQueryContext(context);
        }
    }

    @Override
    public void getMatchInfo(MatchInfo[] matchInfo) {
        assert positionedAtHit();
        for (BLSpans subSpan : subSpans) {
            subSpan.getMatchInfo(matchInfo);
        }
    }

    @Override
    public boolean hasMatchInfo() {
        return Arrays.stream(subSpans).anyMatch(BLSpans::hasMatchInfo);
    }

    @Override
    public RelationInfo getRelationInfo() {
        for (int i = 0; i < subSpans.length; i++) {
            RelationInfo info = subSpans[i].getRelationInfo();
            if (info != null)
                return info;
        }
        return null;
    }
}
