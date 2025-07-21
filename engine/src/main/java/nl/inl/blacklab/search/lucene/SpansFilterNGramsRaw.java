package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.queries.spans.SpanCollector;

import nl.inl.blacklab.search.BlackLabIndexAbstract;

/**
 * Expands the source spans to the left and right to form N-grams.
 *
 * This is used to speed up queries of the form
 * <code>[]{2,4} containing "a" "b"</code>: N-grams of certain lengths
 * containing some construct.
 *
 * Note that this class will generate all possible N-grams, so it will likely
 * produce multiple N-gram hits per source hit.
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
class SpansFilterNGramsRaw extends BLFilterDocsSpans<BLSpans> {

    /** Current startPosition() in the clause */
    private int srcStart = -1;

    /** End of the current source hit */
    private int srcEnd = -1;

    /** How to expand the hits */
    private final SpanQueryPositionFilter.Operation op;

    /** Minimum number of tokens to expand */
    private final int min;

    /** Maximum number of tokens to expand (MAX_UNLIMITED = infinite) */
    private final int max;

    /** How to adjust the left edge of the producer hits (N-grams) while matching */
    private final int leftAdjust;

    /** How to adjust the right edge of the producer hits (N-grams) while matching */
    private final int rightAdjust;

    /** Start of the current expanded hit */
    private int start = -1;

    /** End of the current expanded hit */
    private int end = -1;

    /** For which document do we have the token length? */
    private int tokenLengthDocId = -1;

    /** Token length for the doc specified in tokenLengthDocId */
    private int tokenLength;

    /** Used to get the field length in tokens for a document */
    DocFieldLengthGetter lengthGetter;

    private boolean atFirstInCurrentDoc;

    public SpansFilterNGramsRaw(LeafReader reader, String fieldName, BLSpans clause,
            SpanQueryPositionFilter.Operation op, int min, int max, int leftAdjust, int rightAdjust) {
        super(clause, SpanQueryFilterNGrams.createGuarantees(clause.guarantees(), min, max));
        if (op != SpanQueryPositionFilter.Operation.CONTAINING_AT_END && op != SpanQueryPositionFilter.Operation.ENDS_AT
                && op != SpanQueryPositionFilter.Operation.MATCHES) {
            // We need to know document length to properly do expansion to the right
            // OPT: cache this in BlackLabIndex..?
            lengthGetter = new DocFieldLengthGetter(reader, fieldName);
        }
        this.op = op;
        this.min = min;
        this.max = max == -1 ? MAX_UNLIMITED : max;
        if (min > this.max)
            throw new IllegalArgumentException("min > max");
        if (min < 0)
            throw new IllegalArgumentException("Expansions cannot be negative");
        this.leftAdjust = leftAdjust;
        this.rightAdjust = rightAdjust;
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
    public int endPosition() {
        if (atFirstInCurrentDoc)
            return -1; // .nextStartPosition() not called yet
        return end;
    }

    @Override
    public int nextDoc() throws IOException {
        assert docID() != NO_MORE_DOCS;
        atFirstInCurrentDoc = false;
        return super.nextDoc();
    }

    @Override
    public int nextStartPosition() throws IOException {
        assert startPosition() != NO_MORE_POSITIONS;
        if (atFirstInCurrentDoc) {
            atFirstInCurrentDoc = false;
            return start;
        }
        if (in.docID() == NO_MORE_DOCS || srcStart == NO_MORE_POSITIONS)
            return NO_MORE_POSITIONS;

        // Is there another n-gram for this source hit?
        boolean atValidNGram = false;
        switch (op) {
        case CONTAINING:
            end++;
            while (true) {
                if (end - start <= max && end <= tokenLength) {
                    // N-gram is within allowed size and not beyond end of document
                    atValidNGram = true;
                    break;
                }
                // N-gram became too long, or we went past end of document.
                // On to next start position.
                start++;
                if (start + leftAdjust > srcStart) {
                    // No more start positions for N-gram; done with this source hit.
                    break;
                }
                end = Math.max(srcEnd - rightAdjust, start + min); // minimum length still containing source hit
                if (end > tokenLength) {
                    // Smallest n-gram at start position went past the end of the document.
                    // Done with this source hit.
                    break;
                }
            }
            break;
        case CONTAINING_AT_START:
            end++;
            if (end - start > max || end > tokenLength) {
                // N-gram became too long, or we went past end of document.
                // We're done with this source hit.
                break;
            }
            atValidNGram = true;
            break;
        case CONTAINING_AT_END:
            // On to next start position.
            start++;
            if (end - start < min || start + leftAdjust > srcStart) {
                // No more start positions for N-gram; done with this source hit.
                break;
            }
            atValidNGram = true;
            break;
        case WITHIN:
            end++;
            if (end - start > max || end + rightAdjust > srcEnd) {
                // N-gram became too long, or we went past end of source hit.
                // On to next start position.
                start++;
                if (start + min + rightAdjust > srcEnd) {
                    // No more start positions for N-gram; done with this source hit.
                    break;
                }
                end = start + min;
            }
            atValidNGram = true;
            break;

        case MATCHES:
            // Matches only produces one hit per source hit,
            // so we're always done with the source hit here.
            break;

        case STARTS_AT:
        case ENDS_AT:
            throw new UnsupportedOperationException("STARTS_AT and ENDS_AT not implemented for SpansExpandToNGram");
        }
        if (atValidNGram)
            return start;

        return goToNextClauseSpan();
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
        srcStart = srcEnd = start = end = -1;
        goToNextClauseSpan();
        if (start != NO_MORE_POSITIONS) {
            atFirstInCurrentDoc = true;
            return true;
        }
        return false;
    }

    /**
     * Go to the next position in the source clause and reset the expansion process.
     *
     * Note that we may discover we can't do the minimum expansion (because the hit
     * is at the start of the document, for example), so we may have to advance the
     * clause again, and may actually run out of hits while doing so.
     *
     * @return the start position if we're at a valid hit and have reset the
     *         expansion, NO_MORE_POSITIONS if we're done
     */
    private int goToNextClauseSpan() throws IOException {
        srcStart = in.nextStartPosition();
        if (srcStart == NO_MORE_POSITIONS) {
            start = end = srcEnd = NO_MORE_POSITIONS;
            return NO_MORE_POSITIONS;
        }
        while (true) {
            // Determine limits and set to initial n-gram
            srcEnd = in.endPosition();
            switch (op) {
            case MATCHES:
                int len = srcEnd - rightAdjust - (srcStart - leftAdjust);
                if (len >= min && len <= max) {
                    // Only one n-gram
                    start = srcStart - leftAdjust;
                    end = srcEnd - rightAdjust;
                    return start;
                }
                // Hit length doesn't match, so no n-grams.
                break;
            case CONTAINING:
                // Do we know this document's length already?
                if (in.docID() != tokenLengthDocId) {
                    // No, determine length now
                    tokenLengthDocId = in.docID();
                    tokenLength = lengthGetter.getFieldLength(tokenLengthDocId) - BlackLabIndexAbstract.IGNORE_EXTRA_CLOSING_TOKEN;
                }

                // First n-gram containing source hit: minimum start position,
                // smallest possible length
                start = srcEnd - rightAdjust - max;
                if (start < 0)
                    start = 0;
                while (start <= srcStart - leftAdjust && start <= srcEnd - rightAdjust - min) {
                    end = Math.max(srcEnd - rightAdjust, start + min); // minimum length still containing source hit
                    if (end <= start + max && end <= tokenLength) {
                        // Valid n-gram containing hit.
                        return start;
                    }
                    start++; // couldn't get valid match from this start position; try next
                }
                // Cannot make valid n-gram (too long or would go past end of document), so no n-grams.
                break;
            case CONTAINING_AT_START:
                // Do we know this document's length already?
                if (in.docID() != tokenLengthDocId) {
                    // No, determine length now
                    tokenLengthDocId = in.docID();
                    tokenLength = lengthGetter.getFieldLength(tokenLengthDocId) - BlackLabIndexAbstract.IGNORE_EXTRA_CLOSING_TOKEN;
                }

                // First n-gram containing source hit: minimum start position,
                // smallest possible length
                start = srcStart - leftAdjust;
                end = Math.max(srcEnd - rightAdjust, start + min); // minimum length still containing source hit
                if (end <= start + max && end <= tokenLength) {
                    // Valid n-gram containing hit.
                    return start;
                }
                // First n-gram would go past end of document, so no n-grams.
                break;
            case CONTAINING_AT_END:
                // First n-gram containing source hit: minimum start position, maximum length.
                start = srcEnd - rightAdjust - max;
                if (start < 0)
                    start = 0;
                end = srcEnd - rightAdjust;
                if (start <= srcStart - leftAdjust) {
                    // Valid n-gram containing hit.
                    return start;
                }
                // Hit too large to be contained by specified n-grams.
                break;
            case WITHIN:
                // First n-gram within source hit: starts at source start, minimum length
                start = srcStart - leftAdjust;
                end = start + min;
                if (end <= srcEnd - rightAdjust) {
                    // n-gram is contained within hit, so valid.
                    return start;
                }
                // Minimum length n-gram doesn't fit in hit, so no n-grams.
                break;
            case STARTS_AT:
            case ENDS_AT:
                throw new UnsupportedOperationException("STARTS_AT and ENDS_AT not implemented for SpansExpandToNGram");
            }

            // No, try the next hit, if there is one
            if (nextStartPosition() == NO_MORE_POSITIONS) {
                start = end = srcStart = srcEnd = NO_MORE_POSITIONS;
                return NO_MORE_POSITIONS; // No hits left, we're done
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
    public String toString() {
        String adj = (leftAdjust != 0 || rightAdjust != 0) ? ", " + leftAdjust + ", " + rightAdjust : "";
        return "SpansFilterNGrams(" + in + ", " + op + ", " + min + ", " + BLSpanQuery.inf(max) + adj + ")";
    }

    @Override
    public void passHitQueryContextToClauses(HitQueryContext context) {
        in.setHitQueryContext(context);
    }

    @Override
    public int width() {
        return in.width();
    }

    @Override
    public void collect(SpanCollector collector) throws IOException {
        in.collect(collector);
    }
}
