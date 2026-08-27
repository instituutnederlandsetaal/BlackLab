package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.Weight;

import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.index.BLInputDocument;

/**
 * Converts matching fragments to the documents they occur in.
 * <p>
 * If the whole document also matches, or there are multiple fragments in a document,
 * the document will only be returned once.
 */
public class QueryFullDocsFromFragments extends Query {

    /** Field that contains the index document type (document/fragment/indexmetadata) */
    private static final String DOC_TYPE_FIELD_NAME = BLInputDocument.DOC_TYPE_FIELD_NAME;

    /** Value that indicates a regular (full) document */
    private static final String DOC_TYPE_FULL_DOCUMENT = BLInputDocument.DocType.DOCUMENT.getValue();

    /** A query yielding full documents and/or fragments */
    private final Query fragmentQuery;

    /** A query yielding all full documents in the index */
    private final Query fullDocsQuery;

    /** Field that contains the full document's pid */
    private final String pidField;

    public QueryFullDocsFromFragments(Query fragmentQuery, String pidField) {
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
                        return new FragmentsToDocsIterator(ctx, fragmentScorer, fullDocsScorer);
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
        fragmentQuery.visit(visitor.getSubVisitor(BooleanClause.Occur.MUST, this));
    }

    @Override
    public String toString(String field) {
        return "FragmentsToDocsQuery(" + fragmentQuery.toString(field) + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        QueryFullDocsFromFragments that = (QueryFullDocsFromFragments) o;
        return Objects.equals(fragmentQuery, that.fragmentQuery);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(fragmentQuery);
    }

    /** Iterate over all the full documents that match the document+fragments found by the query */
    private class FragmentsToDocsIterator extends DocIdSetIterator {

        /** Where we retrieve stored fields from */
        private final StoredFields storedFields;

        /** Iterator over the matched index documents (full documents and/or fragments) */
        DocIdSetIterator fragmentIterator;

        // Iterate over all the docs in the segment that are full documents (not fragments)
        // (needed to find the full document for a fragment if the full document wasn't matched already)
        DocIdSetIterator fullDocsIterator;

        /** Current matching document id (last returned from nextDoc) */
        int currentDocId;

        /** Last full document pid we saw. If we see a fragment that refers to this pid, we can skip it. */
        String currentDocPid;

        public FragmentsToDocsIterator(LeafReaderContext ctx, Scorer fragmentScorer, Scorer fullDocsScorer) {
            try {
                this.storedFields = ctx.reader().storedFields();
                // OPT: use docvalues?
                //SortedSetDocValues dvType = ctx.reader().getSortedSetDocValues(DOC_TYPE_FIELD_NAME);
                //SortedSetDocValues dvPid = ctx.reader().getSortedSetDocValues(pidField);
                //BLInputDocument.FRAG_FIELD_DOC
            } catch (IOException e) {
                throw new InvalidIndex(e);
            }
            fragmentIterator = fragmentScorer.iterator();
            fullDocsIterator = fullDocsScorer.iterator();
            currentDocId = -1;
            currentDocPid = null;
        }

        @Override
        public int docID() {
            return currentDocId;
        }

        @Override
        public int nextDoc() throws IOException {
            // Find the next full document to return (we have to skip potential duplicates because of fragments)
            fragmentIterator.nextDoc();
            synchronizeDoc();
            return currentDocId;
        }

        @Override
        public int advance(int target) throws IOException {
            fragmentIterator.advance(target);
            synchronizeDoc();
            return currentDocId;
        }

        /**
         * From the current index document, find the next full document to return
         * (we have to skip potential duplicates because of fragments).
         */
        private void synchronizeDoc() throws IOException {
            // Find the next full document to return (we have to skip potential duplicates because of fragments)
            while (true) {
                if (fragmentIterator.docID() == DocIdSetIterator.NO_MORE_DOCS) {
                    currentDocId = NO_MORE_DOCS;
                    return;
                }

                // Find the doc type and pid
                Document document = storedFields.document(fragmentIterator.docID(),
                        Set.of(DOC_TYPE_FIELD_NAME, pidField, BLInputDocument.FRAG_FIELD_DOC));
                if (document.get(DOC_TYPE_FIELD_NAME).equals(DOC_TYPE_FULL_DOCUMENT)) {
                    // This is a full document; remember its pid and yield the document
                    currentDocPid = document.get(pidField);
                    currentDocId = fragmentIterator.docID();
                    break;
                } else {
                    // This is a fragment; check if it refers to the last full document pid
                    // (if it does, we can skip it)
                    String fragmentPid = document.get(BLInputDocument.FRAG_FIELD_DOC);
                    if (fragmentPid == null || !fragmentPid.equals(currentDocPid)) {
                        // Different document pid.
                        // Find the full document for this fragment (by advancing our full document iterator)
                        // (this should work because both iterators are in docId order)
                        while (true) {
                            int fullDocId = fullDocsIterator.nextDoc();
                            if (fullDocId == DocIdSetIterator.NO_MORE_DOCS) {
                                throw new IllegalStateException(
                                        "Fragment found but cannot find full document, fragment pid: " + fragmentPid);
                            }
                            // Check if this is the full document for this fragment (by comparing pids)
                            Document fullDoc = storedFields.document(fullDocId, Set.of(pidField));
                            if (fullDoc.get(pidField).equals(fragmentPid)) {
                                // We found the full document for this fragment; yield it
                                currentDocPid = fullDoc.get(pidField);
                                currentDocId = fullDocId;
                                break;
                            }
                        }
                    }

                    // Go to the next matched index document (full document or fragment)
                    fragmentIterator.nextDoc();
                }
            }
        }

        @Override
        public long cost() {
            return fragmentIterator.cost();
        }
    }
}
