package nl.inl.blacklab.search;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.Weight;

import nl.inl.blacklab.index.BLInputDocument;

/**
 * Converts matching fragments to the documents they occur in.
 *
 * If the whole document also matches, or there are multiple fragments in a document,
 * the document will only be returned once.
 */
public class FragmentsToDocsQuery extends Query {

    /** A query yielding full documents and/or fragments */
    private Query fragmentQuery;

    /** A query yielding all full documents in the index */
    private Query fullDocsQuery;

    /** Field that contains the index document type (document/fragment/indexmetadata) */
    private static final String DOC_TYPE_FIELD_NAME = BLInputDocument.DOC_TYPE_FIELD_NAME;

    /** Value that indicates a regular (full) document */
    private static final String DOC_TYPE_FULL_DOCUMENT = BLInputDocument.DocType.DOCUMENT.getValue();

    /** Field that contains the full document's pid */
    private String pidField;

    public FragmentsToDocsQuery(Query fragmentQuery, String pidField) {
        this.fragmentQuery = fragmentQuery;
        this.pidField = pidField;

        Term docTypeTerm = new Term(DOC_TYPE_FIELD_NAME, DOC_TYPE_FULL_DOCUMENT);
        fullDocsQuery = new TermQuery(docTypeTerm);
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        Weight fragmentWeight = fragmentQuery.createWeight(searcher, scoreMode, boost);
        Weight fullDocsWeight = fullDocsQuery.createWeight(searcher, scoreMode, boost);
        return new Weight(this) {

            @Override
            public Explanation explain(LeafReaderContext context, int doc) {
                return null;
            }

            @Override
            public Scorer scorer(final LeafReaderContext ctx) throws IOException {
                Scorer fragmentScorer = fragmentWeight.scorer(ctx);
                Scorer fullDocsScorer = fullDocsWeight.scorer(ctx);
                return new Scorer(this) {
                    @Override
                    public int docID() {
                        return fragmentScorer.docID();
                    }

                    @Override
                    public float score() throws IOException {
                        return fragmentScorer.score();
                    }

                    @Override
                    public DocIdSetIterator iterator() {
                        return new FragmentsToDocsIterator(fragmentScorer, fullDocsScorer, searcher);
                    }

                    @Override
                    public float getMaxScore(int upTo) throws IOException {
                        return fragmentScorer.getMaxScore(upTo);
                    }
                };
            }

            @Override
            public boolean isCacheable(LeafReaderContext ctx) {
                return fragmentWeight.isCacheable(ctx);
            }

        };
    }

    @Override
    public void visit(QueryVisitor visitor) {
        visitor.visitLeaf(this);
    }

    @Override
    public String toString(String field) {
        return "FragmentsToDocsQuery(" + fragmentQuery.toString(field) + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        FragmentsToDocsQuery that = (FragmentsToDocsQuery) o;
        return Objects.equals(fragmentQuery, that.fragmentQuery);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(fragmentQuery);
    }

    /** Iterate over all the full documents that match the document+fragments found by the query */
    private class FragmentsToDocsIterator extends DocIdSetIterator {

        private final IndexSearcher searcher;
        DocIdSetIterator fragmentIterator;

        // Iterate over all the docs in the segment that are full documents (not fragments)
        DocIdSetIterator fullDocsIterator;

        /** Current matching document id (last returned from nextDoc) */
        int currentDocId;

        /** Last full document pid we saw. If we see a fragment that refers to this pid, we can skip it. */
        String lastDocYieldedPid;

        public FragmentsToDocsIterator(Scorer fragmentScorer, Scorer fullDocsScorer, IndexSearcher searcher) {
            this.searcher = searcher;
            fragmentIterator = fragmentScorer.iterator();
            fullDocsIterator = fullDocsScorer.iterator();
            currentDocId = -1;
            lastDocYieldedPid = null;
        }

        @Override
        public int docID() {
            return currentDocId;
        }

        @Override
        public int nextDoc() throws IOException {
            while (true) {
                int docId = fragmentIterator.nextDoc();
                if (docId == DocIdSetIterator.NO_MORE_DOCS) {
                    currentDocId = NO_MORE_DOCS;
                    return NO_MORE_DOCS;
                }

                // Find the doc type and pid
                Document document = searcher.getIndexReader().storedFields().document(docId,
                        Set.of(DOC_TYPE_FIELD_NAME, pidField, BLInputDocument.FRAG_FIELD_DOC));
                if (document.get(DOC_TYPE_FIELD_NAME).equals(DOC_TYPE_FULL_DOCUMENT)) {
                    // This is a full document; remember its pid and yield the document
                    lastDocYieldedPid = document.get(pidField);
                    currentDocId = docId;
                    break;
                } else {
                    // This is a fragment; check if it refers to the last full document pid
                    String fragmentPid = document.get(BLInputDocument.FRAG_FIELD_DOC);
                    if (fragmentPid != null && fragmentPid.equals(lastDocYieldedPid)) {
                        // This fragment refers to the last full document we saw, skip it
                        // (we've already yielded that document, don't yield it again)
                        continue;
                    }
                    // Find the full document for this fragment (by advancing our parallel iterator over all full docs)
                    // (this should work because both iterators are in docId order)
                    while (true) {
                        int fullDocId = fullDocsIterator.nextDoc();
                        if (fullDocId == DocIdSetIterator.NO_MORE_DOCS) {
                            throw new IllegalStateException("Fragment found but cannot find full document, fragment pid: " + fragmentPid);
                        }
                        // Check if this is the full document for this fragment (by comparing pids)
                        Document fullDoc = searcher.getIndexReader().storedFields().document(fullDocId, Set.of(pidField));
                        if (fullDoc.get(pidField).equals(fragmentPid)) {
                            // We found the full document for this fragment; yield it
                            lastDocYieldedPid = fullDoc.get(pidField);
                            currentDocId = fullDocId;
                            break;
                        }
                    }
                }
            }
            return currentDocId;
        }

        @Override
        public int advance(int target) throws IOException {
            // TODO: same as nextDoc(), but advance to the target doc id
            return fragmentIterator.advance(target);
        }

        @Override
        public long cost() {
            return fragmentIterator.cost();
        }
    }
}
