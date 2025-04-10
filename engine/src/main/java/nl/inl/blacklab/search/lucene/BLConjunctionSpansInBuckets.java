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

package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.lucene.search.ConjunctionDISI;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.search.spans.SpanCollector;

/**
 * Common super class for multiple sub spans required in a document.
 * <p>
 * (adapted directly from Lucene's ConjunctionSpans)
 */
abstract class BLConjunctionSpansInBuckets extends BLSpans {
    protected final SpansInBuckets[] subSpans; // in query order
    protected final int[] indexInBucket;     // our position in the bucket
    final DocIdSetIterator conjunction; // use to move to next doc with all clauses

    BLConjunctionSpansInBuckets(List<SpansInBuckets> subSpans, SpanGuarantees guarantees) {
        super(guarantees);
        if (subSpans.size() < 2) {
            throw new IllegalArgumentException("Less than 2 subSpans.size():" + subSpans.size());
        }
        this.subSpans = subSpans.toArray(new SpansInBuckets[0]);
        this.indexInBucket = new int[subSpans.size()];
        Arrays.fill(indexInBucket, -1);
        this.conjunction = ConjunctionDISI.intersectIterators(Collections.unmodifiableList(subSpans));
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
        int doc = conjunction.nextDoc();
        assert Arrays.stream(subSpans).allMatch(a -> a.docID() == doc);
        if (doc == NO_MORE_DOCS)
            return NO_MORE_DOCS;
        return toMatchDoc();
    }

    @Override
    public int advance(int target) throws IOException {
        assert target >= 0 && target > docID();
        int doc = conjunction.advance(target);
        assert Arrays.stream(subSpans).allMatch(a -> a.docID() == doc);
        if (doc == NO_MORE_DOCS)
            return NO_MORE_DOCS;
        return toMatchDoc();
    }

    int toMatchDoc() throws IOException {
        while (true) {
            if (twoPhaseCurrentDocMatches()) {
                return docID();
            }
            int doc = conjunction.nextDoc();
            if (doc == NO_MORE_DOCS) {
                return NO_MORE_DOCS;
            }
            assert Arrays.stream(subSpans).allMatch(a -> a.docID() == doc);
        }
    }


    abstract boolean twoPhaseCurrentDocMatches() throws IOException;

    /**
     * Return a {@link TwoPhaseIterator} view of this ConjunctionSpans.
     */
    @Override
    public TwoPhaseIterator asTwoPhaseIterator() {
        float totalMatchCost = 0;
        // Compute the matchCost as the total matchCost/positionsCostant of the sub spans.
        for (SpansInBuckets spans : subSpans) {
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
//        for (SpansInBuckets subSpan : subSpans) {
//            subSpan.collect(collector);
//        }
    }

    @Override
    protected void passHitQueryContextToClauses(HitQueryContext context) {
        for (SpansInBuckets subSpan : subSpans) {
            subSpan.setHitQueryContext(context);
        }
    }

    @Override
    public void getMatchInfo(MatchInfo[] matchInfo) {
        for (int i = 0; i < subSpans.length; i++) {
            subSpans[i].getMatchInfo(indexInBucket[i], matchInfo);
        }
    }

    @Override
    public boolean hasMatchInfo() {
        return Arrays.stream(subSpans).anyMatch(SpansInBuckets::hasMatchInfo);
    }

    @Override
    public RelationInfo getRelationInfo() {
        for (int i = 0; i < subSpans.length; i++) {
            RelationInfo info = subSpans[i].getRelationInfo(indexInBucket[i]);
            if (info != null)
                return info;
        }
        return null;
    }
}
