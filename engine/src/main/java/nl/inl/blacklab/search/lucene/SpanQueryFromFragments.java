package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.queries.spans.SpanCollector;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.Weight;

import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.index.BLInputDocument;
import nl.inl.blacklab.indexers.config.Span;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * Converts documents and fragments results into spans.
 *
 * If the whole document matches, that's the only span that will be produced.
 * Adjacent fragments will be combined into a single span.
 */
public class SpanQueryFromFragments extends BLSpanQuery {

    /** Field that contains the index document type (document/fragment/indexmetadata) */
    private static final String DOC_TYPE_FIELD_NAME = BLInputDocument.DOC_TYPE_FIELD_NAME;

    /** Value that indicates a regular (full) document */
    private static final String DOC_TYPE_FULL_DOCUMENT = BLInputDocument.DocType.DOCUMENT.getValue();

    /** Have we nexted the fragment query but not used the result yet? */
    private boolean fragmentQueryNexted = false;

    /** A query yielding full documents and/or fragments */
    private final Query fragmentQuery;

    /** A query yielding all full documents in the index */
    private final Query fullDocsQuery;

    /** Field that contains the full document's pid */
    private final String pidField;

    /** Field that gives us the document length in tokens */
    private final String tokenLengthField;

    public SpanQueryFromFragments(QueryInfo queryInfo, Query fragmentQuery, String pidField) {
        super(queryInfo);
        this.fragmentQuery = fragmentQuery;
        this.pidField = pidField;
        this.tokenLengthField = queryInfo.field().tokenLengthField();

        Term docTypeTerm = new Term(DOC_TYPE_FIELD_NAME, DOC_TYPE_FULL_DOCUMENT);
        fullDocsQuery = new TermQuery(docTypeTerm);
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        Query frRewr = fragmentQuery.rewrite(reader);
        Query fdRewr = fullDocsQuery.rewrite(reader);
        if (frRewr != fragmentQuery || fdRewr != fullDocsQuery) {
            return new SpanQueryFromFragments(queryInfo, frRewr, pidField);
        }
        return this;
    }

    @Override
    public BLSpanWeight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        Weight fragmentWeight = fragmentQuery.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 0);
        Weight fullDocsWeight = fullDocsQuery.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 0);
        return new BLSpanWeight(this, searcher, null, 0) {
            @Override
            public boolean isCacheable(LeafReaderContext ctx) {
                return fragmentWeight.isCacheable(ctx) && fullDocsWeight.isCacheable(ctx);
            }

            @Override
            public void extractTermStates(Map<Term, TermStates> contexts) {
                // No terms
            }

            @Override
            public BLSpans getSpans(LeafReaderContext ctx, Postings requiredPostings) throws IOException {
                Scorer fragmentScorer = fragmentWeight.scorer(ctx);
                Scorer fullDocsScorer = fullDocsWeight.scorer(ctx);
                return new FragmentsToSpans(fragmentScorer, fullDocsScorer, searcher);
            }
        };
    }

    @Override
    public long reverseMatchingCost(IndexReader reader) {
        return 0;
    }

    @Override
    public int forwardMatchingCost() {
        return Integer.MAX_VALUE;
    }

    @Override
    public String getRealField() {
        return queryInfo.field().mainAnnotation().mainSensitivity().luceneField();
    }

    @Override
    public void visit(QueryVisitor visitor) {
        if (visitor.acceptField(getRealField())) {
            fragmentQuery.visit(visitor.getSubVisitor(BooleanClause.Occur.MUST, this));
        }
    }

    @Override
    public String toString(String field) {
        return "FragmentsToSpanQuery(" + fragmentQuery.toString(field) + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        SpanQueryFromFragments that = (SpanQueryFromFragments) o;
        return Objects.equals(fragmentQuery, that.fragmentQuery);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(fragmentQuery);
    }

    /** Get the spans matching the full document and fragment matches */
    private class FragmentsToSpans extends BLSpans {

        /** Where we retrieve stored fields from */
        private final StoredFields storedFields;

        /** Iterator over the matched index documents (full documents and/or fragments) */
        private final DocIdSetIterator fragmentIterator;

        // Iterate over all the docs in the segment that are full documents (not fragments)
        // (needed to find the full document for a fragment if the full document wasn't matched already)
        private final DocIdSetIterator fullDocsIterator;

        /** Current matching document id (last returned from nextDoc) */
        private int currentDocId;

        /** Current full document pid (matching currentDocId). */
        private String currentDocPid;

        /** Spans we're producing from this document. */
        private final List<Span> spansInCurrentDoc = new ArrayList<>();

        /** Spans we're producing from this document. */
        private Iterator<Span> spansIt;

        /** Span we're currently positioned at */
        private Span currentSpan;

        // Fragment defined by the current result from the fragmentIterator (may not be processed yet).
        String fragDocPid;
        int fragDocId;
        int fragStart;
        int fragEnd;
        boolean fragIsFullDoc;

        public FragmentsToSpans(Scorer fragmentScorer, Scorer fullDocsScorer, IndexSearcher searcher) {
            super(SpanGuarantees.SORTED_UNIQUE);
            try {
                this.storedFields = searcher.storedFields();
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
            if (fragmentIterator.docID() == NO_MORE_DOCS) {
                currentDocId = NO_MORE_DOCS;
                return NO_MORE_DOCS;
            }
            // Find the next full document we're returning spans from, and determine all the spans
            if (!fragmentQueryNexted) {
                fragmentQueryNexted = true;
                if (fragmentIterator.nextDoc() == DocIdSetIterator.NO_MORE_DOCS) {
                    currentDocId = NO_MORE_DOCS;
                    return NO_MORE_DOCS;
                }
                // Determine the fragment (or full doc, i.e. fragment from 0 to end) fragmentIterator is currently at.
                determineFragment();
            }

            return prepareCurrentDoc();
        }

        @Override
        public int advance(int target) throws IOException {
            fragmentQueryNexted = true;
            if (fragmentIterator.advance(target) == DocIdSetIterator.NO_MORE_DOCS) {
                currentDocId = NO_MORE_DOCS;
                return NO_MORE_DOCS;
            }
            determineFragment();
            return prepareCurrentDoc();
        }

        @Override
        public int nextStartPosition() throws IOException {
            if (spansIt == null)
                return -1;
            if (!spansIt.hasNext()) {
                return NO_MORE_POSITIONS;
            }
            currentSpan = spansIt.next();
            return currentSpan.start();
        }

        @Override
        public int startPosition() {
            if (currentSpan == null)
                return -1;
            return currentSpan.start();
        }

        @Override
        public int endPosition() {
            if (currentSpan == null)
                return -1;
            return currentSpan.end();
        }

        private void determineFragment() throws IOException {
            // OPT: use docvalues?
            Document document = storedFields.document(fragmentIterator.docID(),
                    Set.of(DOC_TYPE_FIELD_NAME, pidField, tokenLengthField, BLInputDocument.FRAG_FIELD_DOC,
                            BLInputDocument.FRAG_FIELD_START, BLInputDocument.FRAG_FIELD_END));
            if (document.get(DOC_TYPE_FIELD_NAME).equals(DOC_TYPE_FULL_DOCUMENT)) {
                // This is a full document; remember its pid and yield the document
                fragIsFullDoc = true;
                fragDocPid = document.get(pidField);
                fragDocId = fragmentIterator.docID();
                fragStart = 0;
                fragEnd = Integer.parseInt(document.get(tokenLengthField));
            } else {
                // This is a fragment; check if it refers to the current document pid or to a new one.
                fragIsFullDoc = false;
                fragDocPid = document.get(BLInputDocument.FRAG_FIELD_DOC);
                assert fragDocPid != null : "Fragment document missing " + BLInputDocument.FRAG_FIELD_DOC + " field";
                fragStart = Integer.parseInt(document.get(BLInputDocument.FRAG_FIELD_START));
                fragEnd = Integer.parseInt(document.get(BLInputDocument.FRAG_FIELD_END));
                if (!fragDocPid.equals(currentDocPid)) {
                    // Fragment in a new document.
                    // Find the full document for this fragment (by advancing our parallel iterator over all full docs)
                    // (this should work because both iterators are in docId order)
                    while (true) {
                        int fullDocId = fullDocsIterator.nextDoc();
                        if (fullDocId == DocIdSetIterator.NO_MORE_DOCS) {
                            throw new IllegalStateException(
                                    "Fragment found but cannot find full document, fragment pid: " + fragDocPid);
                        }
                        // Check if this is the full document for this fragment (by comparing pids)
                        Document fullDoc = storedFields.document(fullDocId, Set.of(pidField));
                        if (fullDoc.get(pidField).equals(fragDocPid)) {
                            // We found the full document for this fragment; yield it
                            fragDocId = fullDocId;
                            break;
                        }
                    }
                }
            }
        }

        private int prepareCurrentDoc() throws IOException {
            // We're now at the first fragment in the next document.
            // Collect this and all subsequent fragments in this doc as the spans we'll produce.
            currentDocId = fragDocId;
            currentDocPid = fragDocPid;
            spansInCurrentDoc.clear();
            spansInCurrentDoc.add(Span.between(fragStart, fragEnd));
            boolean collectFragments = !fragIsFullDoc; // if whole doc matches, don't also produce fragments
            while (true) {
                if (fragmentIterator.nextDoc() == DocIdSetIterator.NO_MORE_DOCS) {
                    // No more fragments left
                    break;
                }
                determineFragment();
                if (!fragDocPid.equals(currentDocPid)) {
                    // This fragment is in a new document; we'll return it next time
                    break;
                }
                if (collectFragments) {
                    addOrMergeSpan(spansInCurrentDoc, fragStart, fragEnd);
                }
            }
            spansIt = spansInCurrentDoc.iterator();
            return currentDocId;
        }

        /** Add a span to the list, combining with the previous span if adjacent. */
        private void addOrMergeSpan(List<Span> spansInCurrentDoc, int fragStart, int fragEnd) {
            if (!spansInCurrentDoc.isEmpty()) {
                Span lastSpan = spansInCurrentDoc.get(spansInCurrentDoc.size() - 1);
                if (lastSpan.end() == fragStart) {
                    // Adjacent fragment; combine with previous span
                    spansInCurrentDoc.set(spansInCurrentDoc.size() - 1, Span.between(lastSpan.start(), fragEnd));
                    return;
                }
            }
            spansInCurrentDoc.add(Span.between(fragStart, fragEnd));
        }

        @Override
        public long cost() {
            return fragmentIterator.cost();
        }

        @Override
        protected void passHitQueryContextToClauses(HitQueryContext context) {
            // No span clauses to pass context to
        }

        @Override
        public boolean hasMatchInfo() {
            return false;
        }

        @Override
        public void getMatchInfo(MatchInfo[] matchInfo) {
            // No match info to provide
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
            // No spans to collect
        }

        @Override
        public float positionsCost() {
            return 0;
        }
    }
}
