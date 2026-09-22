package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SortedDocValues;
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
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.join.BitSetProducer;
import org.apache.lucene.search.join.QueryBitSetProducer;
import org.apache.lucene.util.BitSet;
import org.apache.lucene.util.BytesRef;

import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.index.BLInputDocument;
import nl.inl.blacklab.indexers.config.Span;
import nl.inl.blacklab.search.BlackLabIndexAbstract;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * Converts documents and fragments results into spans.
 * <p>
 * If the whole document matches, that's the only span that will be produced.
 * Adjacent fragments will be combined into a single span.
 */
public class SpanQueryFromFragments extends BLSpanQuery {

    /** A query yielding full documents and/or fragments */
    private final Query fragmentQuery;

    /** If not null: hits to look for to determine which fragments and full documents match */
    private final BLSpanQuery hitsInFragments;

    /** Creates a bitset of full documents per segment, so we can find a fragment's parent */
    private final BitSetProducer fullDocsBitSetProducer;

    /** Field that gives us the document length in tokens */
    private final String tokenLengthField;

    /** Whether to prefer full documents or fragments. */
    public enum Behaviour {
        /** If the the full document matches, return only that as a span.
         * (used by default for filters on (fragment) metadata) */
        PREFER_FULL_DOCS,

        /** If there are any fragment matches, return those. Only if there are no fragment matches, return the full doc.
         * (used for e.g. subcorpora containing a word - you may want only fragments containing that word) */
        PREFER_FRAGMENTS,
    }

    /** The behaviour of the span query (whether to prefer full documents or fragments) */
    private final Behaviour behaviour;

    public SpanQueryFromFragments(QueryInfo queryInfo, Query fragmentQuery, BLSpanQuery hitsInFragments, Behaviour behaviour) {
        super(queryInfo);
        this.fragmentQuery = fragmentQuery;
        this.hitsInFragments = hitsInFragments;
        this.tokenLengthField = queryInfo.field().tokenLengthField();
        this.behaviour = behaviour;

        fullDocsBitSetProducer = new QueryBitSetProducer(BLInputDocument.docTypeQuery(BLInputDocument.DocType.DOCUMENT));
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) throws IOException {
        Query frRewr = fragmentQuery.rewrite(reader);
        BLSpanQuery hitsRewr = hitsInFragments == null ? null : hitsInFragments.rewrite(reader);
        if (frRewr != fragmentQuery || hitsRewr != hitsInFragments) {
            return new SpanQueryFromFragments(queryInfo, frRewr, hitsRewr, behaviour);
        }
        return this;
    }

    @Override
    public BLSpanWeight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        Weight fragmentWeight = fragmentQuery.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 0);
        BLSpanWeight hitsWeight = hitsInFragments == null ? null : hitsInFragments.createWeight(searcher, scoreMode, boost);
        return new BLSpanWeight(this, searcher, null, 0) {
            @Override
            public boolean isCacheable(LeafReaderContext ctx) {
                return fragmentWeight.isCacheable(ctx) && (hitsWeight == null || hitsWeight.isCacheable(ctx));
            }

            @Override
            public void extractTermStates(Map<Term, TermStates> contexts) {
                // No terms
            }

            @Override
            public BLSpans getSpans(LeafReaderContext ctx, Postings requiredPostings) throws IOException {
                Scorer fragmentScorer = fragmentWeight.scorer(ctx);
                if (fragmentScorer == null)
                    return null; // no matches in segment
                BLSpans hitsSpans;
                if (hitsWeight != null) {
                    hitsSpans = hitsWeight.getSpans(ctx, requiredPostings);
                    if (hitsSpans == null)
                        return null; // no matches in segment
                } else {
                    hitsSpans = null;
                }
                BitSet fullDocsBitSet = fullDocsBitSetProducer.getBitSet(ctx);
                if (fullDocsBitSet == null)
                    return null; // This segment contains no documents to return spans from.
                HitTester hitTester = hitsSpans == null ? null : new HitTester(hitsSpans);
                return new FragmentsToSpans(fragmentScorer, fullDocsBitSet, ctx, hitTester);
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
        return "SpanQueryFromFragments(" + fragmentQuery.toString(field) + ")";
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

    /** Does fragment/document contain at least one hit? */
    static class HitTester {

        /** Hits to look for */
        private final BLSpans hitsSpans;

        public HitTester(BLSpans hitsSpans) {
            this.hitsSpans = hitsSpans;
        }

        public BLSpans getSpans() {
            return hitsSpans;
        }

        /** Does the current full document contain any hits at all? */
        public boolean anyHitsInDocument(int fullDocId) throws IOException {
            // If we didn't skip past the fullDocId requested, there must be at least one hit in it.
            return goToDocId(fullDocId) == fullDocId;
        }

        /** Does this fragment contain at least one hit? */
        public boolean anyHitsInFragment(int fullDocId, int fragStart, int fragEnd) throws IOException {
            // Ensure hitsSpans is in the same document.
            int docId = goToDocId(fullDocId);
            if (docId != fullDocId)
                return false; // no hits in fullDocId

            // Ensure we're at least at the first hit in the document
            if (hitsSpans.startPosition() < 0)
                hitsSpans.nextStartPosition();
            if (hitsSpans.startPosition() == DocIdSetIterator.NO_MORE_DOCS)
                return false; // no more hits in this document, so fragment doesn't match
            while (true) {
                int hitStart = hitsSpans.startPosition();
                int hitEnd = hitsSpans.endPosition();
                if (hitStart >= fragEnd)
                    return false; // hit is after fragment, so fragment doesn't match
                if (hitStart >= fragStart && hitEnd <= fragEnd)
                    return true; // hit is within fragment, so fragment matches
                // Hit is before fragment, or overlaps fragment boundaries; advance to next hit
                if (hitsSpans.nextStartPosition() == DocIdSetIterator.NO_MORE_DOCS)
                    return false; // no more hits in this document
            }
        }

        /** Go to the specified docId, if there's matches in it.
         *
         * @param fullDocId docId of the full document to go to
         * @return current docId, which may not be the one requested.
         * @throws IOException
         */
        private int goToDocId(int fullDocId) throws IOException {
            // Ensure we're in the right document
            int docId = hitsSpans.docID();
            if (docId < fullDocId) {
                docId = hitsSpans.advance(fullDocId);
            }
            return docId;
        }
    }

    /** Get the spans matching the full document and fragment matches */
    private class FragmentsToSpans extends BLSpans {

        /** Iterator over the matched index documents (full documents and/or fragments) */
        private final DocIdSetIterator fragmentIterator;

        /** DocValues for _frag_annotatedField (field this is a fragment of) */
        private final SortedDocValues dvFragAnnotatedField;

        /** Ord of the annotated field we're searching. Only look at matching fragments in this field. */
        private final long currentAnnotatedFieldOrd;

        /** DocValues for _frag_start (start of fragment) */
        private final NumericDocValues dvFragStart;

        /** DocValues for _frag_end (start of fragment) */
        private final NumericDocValues dvFragEnd;

        /** BitSet indicating which Lucene docs are full documents (not fragments)
         * (needed to find the full document for a fragment if the full document wasn't matched already)
         */
        private final BitSet fullDocsBitSet;

        /** DocValues for token length field */
        private final NumericDocValues dvTokenLength;

        /** One greater than highest doc id */
        private final int maxDoc;

        /** Current matching document id (last returned from nextDoc) */
        private int currentDocId;

        /** Spans we're producing from this document. */
        private final List<Span> spansInCurrentDoc = new ArrayList<>();

        /** Spans we're producing from this document. */
        private Iterator<Span> spansIt;

        /** Span we're currently positioned at */
        private Span currentSpan;

        // The following fields track the fragment (or full document) defined by the current result
        // from the fragmentIterator. It may not be processed yet.

        /** Document id of the full document the current fragment belongs to. */
        int fragFullDocId;

        /** Token position where current fragments starts */
        int fragStart;

        /** Token position where current fragments ends */
        int fragEnd;

        /** Is current fragment in the field we're interested in? */
        boolean fragInCorrectField;

        /** Is current "fragment" actually a full document? */
        boolean fragIsFullDoc;

        /** Should a fragment be considered a match? E.g. does it contain at least one hit? */
        HitTester hitTester;

        public FragmentsToSpans(Scorer fragmentScorer, BitSet fullDocsBitSet, LeafReaderContext ctx,
                HitTester hitTester) {
            super(SpanGuarantees.SORTED_UNIQUE);
            try {
                // Get the DocValues for the fields we need to read from the fragmentIterator results
                LeafReader reader = ctx.reader();
                dvTokenLength = DocValues.getNumeric(reader, tokenLengthField);
                dvFragAnnotatedField = DocValues.getSorted(reader, BLInputDocument.FRAG_FIELD_ANNOTATED_FIELD);
                String annotatedFieldName = queryInfo.field().name();
                currentAnnotatedFieldOrd = dvFragAnnotatedField.lookupTerm(new BytesRef(annotatedFieldName));
                dvFragStart = DocValues.getNumeric(reader, BLInputDocument.FRAG_FIELD_START);
                dvFragEnd = DocValues.getNumeric(reader, BLInputDocument.FRAG_FIELD_END);
                maxDoc = reader.maxDoc();
            } catch (IOException e) {
                throw new InvalidIndex(e);
            }
            fragmentIterator = fragmentScorer.iterator();
            this.fullDocsBitSet = fullDocsBitSet;
            this.hitTester = hitTester;
            currentDocId = -1;
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
            // Found a document with spans to return
            do {
                if (ensureAtMatchingFrag() == NO_MORE_DOCS)
                    return NO_MORE_DOCS;
                collectSpansInDocAndFilter();
            } while (spansInCurrentDoc.isEmpty());
            return docID();
        }

        /** Make sure we are at a full document or matching (i.e. in correct field) fragment.
         * <p>
         * Precondition: fragmentIterator is either not yet nexted or positioned at a full document or fragment.
         * Postcondition: fragmentIterator is positioned at a full document or matching fragment.
         * May be the same, could be different.
         *
         * @return the doc id of the full document we're returning spans from, or NO_MORE_DOCS if there are no more
         */
        private int ensureAtMatchingFrag() throws IOException {
            assert fragmentIteratorNotExhausted();
            while (fragmentIterator.docID() < 0 || !fragInCorrectField) {
                if (fragmentIterator.nextDoc() == DocIdSetIterator.NO_MORE_DOCS) {
                    currentDocId = NO_MORE_DOCS;
                    return NO_MORE_DOCS;
                }
                // Determine the fragment (or full doc, i.e. fragment from 0 to end) fragmentIterator is currently at.
                determineFragment();
            }
            return fragFullDocId;
        }

        @Override
        public int advance(int target) throws IOException {
            if (target >= maxDoc) {
                currentDocId = NO_MORE_DOCS;
                return NO_MORE_DOCS;
            }

            // We need to advance fragmentIterator to the first fragment in a document >= target.
            // Find the previous full document before target, and advance to the next document after that,
            // which is the first fragment in a document >= target (or a full document without fragments).
            int firstFragId = target == 0 ? 0 : fullDocsBitSet.prevSetBit(target - 1) + 1;

            if (fragmentIterator.advance(firstFragId) == DocIdSetIterator.NO_MORE_DOCS) {
                currentDocId = NO_MORE_DOCS;
                return NO_MORE_DOCS;
            }
            assert fragmentIterator.docID() >= firstFragId : "fragmentIterator.advance() returned a doc < firstFrag: " + fragmentIterator.docID() + " < " + firstFragId;
            determineFragment();

            // Find the next full document we're returning spans from, and determine all the spans
            // Found a document with spans to return
            int docId;
            do {
                if (ensureAtMatchingFrag() == NO_MORE_DOCS)
                    return NO_MORE_DOCS;
                docId = collectSpansInDocAndFilter();
                assert docId >= target : "advance() returned a doc < target: " + docId + " < " + target;
            } while (spansInCurrentDoc.isEmpty());
            return docId;
        }

        @Override
        public int nextStartPosition() throws IOException {
            if (spansIt == null)
                return -1;
            if (!spansIt.hasNext()) {
                currentSpan = Span.between(NO_MORE_POSITIONS, NO_MORE_POSITIONS);
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

        /**
         * Determine the fragment (or full document) fragmentIterator is currently at.
         * <p>
         * Precondition: fragmentIterator is positioned at a full document or fragment.
         * Sets fragDocId, fragStart, fragEnd, fragInCorrectField, fragIsFullDoc.
         */
        private void determineFragment() throws IOException {
            assert fragmentIteratorPositioned();
            int docId = fragmentIterator.docID();
            if (fullDocsBitSet.get(docId)) {
                // This is a full document; yield the document
                fragIsFullDoc = true;
                fragInCorrectField = true; // (only applies to fragments)
                fragFullDocId = docId;
                fragStart = 0;
                if (dvTokenLength.docID() != docId)
                    dvTokenLength.advance(docId);
                fragEnd = (int)dvTokenLength.longValue() - BlackLabIndexAbstract.IGNORE_EXTRA_CLOSING_TOKEN;
            } else {
                // This is a fragment.
                if (dvFragAnnotatedField.docID() != docId) {
                    dvFragAnnotatedField.advance(docId);
                    dvFragStart.advance(docId);
                    dvFragEnd.advance(docId);
                }
                fragIsFullDoc = false;
                fragInCorrectField = dvFragAnnotatedField.ordValue() == currentAnnotatedFieldOrd;
                fragStart = (int)dvFragStart.longValue();
                fragEnd = (int)dvFragEnd.longValue();
                // Find the parent document (the next full doc in the index)
                fragFullDocId = fullDocsBitSet.nextSetBit(docId);
            }
        }

        /** Starting from a matching fragment, collect all fragments in this document.
         *
         * Note that, after filtering, there may not be any spans in this document.
         *
         * Precondition: fragmentIterator is positioned at a full document or matching fragment.
         * Postcondition: fragmentIterator is positioned at a full document or matching fragment in a new document
         * (or NO_MORE_DOCS).
         *
         * @return the doc id of the full document we're returning spans from, or NO_MORE_DOCS if there are no more
         */
        private int collectSpansInDocAndFilter() throws IOException {
            assert fragmentIteratorPositioned();
            // We're now at the first fragment in a new document.
            // Collect this and all subsequent fragments in this doc as the spans we'll produce.
            currentDocId = fragFullDocId;
            currentSpan = null;
            spansInCurrentDoc.clear();
            List<Span> spans = hitTester == null ? spansInCurrentDoc : new ArrayList<>();
            // (we already know it's in the correct field, see ensureAtMatchingFrag())
            spans.add(Span.between(fragStart, fragEnd));
            Span fullDocMatch = null;
            while (true) {
                if (fragmentIterator.nextDoc() == DocIdSetIterator.NO_MORE_DOCS) {
                    // No more fragments left
                    break;
                }
                determineFragment();
                if (fragFullDocId != currentDocId) {
                    // This fragment is in a new full document; we'll return it next time
                    break;
                }
                if (fragIsFullDoc) {
                    // Remember full doc match so we can later use it if needed.
                    // (i.e. if there were no fragment matches, or if we prefer the full doc match).
                    fullDocMatch = Span.between(fragStart, fragEnd);
                    // Note that full document is indexed last, so we could break here, but we continue to advance
                    // the fragmentIterator to the next match, which is expected by the rest of the code.
                } else {
                    // This is a fragment; if it's in the correct field,
                    // add it to the list of spans, or merge with the previous span if adjacent
                    if (fragInCorrectField) {
                        addOrMergeSpan(spans, fragStart, fragEnd);
                    }
                }
            } // while

            if (hitTester != null) {
                // We've collected the (merged) fragments in a separate list and now
                // need to filter them using the filter function.
                for (Span span: spans) {
                    if (hitTester == null || hitTester.anyHitsInFragment(currentDocId, span.start(), span.end())) {
                        spansInCurrentDoc.add(span);
                    }
                }
            }
            if (fullDocMatch != null && (spansInCurrentDoc.isEmpty() || behaviour == Behaviour.PREFER_FULL_DOCS)) {
                // Full document matched, but no fragments did, or we prefer the full document match to the fragments.
                // Return the full document match instead of the fragments [if it contains any hits].
                if (hitTester == null || hitTester.anyHitsInDocument(currentDocId)) {
                    spansInCurrentDoc.clear();
                    spansInCurrentDoc.add(fullDocMatch);
                }
            }
            spansIt = spansInCurrentDoc.iterator();
            return currentDocId;
        }

        /** Verify that fragmentIterator has not yet been exhausted. */
        private boolean fragmentIteratorNotExhausted() {
            assert fragmentIterator.docID() != NO_MORE_DOCS : "fragmentIterator exhausted";
            return true;
        }

        /** Verify that fragmentIterator is positioned at a document. */
        private boolean fragmentIteratorPositioned() {
            assert fragmentIterator.docID() >= 0 : "fragmentIterator not yet nexted";
            assert fragmentIteratorNotExhausted();
            return true;
        }

        /** Add a span to the list, combining with the previous span if adjacent. */
        private static void addOrMergeSpan(List<Span> spans, int fragStart, int fragEnd) {
            if (!spans.isEmpty()) {
                Span lastSpan = spans.get(spans.size() - 1);
                if (lastSpan.end() == fragStart) {
                    // Adjacent fragment; combine with previous span
                    spans.set(spans.size() - 1, Span.between(lastSpan.start(), fragEnd));
                    return;
                }
            }
            spans.add(Span.between(fragStart, fragEnd));
        }

        @Override
        public long cost() {
            return Math.min(fragmentIterator.cost(), (hitTester == null ? Long.MAX_VALUE : hitTester.getSpans().cost()));
        }

        @Override
        protected void passHitQueryContextToClauses(HitQueryContext context) {
            // No clauses to pass context to
        }

        @Override
        public boolean hasMatchInfo() {
            return false;
        }

        @Override
        public void getMatchInfo(MatchInfo[] matchInfo) {
            // No match info to return
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
        public void collect(SpanCollector collector) throws IOException {
            // No spans to collect
        }

        @Override
        public float positionsCost() {
            return hitTester == null ? 0 : hitTester.getSpans().positionsCost();
        }
    }
}
