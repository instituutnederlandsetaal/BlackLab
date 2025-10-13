package nl.inl.blacklab.search.lucene;

import java.io.IOException;

/**
 * A version of SpansRepetition that only looks at consecutive hits.
 *
 * Suitable if hits don't overlap. Otherwise, use SpansRepetition.
 */
class SpansRepetitionSimple extends BLFilterDocsSpans<SpansInBuckets> {
    boolean moreBuckets = false;

    private final int min;

    private final int max;

    private int firstToken;

    private int numRepetitions;

    private boolean atFirstInCurrentDoc = false;

    /**
     * Construct SpansRepetition.
     * <p>
     * Source must be start-point sorted and unique.
     *
     * @param source spans to find repetitions in
     * @param min minimum number of repetitions
     * @param max maximum number of repetitions
     */
    public SpansRepetitionSimple(BLSpans source, int min, int max) {
        // Find all consecutive matches in this Spans
        super(new SpansInBucketsConsecutive(source), SpanQueryRepetition.createGuarantees(source.guarantees(), min, max));
        this.min = min;
        this.max = max == -1 ? MAX_UNLIMITED : max;
        if (min > this.max)
            throw new IllegalArgumentException("min > max");
        if (min < 1)
            throw new IllegalArgumentException("min and max must be at least 1");
        if (!source.guarantees().hitsStartPointSorted())
            throw new IllegalArgumentException("Source is not start point sorted!");
        if (!source.guarantees().hitsHaveUniqueStartEnd())
            throw new IllegalArgumentException("Source does not guarantee unique starts/ends!");
    }

    @Override
    public int endPosition() {
        if (atFirstInCurrentDoc)
            return -1; // .nextStartPosition() not called yet
        if (!moreBuckets)
            return NO_MORE_POSITIONS;
        return in.endPosition(firstToken + numRepetitions - 1);
    }

    @Override
    public int nextDoc() throws IOException {
        assert docID() != NO_MORE_DOCS;
        atFirstInCurrentDoc = false;
        return super.nextDoc();
    }

    @Override
    protected boolean twoPhaseCurrentDocMatches() throws IOException {
        atFirstInCurrentDoc = false;
        assert positionedInDoc();
        // See if there's a bucket of matches in this doc
        moreBuckets = nextBucket() != SpansInBuckets.NO_MORE_BUCKETS;
        if (moreBuckets) {
            atFirstInCurrentDoc = true;
            return true;
        }

        // No more matching buckets.
        return false;
    }

    /**
     * Go to the next matching bucket in the current doc, if it has any.
     *
     * @return the start position of the bucket, or NO_MORE_BUCKETS if there's no
     *         more matching buckets
     */
    private int nextBucket() throws IOException {
        moreBuckets = in.nextBucket() != SpansInBuckets.NO_MORE_BUCKETS;
        while (moreBuckets) {
            if (in.bucketSize() >= min) {
                // This stretch is large enough to get a repetition hit;
                // Position us at the first hit and remember we're already there.
                firstToken = 0;
                numRepetitions = min;
                return in.startPosition(firstToken);
            }
            // Not large enough; try next bucket
            moreBuckets = in.nextBucket() != SpansInBuckets.NO_MORE_BUCKETS;
        }
        return SpansInBuckets.NO_MORE_BUCKETS;
    }

    /**
     * Go to the next match.
     *
     * @return start position if we're on a valid match, NO_MORE_POSITIONS if we're done.
     */
    @Override
    public int nextStartPosition() throws IOException {
        assert startPosition() != NO_MORE_POSITIONS;
        if (in.docID() == NO_MORE_DOCS || !moreBuckets)
            return NO_MORE_POSITIONS;

        if (atFirstInCurrentDoc) {
            // We're already at the first match in the document, because
            // we needed to check if there were matches at all. Return it now.
            atFirstInCurrentDoc = false;
            return in.startPosition(firstToken);
        }

        // Go to the next hit length for this start point in the current bucket.
        numRepetitions++;

        // Find the first valid hit in the bucket
        if (numRepetitions > max || firstToken + numRepetitions > in.bucketSize()) {
            // On to the next start point.
            firstToken++;
            numRepetitions = min;
        }

        if (firstToken + numRepetitions <= in.bucketSize()) {
            // Still a valid rep. hit.
            return in.startPosition(firstToken);
        }

        // No valid hits left; on to the next matching bucket
        int startPos = nextBucket();
        moreBuckets = startPos != SpansInBuckets.NO_MORE_BUCKETS;
        if (moreBuckets) {
            return startPos;
        }

        // No more matching buckets.
        return NO_MORE_POSITIONS;
    }

    /**
     * Go to the specified document, if it has hits. If not, go to the next document
     * containing hits.
     *
     * @param target the document number to skip to / over
     * @return start position if we're on a valid match, NO_MORE_POSITIONS if we're done.
     */
    @Override
    public int advance(int target) throws IOException {
        assert target >= 0 && target > docID();
        atFirstInCurrentDoc = false;
        return super.advance(target);
    }

    /**
     * @return start of the current hit
     */
    @Override
    public int startPosition() {
        if (atFirstInCurrentDoc)
            return -1; // .nextStartPosition() not called yet
        if (!moreBuckets)
            return NO_MORE_POSITIONS;
        return in.startPosition(firstToken);
    }

    @Override
    public String toString() {
        return "SpansRepetitionSimple(" + in + ", " + min + ", " + BLSpanQuery.inf(max) + ")";
    }

    @Override
    public void getMatchInfo(MatchInfo[] matchInfo) {
        // NOTE: this uses the first match in the repetition for match info!
        // (for the last match, we would use firstToken + numRepetitions - 1)
        in.getMatchInfo(firstToken, matchInfo);
    }

    @Override
    public boolean hasMatchInfo() {
        return in.hasMatchInfo();
    }

    @Override
    public RelationInfo getRelationInfo() {
        // NOTE: this uses the first match in the repetition for match info!
        // (for the last match, we would use firstToken + numRepetitions - 1)
        return in.getRelationInfo(firstToken);
    }
}
