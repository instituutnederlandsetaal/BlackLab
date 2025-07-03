package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.MultiBits;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.queries.spans.SpanCollector;
import org.apache.lucene.util.Bits;

import nl.inl.blacklab.search.BlackLabIndexAbstract;

/**
 * Returns all tokens that do not occur in the matches of the specified query.
 *
 * Each token is returned as a single hit.
 */
class SpansNot extends BLSpans {
    /** The spans to invert */
    private final BLSpans clause;

    private int clauseDoc = -1;

    /** Current token position in clause (if valid), or -1 for none yet, or NO_MORE_POSITIONS for no more. */
    private int clauseStart = -1;

    /** Current document, or -1 for none yet, or NO_MORE_DOCS for no more. */
    private int currentDoc = -1;

    /** Current document length (if currentDoc is valid) */
    private long currentDocLength = -1;

    /** Current hit start position, or -1 if we're not at a hit yet, or NO_MORE_POSITIONS if no more hits. */
    private int currentStart = -1;

    /** Current hit end position, or -1 if we're not at a hit, or NO_MORE_POSITIONS if no more hits. */
    private int currentEnd = -1;

    /** Used to get the field length in tokens for a document */
    final DocFieldLengthGetter lengthGetter;

    /** Highest document id plus one */
    private int maxDoc;

    /** Documents that haven't been deleted */
    private final Bits liveDocs;

    private boolean atFirstInCurrentDoc = false;

    /**
     * For testing, we don't have an IndexReader available, so we use test values.
     *
     * The test values are: there are 3 documents (0, 1 and 2) and each is 5 tokens
     * long.
     *
     * @param maxDoc number of docs in the (mock) test set
     */
    void setTest(int maxDoc) {
        // For testing, we don't have an IndexReader available, so we use test values
        this.maxDoc = maxDoc;
        lengthGetter.setTest(true);
    }

    /**
     * Constructs a SpansNot.
     *
     * Clause may not be null; use SpansNGrams(1,1) instead.
     *
     * @param reader the index reader, for getting field lengths
     * @param fieldName the field name, for getting field lengths
     * @param clause the clause to invert (must be startpoint-sorted)
     */
    public SpansNot(LeafReader reader, String fieldName, BLSpans clause) {
        super(SpanQueryNot.createGuarantees());
        if (clause == null)
            throw new IllegalArgumentException("clause == null; use SpansNGrams(1,1) instead");
        this.clause = BLSpans.ensureSorted(clause);
        maxDoc = reader == null ? -1 : reader.maxDoc();
        liveDocs = reader == null ? null : MultiBits.getLiveDocs(reader);
        this.lengthGetter = new DocFieldLengthGetter(reader, fieldName);
    }

    /**
     * @return the Lucene document id of the current hit
     */
    @Override
    public int docID() {
        return currentDoc;
    }

    /**
     * @return end position of current hit
     */
    @Override
    public int endPosition() {
        if (atFirstInCurrentDoc)
            return -1; // .nextStartPosition() not called yet by client
        return currentEnd;
    }

    @Override
    public int nextDoc() throws IOException {
        assert docID() != NO_MORE_DOCS;
        atFirstInCurrentDoc = false;
        do {
            if (currentDoc >= maxDoc) {
                currentDoc = NO_MORE_DOCS;
                currentStart = currentEnd = clauseStart = NO_MORE_POSITIONS;
                return NO_MORE_DOCS;
            }
            // Go to next non-deleted doc
            boolean currentDocIsDeletedDoc;
            do {
                currentDoc++;
                currentDocIsDeletedDoc = liveDocs != null && !liveDocs.get(currentDoc);
            } while (currentDoc < maxDoc && currentDocIsDeletedDoc);
            if (currentDoc > maxDoc)
                throw new RuntimeException("currentDoc > maxDoc!!");
            if (currentDoc == maxDoc) {
                currentDoc = NO_MORE_DOCS;
                currentStart = currentEnd = clauseStart = NO_MORE_POSITIONS;
                return NO_MORE_DOCS; // no more docs; we're done
            }
            // Advance clause to current doc or beyond
            if (clauseDoc < currentDoc)
                clauseDoc = clause.advance(currentDoc);
            clauseStart = clauseDoc == NO_MORE_DOCS ? NO_MORE_POSITIONS : -1;
            // Prepare to produce tokens for this doc
            currentDocLength = (long)lengthGetter.getFieldLength(currentDoc) - BlackLabIndexAbstract.IGNORE_EXTRA_CLOSING_TOKEN;
            currentStart = currentEnd = -1;
        } while (nextStartPosition() == NO_MORE_POSITIONS);
        atFirstInCurrentDoc = true;

        return currentDoc;
    }

    /**
     * Go to next span.
     *
     * @return true if we're at the next span, false if we're done
     */
    @Override
    public int nextStartPosition() throws IOException {
        assert startPosition() != NO_MORE_POSITIONS;
        if (atFirstInCurrentDoc) {
            atFirstInCurrentDoc = false;
            return currentStart;
        }

        if (currentDoc < 0 || currentDoc == NO_MORE_DOCS || currentStart == NO_MORE_POSITIONS) {
            return NO_MORE_POSITIONS;
        }

        // Advance token
        currentStart++;
        currentEnd = currentStart + 1;

        boolean foundValidToken = false;
        while (!foundValidToken) {

            // Which of 3 situations are we in?
            if (currentDoc < 0) {

                // A - We haven't started yet.
                return -1;

            } else if (clauseDoc == currentDoc && clauseStart != NO_MORE_POSITIONS) {

                // B - There is a clause, and it is positioning within currentDoc.
                //     Look at the hit and adjust currentToken if necessary.

                // Current hit beyond currentToken?
                if (clauseStart > currentStart) {

                    // Yes. currentToken is fine to produce.
                    foundValidToken = true;

                } else {
                    // No; advance currentToken past this hit if necessary
                    if (clause.endPosition() > currentStart) {
                        // (note that end is the first word not in the hit)
                        currentStart = clause.endPosition();
                        currentEnd = currentStart + 1;
                    }

                    // Now go to next hit and loop again, until we hit the
                    // then-part above.
                    clauseStart = clause.nextStartPosition();
                }

            } else {

                // C - We have no clause, or the clause was depleted, or it is pointing beyond current doc.
                //     Either produce next token (because it's obviously not a match of the
                //     clause to exclude), or move to next doc if we're done with this doc.
                if (currentStart < currentDocLength) {

                    // Token is fine to produce.
                    foundValidToken = true;

                } else {
                    // We're done in this document
                    currentStart = currentEnd = NO_MORE_POSITIONS;
                    return NO_MORE_POSITIONS;
                }
            }
        }
        return currentStart;
    }

    @Override
    public int advanceStartPosition(int targetPosition) throws IOException {
        assert targetPosition > startPosition();
        if (atFirstInCurrentDoc) {
            atFirstInCurrentDoc = false;
            if (currentStart >= targetPosition)
                return currentStart;
        }
        if (currentDocLength >= 0 && targetPosition >= currentDocLength) {
            currentStart = currentEnd = NO_MORE_POSITIONS;
            return NO_MORE_POSITIONS;
        }
        // Advance us to just before the requested start point, then call nextStartPosition().
        if (clauseDoc == currentDoc && clauseStart < targetPosition)
            clauseStart = clause.advanceStartPosition(targetPosition);
        currentStart = targetPosition - 1;
        currentEnd = targetPosition;
        return nextStartPosition();
    }

    /**
     * Skip to the specified document (or the first document after it containing
     * hits).
     *
     * @param doc the doc number to skip to (or past)
     * @return true if we're still pointing to a valid hit, false if we're done
     */
    @Override
    public int advance(int doc) throws IOException {
        assert doc >= 0 && doc > docID();
        atFirstInCurrentDoc = false;
        if (currentDoc == NO_MORE_DOCS)
            return NO_MORE_DOCS;
        if (doc >= maxDoc) {
            currentDoc = NO_MORE_DOCS;
            currentStart = currentEnd = clauseStart = NO_MORE_POSITIONS;
            return NO_MORE_DOCS;
        }

        if (currentDoc >= doc) {
            // We can't skip to it because we're already there or beyond.
            // But, as per spec, advance always at least advances to the next document.
            return nextDoc();
        }

//		// If it's not already (past) there, skip clause
//		// to doc (or beyond if there's no hits in doc)
//		if (clauseStart != NO_MORE_POSITIONS && (clauseStart == -1 || clauseDoc < doc)) {
//			clauseDoc = clause.advance(doc);
//			clauseStart = clauseDoc == NO_MORE_DOCS ? NO_MORE_POSITIONS : -1;
//		}

        // Advance to first livedoc containing matches at or after requested docID
        currentDoc = doc - 1;
        nextDoc();
        return currentDoc;
    }

    /**
     * @return start of current span
     */
    @Override
    public int startPosition() {
        if (atFirstInCurrentDoc)
            return -1; // .nextStartPosition() not called yet by client
        return currentStart;
    }

    @Override
    public String toString() {
        return "NotSpans(" + clause + ")";
    }

    @Override
    public void passHitQueryContextToClauses(HitQueryContext context) {
        clause.setHitQueryContext(context);
    }

    @Override
    public void getMatchInfo(MatchInfo[] matchInfo) {
        // SpansNot doesn't add any match info but the clause is negated
    }

    @Override
    public boolean hasMatchInfo() {
        return false;
    }

    @Override
    public RelationInfo getRelationInfo() {
        return null;
    }

    @Override
    public int width() {
        return 0;
    }

    @Override
    public void collect(SpanCollector collector) {
        // nothing to collect
    }

    @Override
    public float positionsCost() {
        // we have no clause to derive this from; we should really return a value indicating
        // how many n-grams are in the entire corpus?
        // For now we just return a random value that seems fairly high, but maybe look into this more
        return 10_000;

        // This is wrong, because we don't know that the clause supports positionsCost().
        // (Spans classes that never return null from asTwoPhaseIterator() often don't implement this method)
        //return clause.positionsCost();
    }

    @Override
    public TwoPhaseIterator asTwoPhaseIterator() {
        // An approximation of our clause doesn't help us eliminate documents,
        // because we can only eliminate a document if we know its clause matches all tokens.
        return null;
    }
}
