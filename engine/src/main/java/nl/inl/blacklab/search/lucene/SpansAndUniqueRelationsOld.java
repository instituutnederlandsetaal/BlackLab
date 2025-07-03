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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.search.spans.SpanCollector;
import org.apache.lucene.util.PriorityQueue;

/**
 * Similar to {@link org.apache.lucene.search.spans.NearSpansOrdered}, but for the unordered case.
 *
 * Expert:
 * Only public for subclassing.  Most implementations should not need this class
 *
 * (copied from Lucene, to adapt into an AND operation with multiple clauses and the requirement
 *  that each clause corresponds to a *different* active relation, which we need to perform tree-like
 *  dependency relations queries)
 * (we also make sure we don't match two hits with the same start and end position but relation match info
 *  A/B and B/A respectively; i.e. relation match info should be unique regardless of ordering)
 *
 *
 *
 * NOTE: we'll need to bucketize our spans using SpansInBucketsSameStartEnd, but BLConjunctionSpans
 * cannot handle SpansInBuckets as clauses, so we'll either need to make it generic or derive from
 * BLSpans instead.
 *
 *
 *
 *
 */
public class SpansAndUniqueRelationsOld extends BLConjunctionSpansInBuckets {

    /** One subspans exhausted in current doc, so there's no more hits in this doc. */
    boolean oneExhaustedInCurrentDoc;

    /** Our subspans are already at the first hit, but our startPosition() method should still return -1. */
    boolean atFirstInCurrentDoc;

    /** Keeps our subspans in order, and keeps track of total length and end position. */
    private final SpanTotalLengthEndPositionWindow spanWindow;

    private HitQueryContext context;

    /** Sorted serialized representation of the relationInfos we've returned
     *  at the current start/end position. We use this to avoid the A/B vs. B/A problem:
     *  we should produce separate hits for separate combinations of relations matches,
     *  but we shouldn't produce two hits that match the same relations, even if the
     *  order is different. */
    private final Set<List<RelationInfo>> relationsReturnedAtThisPosition = new HashSet<>();

    /**
     * Wrap BLSpans in SpansInBucketsSameStartEnd.
     *
     * This is necessary so "duplicate" hits (hits with the same start and end position)
     * will correctly generate multiple results when combined with AND. These duplicate hits
     * may still have different match information, so we can't just remove them.
     *
     * @param subSpans the spans to bucketize
     * @return the bucketized spans
     */
    private static List<SpansInBuckets> bucketizeSameStartEnd(List<BLSpans> subSpans) {
        List<SpansInBuckets> bucketized = new ArrayList<>();
        for (BLSpans subSpan: subSpans) {
            bucketized.add(new SpansInBucketsSameStartEnd(ensureSorted(subSpan)));
        }
        return bucketized;
    }

    public SpansAndUniqueRelationsOld(List<BLSpans> subSpans) {
        super(bucketizeSameStartEnd(subSpans),
                SpanQueryAnd.createGuarantees(SpanGuarantees.from(subSpans), false));

        this.spanWindow = new SpanTotalLengthEndPositionWindow();
        this.atFirstInCurrentDoc = true; // ensure for doc -1 that start/end positions are -1
    }

    /** Maintain totalSpanLength and maxEndPosition */
    protected class SpanTotalLengthEndPositionWindow extends PriorityQueue<SpansInBuckets> {

        public SpanTotalLengthEndPositionWindow() {
            super(subSpans.length);
        }

        @Override
        protected final boolean lessThan(SpansInBuckets spans1, SpansInBuckets spans2) {
            return positionsOrdered(spans1, spans2);
        }

        void startDocument() throws IOException {
            // Place all spans in the first bucket and add to the queue
            clear();
            for (SpansInBuckets spans: subSpans) {
                int docId = spans.nextBucket();
                assert docId != SpansInBuckets.NO_MORE_BUCKETS;
                assert spans.bucketSize() > 0;
                add(spans);
            }
        }

        boolean nextPosition() throws IOException {
            // Advance the top (most lagging) span
            SpansInBuckets topSpans = top();
            assert topSpans.bucketStart() != NO_MORE_POSITIONS;
            if (topSpans.nextBucket() == SpansInBuckets.NO_MORE_BUCKETS) {
                return false;
            }
            updateTop();
            return true;
        }

        boolean atMatch() {
            // Make sure all spans are at the same start and end position
            Iterator<SpansInBuckets> it = iterator();
            int start = -1, end = -1;
            while (it.hasNext()) {
                SpansInBuckets spans = it.next();
                if (start == -1) {
                    start = spans.bucketStart();
                    end = spans.bucketEnd();
                } else {
                    if (spans.bucketStart() != start || spans.bucketEnd() != end) {
                        return false;
                    }
                }
            }
            return true;
        }
    }


    /** Check whether two Spans in the same document are ordered with possible overlap.
     * @return true iff spans1 starts before spans2
     *              or the spans start at the same position,
     *              and spans1 ends before spans2.
     */
    static boolean positionsOrdered(SpansInBuckets spans1, SpansInBuckets spans2) {
        assert spans1.docID() == spans2.docID() : "doc1 " + spans1.docID() + " != doc2 " + spans2.docID();
        int start1 = spans1.bucketStart();
        int start2 = spans2.bucketStart();
        return (start1 == start2) ? (spans1.bucketEnd() < spans2.bucketEnd()) : (start1 < start2);
    }

    @Override
    boolean twoPhaseCurrentDocMatches() throws IOException {
        oneExhaustedInCurrentDoc = false;
        atFirstInCurrentDoc = false;
        assert positionedInDoc();
        // at doc with all subSpans
        relationsReturnedAtThisPosition.clear(); // don't return the same combination of relations twice
        spanWindow.startDocument();
        assert spanWindow.size() == subSpans.length;
        while (true) {
            if (spanWindow.atMatch()) {
                oneExhaustedInCurrentDoc = false;
                for (int i = 0; i < subSpans.length; i++) {
                    indexInBucket[i] = 0;
                }
                // Is this really a match (i.e. the same relation wasn't matched multiple times)?
                if (nextMatchAtThisPosition(false) != NO_MORE_POSITIONS) {
                    atFirstInCurrentDoc = true;
                    return true;
                }
            }
            if (! spanWindow.nextPosition()) {
                return false;
            }
        }
    }

    @Override
    public int nextStartPosition() throws IOException {
        assert startPosition() != NO_MORE_POSITIONS;
        if (atFirstInCurrentDoc) {
            atFirstInCurrentDoc = false;
            return spanWindow.top().bucketStart();
        }
        assert spanWindow.top().bucketStart() != -1;
        assert spanWindow.top().bucketStart() != NO_MORE_POSITIONS;

        // Make sure we return all combinations of matches with this start and end
        // (and different match information)
        int startPosition = nextMatchAtThisPosition(true);
        if (startPosition != NO_MORE_POSITIONS)
            return startPosition;

        // No more matches with this start and end position, move to the next position
        while (true) {
            if (!spanWindow.nextPosition()) {
                oneExhaustedInCurrentDoc = true;
                return NO_MORE_POSITIONS;
            }
            if (spanWindow.atMatch()) {
                int startPos = nextMatchAtThisPosition(false);
                if (startPos != NO_MORE_POSITIONS) {
                    return startPos;
                }
            }
        }
    }

    /**
     * Without changing start or end position, move to the next match.
     *
     * Each of our clauses has been bucketized into buckets with the same start/end,
     * so we need to produce all combinations of the matches in these buckets.
     *
     * @param immediatelyGoToNext if true, go to the next match immediately, otherwise check if
     *                            we're already at a valid match
     * @return the start position, or NO_MORE_POSITIONS if we're done at this start/end position
     */
    private int nextMatchAtThisPosition(boolean immediatelyGoToNext) {
        if (!immediatelyGoToNext) {
            // Check if we're already at a valid match.
            if (eachClauseHasDifferentActiveRelation()) {
                List<RelationInfo> relStr = getRelationsSorted();
                if (relationsReturnedAtThisPosition.add(relStr)) {
                    // this is a new combination of relations
                    return spanWindow.top().bucketStart();
                }
            }
        }
        while (true) {
            // Go to the next match with this start/end position and check if it's valid.
            // (this works like counting, except indexInBucket[0] is the least significant digit,
            //  and the bucket size per span is not necessarily the same, i.e. each digit is in
            //  a different base)
            int i;
            for (i = 0; i < subSpans.length; i++) {
                if (indexInBucket[i] >= subSpans[i].bucketSize() - 1) {
                    // Roll over to 0 and advance the next spans ("carry the 1") in the next loop iteration
                    indexInBucket[i] = 0;
                } else {
                    // Go to next match in this bucket and return it if it's valid
                    indexInBucket[i]++;
                    if (eachClauseHasDifferentActiveRelation()) {  // Each clause matches a different relation
                        List<RelationInfo> relStr = getRelationsSorted();
                        if (relationsReturnedAtThisPosition.add(relStr)) {
                            // This is a new combination of relations
                            return spanWindow.top().startPosition(i);
                        }
                        // Not unique; try next match
                        break;
                    } else {
                        // Not valid; try next match
                        break;
                    }
                }
            }
            // If all buckets are exhausted, we're done
            if (i == subSpans.length) {
                relationsReturnedAtThisPosition.clear(); // reset for next position
                return NO_MORE_POSITIONS;
            }
        }
    }

    private List<RelationInfo> getRelationsSorted() {
        MatchInfo[] matchInfo = new MatchInfo[context.numberOfMatchInfos()];
        getMatchInfo(matchInfo);
        List<RelationInfo> ri = new ArrayList<>();
        for (MatchInfo mi : matchInfo) {
            if (mi != null && mi.getType() == MatchInfo.Type.RELATION) {
                ri.add((RelationInfo)mi);
            }
        }
        ri.sort(RelationInfo::compareTo);
        return ri;
    }

    @Override
    protected void passHitQueryContextToClauses(HitQueryContext context) {
        super.passHitQueryContextToClauses(context);
        this.context = context;
    }

    @Override
    public int startPosition() {
        assert spanWindow.top() != null;
        int r = atFirstInCurrentDoc ? -1
                : oneExhaustedInCurrentDoc ? NO_MORE_POSITIONS
                : spanWindow.top().bucketStart();
        return r;
    }

    private boolean eachClauseHasDifferentActiveRelation() {
        // Double-check that no relation was matched multiple times,
        // i.e. all matched relations are unique
        Set<RelationInfo> relations = new HashSet<>();
        for (int i = 0; i < subSpans.length; i++) {
            RelationInfo r = subSpans[i].getRelationInfo(indexInBucket[i]);
            if (!relations.add(r)) {
                // This relation was already found by another clause; this is not a valid match
                return false;
            }
        }
        return true;
    }

    @Override
    public int endPosition() {
        return atFirstInCurrentDoc ? -1
                : oneExhaustedInCurrentDoc ? NO_MORE_POSITIONS
                : spanWindow.top().bucketEnd();
    }

    @Override
    public int width() {
        return endPosition() - startPosition();
    }

    @Override
    public void collect(SpanCollector collector) throws IOException {
        // we cannot collect because our clauses are bucketized.
        // that's okay, we only use payload directly in "simple" spans anyway,
        // we don't need to propagate it to "parent" spans.
    }

    @Override
    public String toString() {
        return "ANDUNIQREL(" + StringUtils.join(subSpans, ", ") + ")";
    }
}
