package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

import org.apache.lucene.search.ConjunctionUtils;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.queries.spans.SpanCollector;
import org.apache.lucene.queries.spans.Spans;

/**
 * Combines spans, keeping only combinations of hits that occur one after the
 * other. The order is significant: a hit from the first span must be followed
 * by a hit from the second.
 *
 * There are different Spans classes that deal with variations of this problem;
 * this one deals with clauses with a "gap" in the middle, and doesn't sort the 
 * first clause by endpoint, making it potentially faster than other versions.
 *
 * (it doesn't need to sort the first clause by end point because it keeps track
 *  of the first index in the bucket for the second clause where a match could
 *  connect)
 *
 * It has to take the following problem into account, which might arise with
 * more complex sequences with overlapping hits ("1234" are token positions in
 * the document, A-C are hits in spans1, D-F are hits in spans2, "AD", "AE" and
 * "BF" are resulting sequence hits):
 *
 * <pre>
 *  spans1       1234
 *       A(1-2)  -
 *       B(1-3)  --
 *       C(2-4)   --
 *
 *  spans2       1234
 *       D(2-4)   --
 *       E(2-5)   ---
 *       F(3-4)    -
 *
 *  seq(1,2)     1234
 *       AD(1-4) ---
 *       AE(1-5) ----
 *       BF(1-4) ---
 * </pre>
 *
 * Note that the sequence of the two spans contains duplicates (AD and BF are
 * identical) and out-of-order endpoints (AE ends at 5 but BF ends at 4). Both
 * are undesirable; the user doesn't want to see duplicates, and out-of-order
 * endpoints may cause problems when combining this spans with other spans
 * (although that is not certain; should be checked).
 *
 * Therefore, objects of this class should be wrapped in a class that sort the
 * matches per document and eliminates duplicates.
 */
class SpansSequenceWithGap extends BLSpans {

    private static SpanGuarantees createGuarantees(SpanGuarantees first, SequenceGap gap, SpanGuarantees second) {
        return new SpanGuarantees() {
            @Override
            public boolean hitsHaveUniqueStart() {
                return first.hitsHaveUniqueStartEnd() && gap.isFixed() && second.hitsHaveUniqueStart();
            }

            @Override
            public boolean hitsHaveUniqueEnd() {
                return first.hitsHaveUniqueEnd() && gap.isFixed() && second.hitsHaveUniqueStartEnd();
            }

            @Override
            public boolean isSingleAnyToken() {
                return false;
            }

            @Override
            public boolean hitsStartPointSorted() {
                return true;
            }

            @Override
            public boolean hitsEndPointSorted() {
                return first.hitsEndPointSorted() && gap.isFixed() && second.hitsAllSameLength();
            }

            @Override
            public boolean okayToInvertForOptimization() {
                return false;
            }

            @Override
            public boolean isSingleTokenNot() {
                return false;
            }

            @Override
            public boolean hitsAllSameLength() {
                return first.hitsAllSameLength() && gap.isFixed() && second.hitsAllSameLength();
            }

            @Override
            public int hitsLengthMin() {
                return first.hitsLengthMin() + gap.minSize() + second.hitsLengthMin();
            }

            @Override
            public int hitsLengthMax() {
                return first.hitsLengthMax() + gap.maxSize() + second.hitsLengthMax();
            }
        };
    }

    /** First clause matches, sorted by start point.
     *  (you would think this needs to be sorted by end point, but we take this into account, see above) */
    private final BLSpans first;
    
    /** Gap between the two clauses. */
    private final SequenceGap gap;

    /** Second clause matches, collected for the whole document, sorted by startpoint. */
    private final SpansInBucketsPerDocument second;

    /** Approximation for two-phase iterator */
    private final DocIdSetIterator conjunction;

    /**
     * First index in the second clause bucket that we could possibly connect to a span from the first
     * clause with the current start position.
     * 
     * This is based on the *start* position of the first clause, not the end position, because
     * the start position is guaranteed to always rise, but the end position could go down between
     * two spans (e.g. if a short span follows a long span, and the short span is actually contained
     * by the long span).
     * 
     * We will remember this index and iterate forward to see if multiple spans from the second
     * clause can be matched with a given span from the first. Then we will start from this index again
     * for the next match from the first clause, etc.
     */
    int indexFirstPossibleSecondClauseMatch = -1;

    /**
     * Index in the second clause's bucket that we've match to the end of the current span from the first clause.
     */
    int indexCurrentSecondClauseMatch = -1;

    /** Start of current match in first clause */
    int firstStart = -1;

    /** End of current match in second clause */
    int secondEnd = -1;

    /**
     * Are we already a the first match in the document, even if
     * .nextStartPosition() hasn't been called? Required because we need to test for
     * matches in the document in .nextDoc()/.advance().
     */
    private boolean atFirstInCurrentDoc = false;

    /** Highest acceptable value for start of second clause match given the current
     *  first clause end and allowable gap. */
    private int secondStartLast;

    /**
     * Construct SpansSequenceWithGap.
     *
     * @param first first clause
     * @param gap allowable gap between the clauses
     * @param second second clause
     */
    SpansSequenceWithGap(BLSpans first, SequenceGap gap, BLSpans second) {
        super(createGuarantees(first.guarantees(), gap, second.guarantees()));

        this.first = BLSpans.ensureSorted(first);
        this.gap = gap;
        this.second = SpansInBucketsPerDocument.sorted(second);
        this.conjunction = ConjunctionUtils.intersectIterators(List.of(this.first, this.second));
    }

    @Override
    public int docID() {
        return conjunction.docID();
    }

    @Override
    public int endPosition() {
        if (atFirstInCurrentDoc)
            return -1; // .nextStartPosition() not called yet
        return secondEnd;
    }

    @Override
    public int nextDoc() throws IOException {
        assert docID() != NO_MORE_DOCS;
        atFirstInCurrentDoc = false;
        int doc = conjunction.nextDoc();
        while (doc != NO_MORE_DOCS && !twoPhaseCurrentDocMatches()) {
            doc = conjunction.nextDoc();
        }
        assert Stream.of(first, second).allMatch(a -> a.docID() == conjunction.docID());
        return doc;
    }

    @Override
    public int advance(int target) throws IOException {
        assert target >= 0 && target > docID();
        atFirstInCurrentDoc = false;
        int doc = conjunction.advance(target);
        while (doc != NO_MORE_DOCS && !twoPhaseCurrentDocMatches()) {
            doc = conjunction.nextDoc();
        }
        assert Stream.of(first, second).allMatch(a -> a.docID() == conjunction.docID());
        return doc;
    }

    private boolean twoPhaseCurrentDocMatches() throws IOException {
        atFirstInCurrentDoc = false;
        assert positionedInDoc();
        // Does this doc have any matches?
        assert first.startPosition() == -1;
        firstStart = first.nextStartPosition();
        if (firstStart == NO_MORE_POSITIONS) {
            secondEnd = NO_MORE_POSITIONS;
            return false;
        }
        assert firstStart >= 0;
        second.nextBucket();
        indexFirstPossibleSecondClauseMatch = -1;
        indexCurrentSecondClauseMatch = -1;
        secondEnd = -1;
        realignPos();
        if (firstStart != NO_MORE_POSITIONS) {
            // Yes. Remember that we're already on the first match
            atFirstInCurrentDoc = true;
            assert firstStart >= 0;
            return true;
        }
        return false;
    }

    @Override
    public int nextStartPosition() throws IOException {
        assert startPosition() != NO_MORE_POSITIONS;
        
        // Preconditions:
        // - first and second are in the same document
        // - first clause is at a valid span
        // - second clause has a bucket available; indexFirstPossibleSecondClauseMatch and
        //   indexCurrentSecondClauseMatch have been set
        
        // Did we already find the first match?
        if (atFirstInCurrentDoc) {
            atFirstInCurrentDoc = false;
            assert firstStart >= 0;
            return firstStart;
        }

        // Are we done with this document?
        if (firstStart == NO_MORE_POSITIONS) {
            secondEnd = NO_MORE_POSITIONS;
            return NO_MORE_POSITIONS;
        }
        
        /*
         * Go to the next match.
         * 
         * A match is the current first clause span combined with a span from the second clause's bucket whose
         * startpoint matches the endpoint of the current first clause span.
         * 
         * Going to the next match therefore means:
         * - going to the next span in the second clause bucket (if that matches the endpoint of the current first
         *   clause span), OR
         * - going to the next first clause span and seeing if that has any matches in the second clause bucket
         */
        
        // See if there's another match with the same first clause span and a new second clause span.
        indexCurrentSecondClauseMatch++;
        if (indexCurrentSecondClauseMatch < second.bucketSize()) {
            // At next span in the bucket. Does this match?
            if (second.startPosition(indexCurrentSecondClauseMatch) <= secondStartLast) {
                // Yes! Report the new match.
                secondEnd = second.endPosition(indexCurrentSecondClauseMatch);
                return firstStart;
            }
        }
        // No more matches, end of bucket, or no bucket yet.
        // Find a new first clause span and corresponding second clause span(s).
        firstStart = first.nextStartPosition();
        realignPos();
        return firstStart;
    }

    /**
     * Restores the property that the current first clause match ends where the current
     * second clause matches begin.
     *
     * The spans are assumed to be already in the same doc. It is also assumed that 
     * the first clause has just been advanced to a new position.
     * 
     * We will start by seeing if the second clause bucket contains matches for this new first
     * clause span. If not, we will advance the first clause and repeat until we've
     * found a match, or are out of matches.
     * 
     * After this function, we're on the first valid match found, or we're out of
     * matches for this document.
     *
     */
    private void realignPos() throws IOException {
        while (true) {
            if (firstStart == NO_MORE_POSITIONS) {
                secondEnd = NO_MORE_POSITIONS;
                return;
            }
            
            // Where should the second clause start?
            // - firstPossibleSecondClauseMatchPosition: we never need to look at matches in second clause that start
            //                                           before this position
            int firstPossibleSecondClauseMatchPosition = firstStart + gap.minSize();
            // - currentSecondClauseMatchPosition: for current first clause match, this is the start position in the
            //                                     second clause
            int currentSecondClauseMatchPosition = first.endPosition() + gap.minSize();
            secondStartLast = gap.maxSize() == MAX_UNLIMITED ? MAX_UNLIMITED : first.endPosition() + gap.maxSize();
            
            // Do we need to advance the starting point in the second clause's bucket?
            // First, position indexFirstPossibleSecondClauseMatch according to firstPossibleSecondClauseMatchPosition.
            // (because first.endPosition() is not guaranteed to always rise, but first.startPosition() is)
            while (indexFirstPossibleSecondClauseMatch < second.bucketSize() && (
                    indexFirstPossibleSecondClauseMatch < 0 || second.startPosition(indexFirstPossibleSecondClauseMatch) < firstPossibleSecondClauseMatchPosition)) {
                indexFirstPossibleSecondClauseMatch++;
            }
            if (indexFirstPossibleSecondClauseMatch < second.bucketSize()) {
                // Found a valid position for indexInBucket.
                // Next, position indexCurrentSecondClauseMatch according to indexFirstPossibleSecondClauseMatch.
                // (This represents the actual first span in the second clause's bucket that can match our current
                //  first clause span)
                indexCurrentSecondClauseMatch = indexFirstPossibleSecondClauseMatch;
                while (indexCurrentSecondClauseMatch < second.bucketSize() && second.startPosition(
                        indexCurrentSecondClauseMatch) < currentSecondClauseMatchPosition) {
                    indexCurrentSecondClauseMatch++;
                }
                if (indexCurrentSecondClauseMatch < second.bucketSize() && second.startPosition(
                        indexCurrentSecondClauseMatch) <= secondStartLast) {
                    // Found the first matching second clause span for this first clause span. Return the sequence span.
                    secondEnd = second.endPosition(indexCurrentSecondClauseMatch);
                    return;
                }
            }
            
            // Advance the first clause.
            firstStart = first.nextStartPosition();
        }
    }

    /**
     * @return start of the current hit
     */
    @Override
    public int startPosition() {
        if (atFirstInCurrentDoc)
            return -1; // .nextStartPosition() not called yet
        return firstStart;
    }

    @Override
    public String toString() {
        return "SeqGap(" + first + ", " + gap + ", " + second + ")";
    }

    @Override
    public void passHitQueryContextToClauses(HitQueryContext context) {
        first.setHitQueryContext(context);
        second.setHitQueryContext(context);
    }

    @Override
    public void getMatchInfo(MatchInfo[] matchInfo) {
        if (!childClausesCaptureMatchInfo)
            return;
        first.getMatchInfo(matchInfo);
        second.getMatchInfo(indexCurrentSecondClauseMatch, matchInfo);
    }

    @Override
    public boolean hasMatchInfo() {
        return first.hasMatchInfo() || second.hasMatchInfo();
    }

    @Override
    public RelationInfo getRelationInfo() {
        RelationInfo info = first.getRelationInfo();
        return info == null ? second.getRelationInfo(indexCurrentSecondClauseMatch) : info;
    }

    @Override
    public int width() {
        return first.width() + second.width();
    }

    @Override
    public void collect(SpanCollector collector) throws IOException {
        first.collect(collector);
        //second.collect(collector); should probably be called as well, but not implemented, and not necessary for now
        // (we only use payloads in SpansRelations)
    }

    @Override
    public float positionsCost() {
        throw new UnsupportedOperationException(); // asTwoPhaseIterator never returns null here.
    }

    @Override
    public TwoPhaseIterator asTwoPhaseIterator() {
        float totalMatchCost = 0;
        // Compute the matchCost as the total matchCost/positionsCostant of the sub spans.
        for (DocIdSetIterator iter : List.of(first, second)) {
            TwoPhaseIterator tpi = iter instanceof Spans ? ((Spans) iter).asTwoPhaseIterator() : null;
            if (tpi != null) {
                totalMatchCost += tpi.matchCost();
            } else {
                totalMatchCost += iter instanceof Spans ? ((Spans)iter).positionsCost() : iter.cost();
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
}
