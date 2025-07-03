package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.lucene.search.ConjunctionUtils;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.queries.spans.SpanCollector;
import org.apache.lucene.store.ByteArrayDataInput;

import nl.inl.blacklab.search.indexmetadata.RelationsStrategySeparateTerms;

/**
 * A version of SpansAnd that accepts more than two clauses and
 * requires them to have the same relation id for a match.
 */
class SpansAndSameRelationIdOld extends BLSpans {

    boolean oneExhaustedInCurrentDoc = false;

    boolean atFirstInCurrentDoc = true;

    /** Bucketed clauses */
    List<SpansInBucketsSameStartEnd> subSpans;

    /** Index in current bucket */
    int[] index = new int[] { -1, -1 };

    /** DocIdSetIterator conjunction of our clauses, for two-phase iterator */
    private final DocIdSetIterator conjunction;

    /**
     * Construct SpansAndSameRelationId.
     * <p>
     * Clauses must be start-point sorted.
     *
     * @param clauses clauses
     */
    public SpansAndSameRelationIdOld(List<BLSpans> clauses) {
        super(SpanQueryAnd.createGuarantees(
                clauses.stream().map(BLSpans::guarantees).toList(),
                false));
        subSpans = new ArrayList<>(clauses.size());
        for (int i = 0; i < clauses.size(); i++) {
            BLSpans clause = clauses.get(i);
            if (!clause.guarantees().hitsStartPointSorted())
                throw new IllegalArgumentException("Clause " + i + " is not start-point sorted");
            subSpans.add(new SpansInBucketsSameStartEnd(clause, true));
        }
        this.conjunction = ConjunctionUtils.intersectIterators(Collections.unmodifiableList(subSpans));
    }

    @Override
    public int nextDoc() throws IOException {
        assert docID() != NO_MORE_DOCS;
        atFirstInCurrentDoc = false;
        int doc = conjunction.nextDoc();
        if (doc == NO_MORE_DOCS)
            return NO_MORE_DOCS;
        assert subSpans.stream().allMatch(a -> a.docID() == doc);
        return toMatchDoc();
    }

    @Override
    public int advance(int target) throws IOException {
        assert target >= 0 && target > docID();
        atFirstInCurrentDoc = false;
        int doc = conjunction.advance(target);
        if (doc == NO_MORE_DOCS)
            return NO_MORE_DOCS;
        assert subSpans.stream().allMatch(a -> a.docID() == doc);
        return toMatchDoc();
    }

    int toMatchDoc() throws IOException {
        while (true) {
            if (twoPhaseCurrentDocMatches()) {
                return docID();
            }
            int doc = conjunction.nextDoc();
            if (doc == NO_MORE_DOCS)
                return NO_MORE_DOCS;
            assert subSpans.stream().allMatch(a -> a.docID() == doc);
        }
    }

    @Override
    public int docID() {
        return subSpans.get(0).docID();
    }

    @Override
    public int startPosition() {
        if (oneExhaustedInCurrentDoc)
            return NO_MORE_POSITIONS;
        return atFirstInCurrentDoc ? -1 : subSpans.get(0).startPosition(index[0]);
    }

    @Override
    public int endPosition() {
        if (oneExhaustedInCurrentDoc)
            return NO_MORE_POSITIONS;
        return atFirstInCurrentDoc ? -1 : subSpans.get(0).endPosition(index[0]);
    }

    @Override
    public int nextStartPosition() throws IOException {
        assert startPosition() != NO_MORE_POSITIONS;
        if (oneExhaustedInCurrentDoc)
            return NO_MORE_POSITIONS;
        if (atFirstInCurrentDoc) {
            atFirstInCurrentDoc = false;
            assert Arrays.stream(index).allMatch(el -> el >= 0);
            assert IntStream.range(0, index.length).allMatch(i -> index[i] < subSpans.get(i).bucketSize());
            assert IntStream.range(0, index.length).allMatch(i -> subSpans.get(i).startPosition(index[i]) >= 0);
            assert IntStream.range(0, index.length).allMatch(i -> subSpans.get(i).startPosition(index[i]) != NO_MORE_POSITIONS);
            return subSpans.get(0).startPosition(index[0]);
        }

        // See if there's more combinations for the current start/end
        // (works the same as incrementing a decimal number: we always
        //  increment the last digit, until we can't, then we set that to
        //  0 and increment the second to last digit, etc., except we don't
        //  use digits but the valid bucket indexes for each clause)
        int clauseIndex = subSpans.size() - 1;
        while (true) {
            if (index[clauseIndex] < subSpans.get(clauseIndex).bucketSize() - 1) {
                index[clauseIndex]++;
                if (relationIdsMatch())
                    return startPosition();
            } else {
                index[clauseIndex] = 0;
                clauseIndex--;
                if (clauseIndex < 0)
                    break;
            }
        }

        // Advance all spans to next bucket and synchronize
        for (SpansInBucketsSameStartEnd subSpan: subSpans) {
            if (subSpan.nextBucket() == SpansInBuckets.NO_MORE_BUCKETS) {
                oneExhaustedInCurrentDoc = true;
                return NO_MORE_POSITIONS;
            }
        }
        return synchronizePosition();
    }

    @Override
    public int advanceStartPosition(int targetPos) throws IOException {
        assert targetPos > startPosition();
        if (oneExhaustedInCurrentDoc)
            return NO_MORE_POSITIONS;
        int startPos = startPosition();
        if (startPos >= targetPos)
            return nextStartPosition(); // already at or beyond target. per contract, return next match
        for (SpansInBucketsSameStartEnd subSpan: subSpans) {
            if (subSpan.advanceBucket(targetPos) == SpansInBuckets.NO_MORE_BUCKETS) {
                oneExhaustedInCurrentDoc = true;
                return NO_MORE_POSITIONS;
            }
        }
        return synchronizePosition();
    }

    private int synchronizePosition() throws IOException {
        while (true) {
            if (oneExhaustedInCurrentDoc)
                return NO_MORE_POSITIONS;
            int smallestStart = subSpans.get(0).bucketStart();
            int smallestStartIndex = 0;
            int largestStart = smallestStart;
            int smallestEnd = subSpans.get(0).bucketEnd();
            int smallestEndIndex = 0;
            int largestEnd = smallestEnd;
            boolean allSameStart = smallestStart != -1;
            boolean allSameEnd = true;
            // TODO: Keeping spans sorted would probably be more efficient..?
            for (int i = 1; allSameStart && i < subSpans.size(); i++) {
                int start = subSpans.get(i).bucketStart();
                if (start != smallestStart) {
                    allSameStart = false;
                    if (start < smallestStart) {
                        smallestStart = start;
                        smallestStartIndex = i;
                    }
                }
                if (start > largestStart) {
                    largestStart = start;
                }
                int end = subSpans.get(i).bucketEnd();
                if (end != smallestEnd) {
                    allSameEnd = false;
                    if (end < smallestEnd) {
                        smallestEnd = end;
                        smallestEndIndex = i;
                    }
                }
                if (end > largestEnd) {
                    largestEnd = end;
                }
            }

            // Synch at match start level
            if (!allSameStart) {
                // Starts don't match
                catchUpMatchStart(smallestStartIndex, largestStart);
            } else if (!allSameEnd) {
                // Starts match but ends don't
                catchUpMatchEnd(smallestEndIndex, largestEnd);
            } else {
                // Both match
                assert smallestStart >= 0;
                Arrays.fill(index, 0);
                if (relationIdsMatch())
                    return smallestStart;
            }
        }
    }

    private boolean relationIdsMatch() {
        // Note: getRelationInfo doesn't work here, these are simple regex spans.
        //   we need to read the relationId directly from the payload.
        byte[] payload = ((SpansInBucketsSameStartEnd) subSpans.get(0)).payload(index[0]);
        int relationId = RelationsStrategySeparateTerms.CODEC.readRelationId(new ByteArrayDataInput(payload));
        for (int i = 1; i < subSpans.size(); i++) {
            int thisRelId = RelationsStrategySeparateTerms.CODEC.readRelationId(new ByteArrayDataInput(((SpansInBucketsSameStartEnd) subSpans.get(i)).payload(index[i])));
            if (thisRelId != relationId)
                return false;
        }
        return true;
    }

    /** See if we can get starts to line up. */
    private void catchUpMatchStart(int laggingSpans, int catchUpTo) throws IOException {
        SpansInBucketsSameStartEnd span = subSpans.get(laggingSpans);
        int catchUpFrom = span.bucketStart();
        if (catchUpFrom < catchUpTo || catchUpFrom == -1) { // also covers catchUpFrom != NO_MORE_POSITIONS
            if (span.advanceBucket(catchUpTo) == SpansInBuckets.NO_MORE_BUCKETS)
                oneExhaustedInCurrentDoc = true;
        }
    }

    /** Try to get ends to line up without moving starts. */
    private void catchUpMatchEnd(int laggingSpans, int catchUpToEnd) throws IOException {
        int catchUpFromStart = subSpans.get(laggingSpans).bucketStart();
        while ((subSpans.get(laggingSpans).bucketStart() == catchUpFromStart &&
                subSpans.get(laggingSpans).bucketEnd() < catchUpToEnd) || subSpans.get(laggingSpans).bucketStart() == -1) {
            if (subSpans.get(laggingSpans).nextBucket() == SpansInBuckets.NO_MORE_BUCKETS) {
                oneExhaustedInCurrentDoc = true;
                break;
            }
        }
    }

    boolean twoPhaseCurrentDocMatches() throws IOException {
        assert positionedInDoc();
        // Note that we DON't use our nextStartPosition() here because atFirstInCurrentDoc
        // is not properly set yet at this point in time (we do that below).
        atFirstInCurrentDoc = false;
        oneExhaustedInCurrentDoc = false;
        for (var spans: subSpans) {
            spans.nextBucket();
        }
        int start = synchronizePosition();
        if (start == NO_MORE_DOCS)
            return false;
        Arrays.fill(index, 0);
        atFirstInCurrentDoc = true;
        return true;
    }

    /**
     * Return a {@link TwoPhaseIterator} view of this Spans.
     */
    @Override
    public TwoPhaseIterator asTwoPhaseIterator() {
        float totalMatchCost = 0;
        // Compute the matchCost as the total matchCost/positionsCostant of the sub spans.
        for (SpansInBuckets spans : subSpans) {
            TwoPhaseIterator tpi = spans.asTwoPhaseIterator();
            totalMatchCost += tpi != null ? tpi.matchCost() : spans.positionsCost();
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

    @Override
    public void collect(SpanCollector collector) throws IOException {
        // not implemented, but not needed
    }

    @Override
    public void getMatchInfo(MatchInfo[] matchInfo) {
        for (int i = 0; i < subSpans.size(); i++) {
            subSpans.get(i).getMatchInfo(index[i], matchInfo);
        }
    }

    @Override
    public boolean hasMatchInfo() {
        return subSpans.stream().anyMatch(SpansInBuckets::hasMatchInfo);
    }

    @Override
    public RelationInfo getRelationInfo() {
        for (int i = 0; i < subSpans.size(); i++) {
            RelationInfo info = subSpans.get(i).getRelationInfo(index[i]);
            if (info != null)
                return info;
        }
        return null;
    }

    @Override
    protected void passHitQueryContextToClauses(HitQueryContext context) {
        for (SpansInBuckets subSpan: subSpans) {
            subSpan.setHitQueryContext(context);
        }
    }

    @Override
    public String toString() {
        return "AND-RELID([" + subSpans.stream().map(SpansInBucketsSameStartEnd::toString).collect(Collectors.joining(", ")) + "])";
    }

    @Override
    public int width() {
        return subSpans.stream().mapToInt(SpansInBuckets::width).sum();
    }

}
