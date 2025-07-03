package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

/**
 * Finds all sequences of consecutive hits from the in spans of the
 * specified min and max lengths. Used to implement repetition operators.
 * <p>
 * This generates all possible sequences of consecutive hits, so if we search
 * for B+ in the input string ABBBA, we'll get 3 hits of length 1, 2 hits of
 * length 2, and 1 hit of length 3. In the future, this should be made
 * configurable (to specifically support greedy matching, etc.)
 * <p>
 * This version can deal with overlapping hits, because it uses a startpoint
 * index and a recursive search for repetitions. This is slower than SpansRepetitionSimple,
 * which only looks at consecutive hits. If your hits don't overlap, use that instead.
 */
class SpansRepetition extends BLFilterDocsSpans<SpansInBucketsPerDocumentWithStartpointIndex> {
    /** Minimum number of repetitions */
    private final int min;

    /** Maximum number of repetitions */
    private final int max;

    /** Index in bucket of the first match involved in the current repetition.
        This will start at 0 and only increase, visiting each match in the bucket and trying to find repetitions from
        there. */
    private int matchStartIndex;

    /** All the valid end positions found for the current start position.
     *  These are determined by recursively finding connecting matches.
     */
    private final MutableIntList endPositions = new IntArrayList();

    /** Index in endPositions list (current end position). */
    private int endPositonIndex;

    /**
     * Whether we've already found our first match even though we weren't yet asked for it.
     * Usually this is because we need to determine if the document has any matches at all, or skip it otherwise.
     * If true, startPosition() will still return -1 and the next call to nextStartPosition() will return
     * the hit we've already found.
     */
    private boolean atFirstInCurrentDoc = false;

    /**
     * Construct SpansRepetition.
     * <p>
     * Source must be start-point sorted.
     *
     * @param source spans to find repetitions in
     * @param min minimum number of repetitions
     * @param max maximum number of repetitions
     */
    public SpansRepetition(BLSpans source, int min, int max) {
        // Find all consecutive matches in this Spans
        super(new SpansInBucketsPerDocumentWithStartpointIndex(source),
                SpanQueryRepetition.createGuarantees(source.guarantees(), min, max));
        this.min = min;
        this.max = max == -1 ? MAX_UNLIMITED : max;
        if (min > this.max)
            throw new IllegalArgumentException("min > max");
        if (min < 1)
            throw new IllegalArgumentException("min and max must be at least 1");
    }

    @Override
    public int nextDoc() throws IOException {
        assert docID() != NO_MORE_DOCS;
        atFirstInCurrentDoc = false;
        return super.nextDoc();
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

    @Override
    protected boolean twoPhaseCurrentDocMatches() throws IOException {
        assert positionedInDoc();
        // Does this document have any clause matches?
        matchStartIndex = -1;
        atFirstInCurrentDoc = false;
        if (in.nextBucket() == SpansInBuckets.NO_MORE_BUCKETS) {
            // Should never happen, because we should be in a document that has matches
            return false;
        }

        // Are any of the clause matches the start of a matching repetition?
        for (int i = 0; i < in.bucketSize(); i++) {
            if (findMatchesFromIndex(i)) {
                matchStartIndex = i;
                atFirstInCurrentDoc = true;
                return true;
            }
        }
        // We didn't find any matches
        return false;
    }

    /**
     * Find matches, if any, starting at the specified index in the current bucket.
     * <p>
     * This will fill the endPositions list with all valid end positions for the current start position.
     *
     * @param indexInBucket index in bucket to start looking for matches
     * @return true if matches were found, false if not
     */
    private boolean findMatchesFromIndex(int indexInBucket) {
        endPositions.clear();
        findEndpointsForRepetition(indexInBucket, 1);
        endPositions.sortThis(); // make sure endpoints are in order
        endPositonIndex = 0;
        return !endPositions.isEmpty();
    }

    /**
     * Find all valid end positions for a repetition starting at the specified position.
     *
     * @param fromIndex index in bucket to start looking for further repetitions
     * @param numberSoFar number of repetitions found so far
     */
    private void findEndpointsForRepetition(int fromIndex, int numberSoFar) {
        if (numberSoFar >= min && numberSoFar <= max) {
            // This is a valid repetition; add the end position
            endPositions.add(in.endPosition(fromIndex));
        }
        if (numberSoFar >= max) {
            // Don't continue looking for longer matches
            return;
        }
        // Find matches starting at the end of this match
        int fromPos = in.endPosition(fromIndex);
        long indexAndCount = in.indexAndCountForStartPoint(fromPos);
        int firstIndex = (int)(indexAndCount >> 32);
        int count = (int)indexAndCount;
        // Iterate over these matches and recursively call ourselves to find our repetitions
        for (int i = firstIndex; i < firstIndex + count; i++) {
            findEndpointsForRepetition(i, numberSoFar + 1);
        }
    }

    /**
     * Go to the next match.
     *
     * @return start position if we're on a valid match, NO_MORE_POSITIONS if we're done.
     */
    @Override
    public int nextStartPosition() throws IOException {
        assert startPosition() != NO_MORE_POSITIONS;
        // Are we done with this document?
        if (in.docID() == NO_MORE_DOCS)
            return NO_MORE_POSITIONS;

        // Did we already find the first match?
        if (atFirstInCurrentDoc) {
            // We're already at the first match in the document, because
            // we needed to check if there were matches at all. Return it now.
            atFirstInCurrentDoc = false;
            return in.startPosition(matchStartIndex);
        }

        // Are there more matches at this start position?
        if (!endPositions.isEmpty()) { // "we're not done with the doc yet"
            // Go to next new end position.
            // (endPositions is sorted but may contain duplicates because of how matching works;
            //  we skip any duplicates here)
            int curEndPos = endPositions.get(endPositonIndex);
            while (endPositonIndex < endPositions.size() - 1) {
                endPositonIndex++;
                if (endPositions.get(endPositonIndex) != curEndPos) {
                    // Yes, this is a valid and different end position, so we're at a new valid match
                    return in.startPosition(matchStartIndex);
                }
            }
            // No more matches at this start position.
            // Look for the next start position with matches.
            while (matchStartIndex < in.bucketSize() - 1) {
                matchStartIndex++;
                if (findMatchesFromIndex(matchStartIndex))
                    return in.startPosition(matchStartIndex);
            }
        }

        // No more matches
        // (we can tell we're not at a valid hit because endPositions is empty)
        endPositions.clear();
        endPositonIndex = -1;
        return NO_MORE_POSITIONS;
    }

    /**
     * @return start of the current hit
     */
    @Override
    public int startPosition() {
        if (atFirstInCurrentDoc || matchStartIndex == -1)
            return -1; // .nextStartPosition() not called yet
        if (endPositions.isEmpty())
            return NO_MORE_POSITIONS;
        return in.startPosition(matchStartIndex);
    }

    @Override
    public int endPosition() {
        if (atFirstInCurrentDoc || matchStartIndex == -1)
            return -1; // .nextStartPosition() not called yet
        if (endPositions.isEmpty())
            return NO_MORE_POSITIONS;
        return endPositions.get(endPositonIndex);
    }

    @Override
    public String toString() {
        return "SpansRepetition(" + in + ", " + min + ", " + BLSpanQuery.inf(max) + ")";
    }

    @Override
    public void getMatchInfo(MatchInfo[] matchInfo) {
        // NOTE: this uses the first match in the repetition for match info!
        in.getMatchInfo(matchStartIndex, matchInfo);
    }

    @Override
    public boolean hasMatchInfo() {
        return in.hasMatchInfo();
    }

    @Override
    public RelationInfo getRelationInfo() {
        return in.getRelationInfo(matchStartIndex);
    }
}
