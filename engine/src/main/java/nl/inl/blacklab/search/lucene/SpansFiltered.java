package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.List;

import org.apache.lucene.search.ConjunctionDISI;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.search.spans.FilterSpans;

/**
 * Apply a document Filter to a Spans.
 *
 * This allows us to only consider certain documents (say, only documents in a
 * certain domain) when executing our query.
 */
class SpansFiltered extends BLFilterSpans<BLSpans> {

    /** Keep hits from these documents. */
    private final DocIdSetIterator acceptedDocs;

    public SpansFiltered(BLSpans spans, Scorer filterDocs) throws IOException {
        this(spans, filterDocs == null ? null : filterDocs.iterator());
    }

    public SpansFiltered(BLSpans spans, DocIdSetIterator acceptedDocs) throws IOException {
        super(spans);
        assert in != null && acceptedDocs != null; // don't create this class if there are no hits!
        this.acceptedDocs = acceptedDocs;
    }

    public void assertDocIdsAllMatch() {
        if (in.docID() == NO_MORE_DOCS)
            return;
        assert acceptedDocs.docID() == in.docID();
    }

    @Override
    public int nextDoc() throws IOException {
        assert docID() != NO_MORE_DOCS;
        atFirstInCurrentDoc = false;
        startPos = -1;
        // advance the doc filter first, because it means we can skip hits from our hits clause (in),
        // which would likely slower to iterate over
        acceptedDocs.nextDoc();
        // now re-synchronize the filter and the hits clause
        return nextMatchingDoc();
    }

    private int nextMatchingDoc() throws IOException {
        // Keep going until we find a doc or we run out
        while (true) {
            // Make sure in and acceptedDocs are in the same doc
            int inDocId = in.docID();
            while (inDocId != NO_MORE_DOCS && acceptedDocs.docID() != NO_MORE_DOCS && inDocId != acceptedDocs.docID()) {
                // Do we need to advance in to catch up with acceptedDocs?
                if (inDocId < acceptedDocs.docID())
                    inDocId = in.advance(acceptedDocs.docID());
                // Do we need to advance acceptedDocs to catch up with in?
                if (acceptedDocs.docID() < inDocId)
                    acceptedDocs.advance(inDocId);
            }
            //assert inDocId == approximation.docID();
            assert acceptedDocs.docID() >= 0;
            assert inDocId >= 0;
            if (acceptedDocs.docID() == NO_MORE_DOCS || inDocId == NO_MORE_DOCS) {
                // Done
                return NO_MORE_DOCS;
            } else if (twoPhaseCurrentDocMatches()) {
                // Found a match; return it
                return inDocId;
            }
        }
    }

    @Override
    public int advance(int target) throws IOException {
        assert target >= 0 && target > in.docID();
        atFirstInCurrentDoc = false;
        startPos = -1;
        // advance the doc filter first, because it means we can skip hits from our hits clause (in),
        // which would likely slower to iterate over
        acceptedDocs.advance(target);
        // now re-synchronize the filter and the hits clause
        return nextMatchingDoc();
    }

    @Override
    public int docID() {
        if (in.docID() == NO_MORE_DOCS || acceptedDocs.docID() == NO_MORE_DOCS)
            return NO_MORE_DOCS;
        assertDocIdsAllMatch();
        return in.docID();
    }

    @Override
    protected FilterSpans.AcceptStatus accept(BLSpans candidate) throws IOException {
        assert candidate.startPosition() >= 0;

        // Accept all hits in the matching document
        // (two-phase approximation already checked our acceptedDocs)
        return FilterSpans.AcceptStatus.YES;
    }

    @Override
    public String toString() {
        return "DOCFILTER(" + in + ", " + acceptedDocs + ")";
    }

    @Override
    protected boolean twoPhaseCurrentDocMatches() throws IOException {
        assert in.startPosition() == -1;
        assert in.endPosition() == -1;
        return in.docID() == acceptedDocs.docID() && super.twoPhaseCurrentDocMatches();
    }

    @Override
    public TwoPhaseIterator asTwoPhaseIterator() {
        float matchCost;
        DocIdSetIterator approximation;
        TwoPhaseIterator inner = in.asTwoPhaseIterator();
        if (acceptedDocs == null || acceptedDocs.docID() == NO_MORE_DOCS) {
            approximation = DocIdSetIterator.empty();
            matchCost = 0;
            return new TwoPhaseIterator(approximation) {
                @Override
                public boolean matches() throws IOException {
                    return false;
                }

                @Override
                public float matchCost() {
                    return matchCost;
                }

                @Override
                public String toString() {
                    return "BLFilterSpans@asTwoPhaseIterator(approx=" + approximation + ")";
                }
            };
        } else {
            if (inner == null) {
                approximation = ConjunctionDISI.intersectIterators(List.of(acceptedDocs, in));
                matchCost = in.positionsCost(); // underestimate
                return new TwoPhaseIterator(approximation) {
                    @Override
                    public boolean matches() throws IOException {
                        return twoPhaseCurrentDocMatches();
                    }

                    @Override
                    public float matchCost() {
                        return matchCost;
                    }

                    @Override
                    public String toString() {
                        return "BLFilterSpans@asTwoPhaseIterator(approx=" + approximation + ")";
                    }
                };
            } else {
                DocIdSetIterator innerApprox = inner.approximation();
                approximation = ConjunctionDISI.intersectIterators(List.of(acceptedDocs, innerApprox));
                matchCost = inner.matchCost(); // overestimate
                return new TwoPhaseIterator(approximation) {
                    @Override
                    public boolean matches() throws IOException {
                        return inner.matches() && twoPhaseCurrentDocMatches();
                    }

                    @Override
                    public float matchCost() {
                        return matchCost;
                    }

                    @Override
                    public String toString() {
                        return "BLFilterSpans@asTwoPhaseIterator(approx=" + approximation + ")";
                    }
                };
            }
        }
    }
}
