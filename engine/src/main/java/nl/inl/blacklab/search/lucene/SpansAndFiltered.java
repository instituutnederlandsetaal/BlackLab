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
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.queries.spans.SpanCollector;
import org.apache.lucene.util.PriorityQueue;

/**
 * A multi-clause AND operation with an overridable filter.
 *
 * The filter (implemented by overriding the accept() method) might check that each clause
 * has the same relationId {@link SpansAndFilterFactorySameRelationId}; or that each clause matches a
 * different relation and we don't produce identical sets of relations matches
 * ({@link SpansAndFilterFactoryUniqueRelations}), etc.
 *
 * Based on {@link org.apache.lucene.queries.spans.NearSpansUnordered}.
 */
public class SpansAndFiltered extends BLConjunctionSpansInBuckets {

    /** Filter instance for filtering our hits */
    public static abstract class SpansAndFilter {
        protected HitQueryContext context;

        public SpansAndFilter() {
        }

        public void setContext(HitQueryContext context) {
            this.context = context;
        }

        public abstract boolean accept();

        public void collect(SpanCollector collector) {
            // Default implementation does nothing
        }
    }

    /** How to filter our matches */
    private SpansAndFilter filter;

    /** Name of the filter factory (without SpansFilterFactory), for toString() */
    private String filterName;

    /** One subspans exhausted in current doc, so there's no more hits in this doc. */
    private boolean oneExhaustedInCurrentDoc;

    /** Our subspans are already at the first hit, but our startPosition() method should still return -1. */
    private boolean atFirstInCurrentDoc;

    /** Keeps our subspans in order, and keeps track of total length and end position. */
    private SpanTotalLengthEndPositionWindow spanWindow;

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
    private static List<SpansInBuckets> bucketizeSameStartEnd(List<BLSpans> subSpans, SpansAndFilterFactory factory) {
        List<SpansInBuckets> bucketized = new ArrayList<>();
        for (int i = 0; i < subSpans.size(); i++) {
            bucketized.add(factory.bucketize(ensureSorted(subSpans.get(i))));
            //bucketized.add(new SpansInBucketsSameStartEnd(ensureSorted(subSpans.get(i))));
        }
        return bucketized;
    }

    public SpansAndFiltered(List<BLSpans> subSpans, SpansAndFilterFactory factory) {
        super(bucketizeSameStartEnd(subSpans, factory),
                SpanQueryAnd.createGuarantees(SpanGuarantees.from(subSpans), false));

        this.filter = factory.create(this, this.subSpans, indexInBucket);
        this.filterName = factory.name();
        this.spanWindow = new SpanTotalLengthEndPositionWindow();
        this.atFirstInCurrentDoc = true; // ensure for doc -1 that start/end positions are -1
    }

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
            if (filter.accept()) {
                return spanWindow.top().bucketStart();
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
                    if (filter.accept()) {  // Valid match?
                        return spanWindow.top().startPosition(i);
                    } else {
                        // Not valid; try next match
                        break;
                    }
                }
            }
            // If all buckets are exhausted, we're done
            if (i == subSpans.length) {
                return NO_MORE_POSITIONS;
            }
        }
    }

    @Override
    protected void passHitQueryContextToClauses(HitQueryContext context) {
        super.passHitQueryContextToClauses(context);
        this.filter.setContext(context);
    }

    @Override
    public int startPosition() {
        assert spanWindow.top() != null;
        int r = atFirstInCurrentDoc ? -1
                : oneExhaustedInCurrentDoc ? NO_MORE_POSITIONS
                : spanWindow.top().bucketStart();
        return r;
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
        filter.collect(collector);
    }

    @Override
    public String toString() {
        return "AND_FILTERED(" + filterName + ", " + StringUtils.join(subSpans, ", ") + ")";
    }
}
