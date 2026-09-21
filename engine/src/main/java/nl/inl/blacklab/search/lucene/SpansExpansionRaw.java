package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.index.LeafReader;

import nl.inl.blacklab.search.lucene.SpanQueryExpansion.Direction;

/**
 * Expands the source spans to the left and right by the given ranges.
 *
 * This is used to support sequences including subsequences of completely
 * unknown tokens (like <code>"apple" []{2,4} "pear"</code> to find apple and
 * pear with 2 to 4 tokens in between).
 *
 * Note that this class will generate all possible expansions, so if you call it
 * with left-expansion of between 2 to 4 tokens, it will generate 3 new hits for
 * every hit from the source spans: one hit with 2 more tokens to the left, one
 * hit with 3 more tokens to the left, and one hit with 4 more tokens to the
 * left.
 *
 * Note that the hits coming out of this class may contain duplicates and will
 * not always be properly sorted. Both are undesirable; the user doesn't want to
 * see duplicates, and Lucene expects Spans to always be sorted by start point
 * (and possible by end point after that, although that is not certain; should
 * be checked).
 *
 * Therefore, objects of this class should be wrapped in a class that sort the
 * matches per document and eliminates duplicates (hence the 'raw' in the name -
 * not suitable for consumption yet).
 */
class SpansExpansionRaw extends BLFilterDocsSpans<BLSpans> {

    /** Current startPosition() in the clause */
    private int clauseStart = -1;

    /** Whether to expand to left or right */
    private final Direction direction;

    /** Minimum number of tokens to expand */
    private final int min;

    /** Maximum number of tokens to expand (MAX_UNLIMITED = infinite) */
    private final int max;

    /** Start of the current expanded hit */
    private int start = -1;

    /** End of the current expanded hit */
    private int end = -1;

    /** Number of expansion steps left to do for current clause hit */
    private int expandStepsLeft = 0;

    /** For which document do we have the token length? */
    private int tokenLengthDocId = -1;

    /** Token length for the doc specified in tokenLengthDocId */
    private int tokenLength;

    /** Used to get the field length in tokens for a document */
    private DocFieldLengthGetter lengthGetter;

    private boolean atFirstInCurrentDoc;

    public SpansExpansionRaw(LeafReader reader, String fieldName, BLSpans clause,
            Direction direction, int min, int max) {
        super(clause, SpanQueryExpansion.createGuarantees(clause.guarantees(), direction, min, max));
        if (direction == Direction.RIGHT) {
            // We need to know document length to properly do expansion to the right
            lengthGetter = new DocFieldLengthGetter(reader, fieldName);
        }
        this.direction = direction;
        this.min = min;
        this.max = max == -1 ? MAX_UNLIMITED : max;
        checkMinMax();
    }

    public SpansExpansionRaw(DocFieldLengthGetter lengthGetter, BLSpans clause,
            Direction direction, int min, int max) {
        super(clause, SpanQueryExpansion.createGuarantees(clause.guarantees(), Direction.RIGHT, min, max));
        if (direction == Direction.RIGHT) {
            // We need to know document length to properly do expansion to the right
            // OPT: cache this in BlackLabIndex..?
            this.lengthGetter = lengthGetter;
        }
        this.direction = direction;
        this.min = min;
        this.max = max == -1 ? MAX_UNLIMITED : max;
        checkMinMax();
    }

    private void checkMinMax() {
        if (min > this.max)
            throw new IllegalArgumentException("min > max");
        if (min < 0)
            throw new IllegalArgumentException("Expansions cannot be negative");
    }

    /**
     * For test, we don't have an index reader, so use default values (all docs are
     * 5 tokens long)
     *
     * @param b true if we're testing, false if not
     */
    public void setTest(boolean b) {
        if (lengthGetter != null)
            lengthGetter.setTest(b);
    }

    @Override
    public int nextDoc() throws IOException {
        assert docID() != NO_MORE_DOCS;
        atFirstInCurrentDoc = false;
        return super.nextDoc();
    }

    @Override
    public int advance(int target) throws IOException {
        assert target >= 0 && target > docID();
        atFirstInCurrentDoc = false;
        return super.advance(target);
    }

    @Override
    protected boolean twoPhaseCurrentDocMatches() throws IOException {
        assert positionedInDoc();
        atFirstInCurrentDoc = false;
        // Are there search results in this document?
        clauseStart = in.nextStartPosition();
        resetExpand();
        atFirstInCurrentDoc = clauseStart != NO_MORE_POSITIONS;
        return atFirstInCurrentDoc;
    }

    @Override
    public int nextStartPosition() throws IOException {
        assert startPosition() != NO_MORE_POSITIONS;
        if (atFirstInCurrentDoc) {
            atFirstInCurrentDoc = false;
            return start;
        }
        if (in.docID() == NO_MORE_DOCS)
            return NO_MORE_POSITIONS;
        if (clauseStart == NO_MORE_POSITIONS)
            return NO_MORE_POSITIONS;

        if (expandStepsLeft > 0) {
            expandStepsLeft--;
            if (direction == Direction.LEFT) {
                start--;

                // Valid expansion?
                if (start >= 0)
                    return start;

                // Can't finish the expansion because we're at the start
                // of the document; go to next hit.
            } else {
                end++;
                return start;
            }
        }

        clauseStart = in.nextStartPosition();
        resetExpand();
        return start;
    }

    @Override
    public int advanceStartPosition(int target) throws IOException {
        assert target > startPosition();
        if (atFirstInCurrentDoc) {
            atFirstInCurrentDoc = false;
            if (start >= target)
                return start;
        }
        if (in.docID() == NO_MORE_DOCS)
            return NO_MORE_POSITIONS;
        if (clauseStart == NO_MORE_POSITIONS)
            return NO_MORE_POSITIONS;

        if (expandStepsLeft > 0 && start >= target)
            return nextStartPosition(); // we're already there

        // If we're expanding to the left, adjust the target to skip positions in the clause that cannot produce
        // any hits with start >= target. Note that we may still produce hits with start < target because of max;
        // but this is inherent with this method if your hits are not startpoint sorted; callers should be aware of this.
        int adjustedTarget = direction == Direction.LEFT ? target + min : target;
        clauseStart = in.advanceStartPosition(adjustedTarget);
        resetExpand();
        return start;
    }

    /**
     * We're at a new position in the source span. Reset the expansion process.
     *
     * Note that we may discover we can't do the minimum expansion (because the hit
     * is at the start of the document, for example), so we may have to advance the
     * clause again, and may actually run out of hits while doing so.
     *
     * Updates clauseStart to the start position if we're at a valid hit and have
     * reset the expansion, or to NO_MORE_POSITIONS if we're done.
     *
     */
    private void resetExpand() throws IOException {
        if (clauseStart == NO_MORE_POSITIONS) {
            start = end = NO_MORE_POSITIONS;
            return;
        }
        while (true) {
            // Attempt to do the initial expansion and reset the counter
            start = clauseStart;
            end = in.endPosition();
            if (direction == Direction.LEFT)
                start -= min;
            else
                end += min;

            // What's the maximum we could still expand from here?
            int maxExpandSteps;
            if (direction == Direction.LEFT) {
                // Can only expand to the left until token 0.
                maxExpandSteps = start;
            } else {
                // Can only expand to the right until last token in document.

                // Do we know this document's length already?
                if (in.docID() != tokenLengthDocId) {
                    // No, determine length now
                    tokenLengthDocId = in.docID();
                    tokenLength = lengthGetter.getFieldLength(tokenLengthDocId);
                }
                maxExpandSteps = tokenLength - end;
            }
            if (max == MAX_UNLIMITED) {
                // Infinite expansion; just use max
                expandStepsLeft = maxExpandSteps;
            } else {
                // Limited expansion; clamp by maximum
                expandStepsLeft = Math.min(max - min, maxExpandSteps);
            }

            // Valid expansion?   [shouldn't be necessary anymore because we calculated max]
            if (expandStepsLeft >= 0) {
                clauseStart = start; // Yes, return
                return;
            }

            // No, try the next hit, if there is one
            clauseStart = in.nextStartPosition();
            if (clauseStart == NO_MORE_POSITIONS) {
                start = end = NO_MORE_POSITIONS;
                return; // No hits left, we're done
            }
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
    public int endPosition() {
        if (atFirstInCurrentDoc)
            return -1; // .nextStartPosition() not called yet
        return end;
    }

    @Override
    public String toString() {
        return "SpansExpansion(" + in + ", " + direction + ", " + min + ", " + BLSpanQuery.inf(max) + ")";
    }

    public Direction direction() {
        return direction;
    }

    public BLSpans clause() {
        return in;
    }

    public SequenceGap gap() {
        return SequenceGap.variable(min, max);
    }

    public DocFieldLengthGetter lengthGetter() {
        return lengthGetter;
    }
}
