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
 * Combines overlapping hits from two spans in various ways.
 */
class SpansOverlapping extends BLSpans {

    /** Operation to perform on spans to get resulting spans */
    public interface Operation {
        static Operation fromStringValue(String value) {
            if (value.equalsIgnoreCase("OVERLAP"))
                return OVERLAP;
            throw new IllegalArgumentException("Unknown operation: " + value);
        }

        /** Start position of resulting hit, or -1 to reject. */
        int start(int start1, int end1, int start2, int end2);

        /** End position of resulting hit, or -1 to reject. */
        int end(int start1, int end1, int start2, int end2);

        /** Guarantees given by this operation */
        default SpanGuarantees createGuarantees(SpanGuarantees a, SpanGuarantees b) {
            return SpanGuarantees.NONE;
        }

        String toString();
    }

    /** Find the overlap between hits from two spans */
    public static Operation OVERLAP = new Operation() {
        @Override
        public int start(int start1, int end1, int start2, int end2) {
            return Math.max(start1, start2);
        }

        @Override
        public int end(int start1, int end1, int start2, int end2) {
            return Math.min(end1, end2);
        }

        @Override
        public SpanGuarantees createGuarantees(SpanGuarantees a, SpanGuarantees b) {
            return new SpanGuaranteesAdapter() {
                @Override
                public boolean producesSingleTokens() {
                    return a.producesSingleTokens() && b.producesSingleTokens();
                }

                @Override
                public boolean hitsAllSameLength() {
                    return producesSingleTokens();
                }

                @Override
                public int hitsLengthMin() {
                    return 1;
                }

                @Override
                public int hitsLengthMax() {
                    return Math.min(a.hitsLengthMax(), b.hitsLengthMax());
                }
            };
        }

        @Override
        public String toString() {
            return "INTERSECTION";
        }
    };

    /** First clause matches, sorted by start point.
     *  (you would think this needs to be sorted by end point, but we take this into account, see above) */
    private final BLSpans first;

    /** Second clause matches, collected for the whole document, sorted by startpoint. */
    private final SpansInBuckets second;

    /** How to calculate the resulting span (i.e. intersection, union, etc.) */
    private final Operation operation;

    /** Approximation for two-phase iterator */
    private final DocIdSetIterator conjunction;

    /** Index in the second clause's bucket */
    int indexCurrentSecondClauseMatch = -1;

    /** Start of current resulting match */
    int start = -1;

    /** End of current resulting match */
    int end = -1;

    /**
     * Are we already at the first match in the document, even if
     * .nextStartPosition() hasn't been called? Required because we need to test for
     * matches in the document in .nextDoc()/.advance().
     */
    private boolean atFirstInCurrentDoc = false;

    /**
     * Construct SpansOverlapping.
     *
     * @param first first clause
     * @param second second clause
     * @param operation operation to perform on clause hits
     */
    SpansOverlapping(BLSpans first, BLSpans second, Operation operation) {
        super(operation.createGuarantees(first.guarantees(), second.guarantees()));
        this.first = BLSpans.ensureSorted(first);
        this.second = SpansInBucketsPerDocument.sorted(second);
        this.operation = operation;
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
        return end;
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
        int firstStart = first.nextStartPosition();
        if (firstStart == NO_MORE_POSITIONS) {
            start = end = NO_MORE_POSITIONS;
            return false;
        }
        assert firstStart >= 0;
        second.nextBucket();
        indexCurrentSecondClauseMatch = 0;
        start = end = -1;
        realignPos();
        if (start != NO_MORE_POSITIONS) {
            // Yes. Remember that we're already on the first match
            atFirstInCurrentDoc = true;
            assert start >= 0;
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
            assert start >= 0;
            return start;
        }

        // Are we done with this document?
        if (start == NO_MORE_POSITIONS) {
            end = NO_MORE_POSITIONS;
            return NO_MORE_POSITIONS;
        }
        
        /*
         * Go to the next match.
         * 
         * A match is the current first clause span combined with an overlapping span from the second clause's bucket.
         * 
         * Going to the next match therefore means:
         * - going to the next span in the second clause bucket (if that still overlaps the first), OR
         * - going to the next first clause span and seeing if that has any overlapping matches in the second clause
         *   bucket
         */
        
        // See if there's another overlapping match in the second clause's bucket.
        indexCurrentSecondClauseMatch++;
        return realignPos();
    }

    /**
     * Restores the property that the current first clause match overlaps with the current
     * second clause match.
     *
     * The spans are assumed to be already in the same doc. It is also assumed that 
     * the first clause has just been advanced to a new position.
     * 
     * We will start by seeing if the second clause bucket contains overlapping matches for
     * this new first clause span. If not, we will advance the first clause and repeat until
     * we've found a match, or are out of matches.
     * 
     * After this function, we're on the first valid match found, or we're out of
     * matches for this document.
     *
     */
    private int realignPos() throws IOException {
        if (first.startPosition() == NO_MORE_POSITIONS) {
            start = end = NO_MORE_POSITIONS;
            return start;
        }
        while (true) {
            if (indexCurrentSecondClauseMatch >= second.bucketSize()) {
                // End of bucket. Advance first clause.
                if (first.nextStartPosition() == NO_MORE_POSITIONS) {
                    // No more matches in first clause. Done with this document.
                    start = end = NO_MORE_POSITIONS;
                    return start;
                }
                indexCurrentSecondClauseMatch = 0;
                continue;
            }
            if (second.startPosition(indexCurrentSecondClauseMatch) < first.endPosition() &&
                    second.endPosition(indexCurrentSecondClauseMatch) > first.startPosition()) {
                // These spans overlap. Calculate and report the new match.
                start = operation.start(first.startPosition(), first.endPosition(), second.startPosition(indexCurrentSecondClauseMatch), second.endPosition(indexCurrentSecondClauseMatch));
                end = operation.end(first.startPosition(), first.endPosition(), second.startPosition(indexCurrentSecondClauseMatch), second.endPosition(indexCurrentSecondClauseMatch));
                return start;
            }
            indexCurrentSecondClauseMatch++;
        }
    }

    /**
     * @return start of the current hit
     */
    @Override
    public int startPosition() {
        if (atFirstInCurrentDoc)
            return -1; // .nextStartPosition() not called yet
        return start;
    }

    @Override
    public String toString() {
        return "SpansOverlapping(" + first + ", " + ", " + second + ", " + operation + ")";
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
