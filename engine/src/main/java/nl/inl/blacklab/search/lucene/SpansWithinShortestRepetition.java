package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.queries.spans.SpanCollector;
import org.apache.lucene.search.ConjunctionUtils;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.TwoPhaseIterator;

/**
 * Resolve Q within <s/>+ with the shortest possible match for <s/>+.
 *
 * Used to implement context=s where multiple sentences may have to be
 * captured because the match crosses sentence boundaries.
 */
class SpansWithinShortestRepetition extends BLSpans {
    /** The spans we're (possibly) looking for */
    private final BLSpans producer;

    /** The filter spans we want to find a repetition for */
    private final SpansInBucketsPerDocument filterUnit;

    /** Relations to capture */
    private final BLSpans relations;

    /** What match info to capture the list of relations under */
    private final String captureRelsAs;

    /** Match info index for list of captured relations */
    private int captureRelsIndex;

    /** First filter hit used for current match */
    private int firstFilterHit;

    /** Last filter hit used for current match */
    private int lastFilterHit;

    /** How to adjust the leading edge of the producer hits while matching */
    private final int adjustLeading;

    /** How to adjust the trailing edge of the producer hits while matching */
    private final int adjustTrailing;

    /**
     * Are we already at the first match in a new document, before
     * nextStartPosition() has been called? Necessary because we have to make sure
     * nextDoc()/advance() actually puts us in a document with at least one match.
     */
    private boolean atFirstInCurrentDoc = false;

    /**
     * Do we have a positive (non-inverted) filter query that has no
     * more matches? If so, we can't have any more matches regardless
     * of the status of producerDoc.
     */
    private boolean positiveFilterRanOut = false;

    /** Do we need to call filter.nextBucket() before matching? */
    private int nextBucketCalledOnDocId = -1;

    /**
     * Find hits from producer, filtered by the filter according to the specified op
     *
     * @param producer the hits we may be interested in
     * @param filterUnit the hits used to filter the producer hits
     * @param op filter operation to use
     * @param invert if true, produce hits that DON'T match the filter instead
     * @param adjustLeading how to adjust the left edge of the producer hits while
     *            matching
     * @param adjustTrailing how to adjust the right edge of the producer hits while
     *            matching
     */
    public SpansWithinShortestRepetition(BLSpans producer, BLSpans filterUnit, BLSpans relations,
            String captureRelsAs, int adjustLeading, int adjustTrailing) {
        super(SpanQueryPositionFilter.createGuarantees(producer.guarantees()));
        this.producer = BLSpans.ensureSortedUnique(producer);
        this.filterUnit = SpansInBucketsPerDocument.sorted(filterUnit);
        this.relations = relations;
        this.captureRelsAs = captureRelsAs;
        this.adjustLeading = adjustLeading;
        this.adjustTrailing = adjustTrailing;
    }

    @Override
    public int docID() {
        return positiveFilterRanOut ? NO_MORE_DOCS : producer.docID();
    }

    @Override
    public int endPosition() {
        if (atFirstInCurrentDoc)
            return -1; // nextStartPosition() hasn't been called yet
        assert positionedInDoc();
        return producer.endPosition();
    }

    @Override
    public int nextDoc() throws IOException {
        assert docID() != NO_MORE_DOCS;
        atFirstInCurrentDoc = false;

        // Advance container
        if (producer.nextDoc() == NO_MORE_DOCS)
            return NO_MORE_DOCS; // no more containers; we're done.

        // Find first matching producer span from here
        return findDocWithMatch();
    }

    @Override
    public int advance(int target) throws IOException {
        assert docID() != NO_MORE_DOCS;
        assert target >= 0 && target > docID();
        atFirstInCurrentDoc = false;

        // Skip both to doc
        if (producer.advance(target) == NO_MORE_DOCS)
            return NO_MORE_DOCS;

        // Find first matching producer span from here
        return findDocWithMatch();
    }

    /**
     * Find a producer span (not necessarily in this document) matching with filter,
     * starting from the current producer span.
     *
     * @return docID if found, NO_MORE_DOCS if no such producer span exists (i.e.
     *         we're done)
     */
    private int findDocWithMatch() throws IOException {
        assert positionedInDoc();
        // Find the next "valid" container, if there is one.
        int producerDoc = producer.docID();
        int filterDoc = filterUnit.docID();
        while (producerDoc != NO_MORE_DOCS) {

            // Are filter and producer in the same document?
            while (filterDoc != producerDoc) {
                if (filterDoc < producerDoc) {
                    // No, advance filter to be in the same document as the producer
                    filterDoc = filterUnit.advance(producerDoc);
                    if (filterDoc == NO_MORE_DOCS) {
                        // Positive filter, but no more filter hits. We're done.
                        positiveFilterRanOut = true;
                        return NO_MORE_DOCS;
                    }
                } else {
                    // No, advance producer to be in the same document as the producer
                    producerDoc = producer.advance(filterDoc);
                    if (producerDoc == NO_MORE_DOCS)
                        return NO_MORE_DOCS; // No more producer results, we're done.
                }
            }

            // Are there search results in this document?
            if (twoPhaseCurrentDocMatches())
                return producerDoc;

            // No search results found in the current container.
            // Advance to the next container.
            producerDoc = producer.nextDoc();
        }
        return producerDoc;
    }

    private boolean twoPhaseCurrentDocMatches() throws IOException {
        assert positionedInDoc();
        atFirstInCurrentDoc = false;
        assert producer.startPosition() < 0;

        int producerStart = producer.nextStartPosition(); // position it
        if (producerStart == NO_MORE_POSITIONS)
            return false;

        // Filter also may not have been advanced by our approximation.
        if (filterUnit.docID() < producer.docID()) {
            // Filter lagging behind producer (because conjunction only advanced producer - inverted filter, see above)
            filterUnit.advance(producer.docID());
            // (NOTE: if we overshot the target, synchronizePos will detect that)
        }

        // Now that both clauses are positioned, find an actual match
        if (synchronizePos() != NO_MORE_POSITIONS) {
            atFirstInCurrentDoc = true;
            return true;
        }
        return false;
    }

    /**
     * Return a {@link TwoPhaseIterator} view of this Spans.
     */
    @Override
    public TwoPhaseIterator asTwoPhaseIterator() {
        // We can use conjunction of the producer and filter (both need to occur in document to produce matches)
        DocIdSetIterator approx = ConjunctionUtils.intersectIterators(List.of(producer, filterUnit));
        return new TwoPhaseIterator(approx) {
            @Override
            public boolean matches() throws IOException {
                return twoPhaseCurrentDocMatches();
            }

            @Override
            public float matchCost() {
                return approximation.cost();
            }
        };
    }

    @Override
    public int nextStartPosition() throws IOException {
        assert startPosition() != NO_MORE_POSITIONS;
        if (atFirstInCurrentDoc) {
            // We're already at the first match in the doc. Return it.
            atFirstInCurrentDoc = false;
            assert positionedAtHit();
            return producer.startPosition();
        }

        // Are we done yet?
        if (producer.startPosition() == NO_MORE_POSITIONS)
            return NO_MORE_POSITIONS;

        // Find first matching producer span from here
        if (producer.nextStartPosition() == NO_MORE_POSITIONS)
            return NO_MORE_POSITIONS;
        return synchronizePos();
    }

    @Override
    public int advanceStartPosition(int target) throws IOException {
        assert target > startPosition();
        if (atFirstInCurrentDoc) {
            atFirstInCurrentDoc = false;
            int producerStart = producer.startPosition();
            if (producerStart >= target) {
                assert positionedAtHit();
                return producerStart;
            }
        }

        // Are we done yet?
        if (producer.startPosition() == NO_MORE_POSITIONS)
            return NO_MORE_POSITIONS;

        if (producer.advanceStartPosition(target) == NO_MORE_POSITIONS)
            return NO_MORE_POSITIONS;

        // Find first matching producer span from here
        return synchronizePos();
    }

    /**
     * Find a producer span matching with filter, starting from the current producer
     * span.
     *
     * Both producer and filter must be positioned, i.e. producer must be at a valid
     * span and filter must be in a document (although nextBucket may not have been called;
     * see {@link #nextBucketCalledOnDocId}).
     *
     * @return start position if found, NO_MORE_POSITIONS if no such container
     *         exists (i.e. we're done)
     */
    private int synchronizePos() throws IOException {
        // Find the next "valid" producer spans, if there is one.
        int producerStart = producer.startPosition();
        assert producerStart >= 0 && producerStart != NO_MORE_POSITIONS;
        while (producerStart != NO_MORE_POSITIONS) {

            // Are producer and filter in the same doc?
            if (filterUnit.docID() != producer.docID()) {
                // No filter matches, therefore no matches
                return NO_MORE_POSITIONS;
            }

            // We must be at a valid (non-empty) bucket.
            if (nextBucketCalledOnDocId < filterUnit.docID()) {
                int docId = filterUnit.nextBucket();
                assert docId != SpansInBuckets.NO_MORE_BUCKETS;
                nextBucketCalledOnDocId = docId;
            }
            assert filterUnit.bucketSize() > 0;

            // Looking for the range of filter hits that overlap the producer hit.
            // (i.e. the consecutive sentences that together contain the producer hit, if the filter is sentences)
            // First, look for the first filter hit that starts after the producer hit starts. The filter hit BEFORE
            // that one is the start of our range.
            int min = 0, max = filterUnit.bucketSize() - 1;
            while (min < max) {
                int i = (min + max) / 2;
                if (filterUnit.startPosition(i) > producerStart + adjustLeading) {
                    // Filter start position after producer start.
                    max = i;
                } else if (filterUnit.startPosition(i) <= producerStart + adjustLeading) {
                    // Filter start position before or at producer start.
                    // We're looking for the first filter hit that starts after the producer hit starts, so update min.
                    min = i + 1;
                }
            }
            int startIndex = min == 0 ? 0 : min - 1; // index of the last filter hit that starts before or at producer start
            if (filterUnit.startPosition(startIndex) > producerStart + adjustLeading) {
                // No filter hit starts before or at producer start, so producer cannot fit within a repetition of
                // filter units. No match.
                producerStart = producer.nextStartPosition();
                continue;
            }
            // Next, look for the first filter hit that ends after or at the producer hit ends (or the last one if not found).
            // This is the end of our range.
            int endIndex;
            for (endIndex = startIndex; endIndex < filterUnit.bucketSize(); endIndex++) {
                if (filterUnit.endPosition(endIndex) >= producer.endPosition() + adjustTrailing)
                    break;
            }
            if (filterUnit.endPosition(endIndex) < producer.endPosition() + adjustTrailing) {
                // Didn't match filter (producer doesn't fit within a repetition of filter units);
                // go to the next producer hit.
                producerStart = producer.nextStartPosition();
                continue;
            }

            // We found a filter hit that starts before or at the producer hit and ends after or at the producer hit.
            // This is a match! Return it. Remember which filter hits we used, for getting captured groups.
            firstFilterHit = startIndex;
            lastFilterHit = endIndex;
            return producerStart;
        }
        return NO_MORE_POSITIONS;
    }

    @Override
    public int startPosition() {
        if (atFirstInCurrentDoc)
            return -1; // nextStartPosition() hasn't been called yet
        return producer.startPosition();
    }

    @Override
    public String toString() {
        String ign = (adjustLeading != 0 || adjustTrailing != 0) ? ", " + adjustLeading + ", " + adjustTrailing : "";
        return "WITHIN-SHORTEST-REP(" + producer + ", " + filterUnit + ign + ")";
    }

    @Override
    public void passHitQueryContextToClauses(HitQueryContext context) {
        producer.setHitQueryContext(context);
        filterUnit.setHitQueryContext(context);
        captureRelsIndex = context.registerMatchInfo(captureRelsAs, MatchInfo.Type.LIST_OF_RELATIONS);
    }

    @Override
    public void getMatchInfo(MatchInfo[] matchInfo) {
        if (!childClausesCaptureMatchInfo)
            return;
        producer.getMatchInfo(matchInfo);
        int docId = filterUnit.docID();
        int start = filterUnit.startPosition(firstFilterHit);
        int end = filterUnit.endPosition(lastFilterHit);
        List<RelationInfo> capturedRelations = new ArrayList<>();
        try {
            SpansCaptureRelationsWithinSpan.captureRelationsWithinSpan(docId, start, end, relations, capturedRelations);
            RelationListInfo relationListInfo = RelationListInfo.create(capturedRelations, getOverriddenField());
            matchInfo[captureRelsIndex] = relationListInfo;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean hasMatchInfo() {
        return true; // we capture context_rels
    }

    @Override
    public RelationInfo getRelationInfo() {
        return producer.getRelationInfo();
    }

    @Override
    public int width() {
        return producer.width();
    }

    @Override
    public void collect(SpanCollector collector) throws IOException {
        producer.collect(collector);
    }

    @Override
    public float positionsCost() {
        throw new UnsupportedOperationException(); // asTwoPhaseIterator never returns null here.
    }
}
