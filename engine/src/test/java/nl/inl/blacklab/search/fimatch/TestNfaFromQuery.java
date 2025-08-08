package nl.inl.blacklab.search.fimatch;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.spans.BLSpanOrQuery;
import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.forwardindex.TermsSegmentReader;
import nl.inl.blacklab.mocks.MockBlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.BLSpanTermQuery;
import nl.inl.blacklab.search.lucene.SpanQueryAnd;
import nl.inl.blacklab.search.lucene.SpanQueryAnyToken;
import nl.inl.blacklab.search.lucene.SpanQueryExpansion;
import nl.inl.blacklab.search.lucene.SpanQueryExpansion.Direction;
import nl.inl.blacklab.search.lucene.SpanQueryNot;
import nl.inl.blacklab.search.lucene.SpanQueryRepetition;
import nl.inl.blacklab.search.lucene.SpanQuerySequence;
import nl.inl.blacklab.search.results.QueryInfo;

public class TestNfaFromQuery {

    static class MockForwardIndexAccessor implements ForwardIndexAccessor {

        final int[] termIds;

        private final Map<String, Integer> terms = new HashMap<>();

        public MockForwardIndexAccessor(String... document) {
            this.termIds = new int[document.length];
            for (int i = 0; i < document.length; i++) {
                Integer termId = terms.computeIfAbsent(document[i], k -> terms.size());
                termIds[i] = termId;
            }
        }

        @Override
        public int getAnnotationIndex(String annotationName) {
            if (annotationName.equals("word"))
                return 0;
            throw new IllegalArgumentException("Unknown annotation " + annotationName);
        }

        public int numberOfAnnotations() {
            return 1;
        }

        @Override
        public ForwardIndexAccessorLeafReader getForwardIndexAccessorLeafReader(LeafReaderContext readerContext) {
            return new ForwardIndexAccessorLeafReader() {

                @Override
                public TermsSegmentReader terms(int annotIndex) {
                    return new TermsSegmentReader() {
                        @Override
                        public String get(int id) {
                            for (Entry<String, Integer> e : terms.entrySet()) {
                                if (e.getValue() == id)
                                    return e.getKey();
                            }
                            return null; // should not happen
                        }

                        @Override
                        public boolean termsEqual(int[] termIds, MatchSensitivity sensitivity) {
                            return termIds.length == 2 && termIds[0] == termIds[1];
                        }

                        @Override
                        public int idToSortPosition(int id, MatchSensitivity sensitivity) {
                            return id; // wrong, but not used for sorting in these tests
                        }

                        @Override
                        public void toSortOrder(int[] termIds, int[] sortOrder, MatchSensitivity sensitivity) {
                            for (int i = 0; i < termIds.length; i++) {
                                sortOrder[i] = termIds[i];
                            }
                        }

                        @Override
                        public int sortPositionFor(String term, MatchSensitivity sensitivity) {
                            // Wrong (we return id, not sort pos), but we only need equal words to have the same sort position for these tests.
                            return terms.get(term);
                        }

                        @Override
                        public int indexOf(String term) {
                            return terms.get(term);
                        }

                        @Override
                        public int numberOfTerms() {
                            return terms.size();
                        }
                    };
                }

                public String getTermString(int annotIndex, int segmentTermId) {
                    return MockForwardIndexAccessor.this.getTermString(annotIndex, segmentTermId);
                }

                /**
                 * Get the number of mapped annotations.
                 *
                 * Annotations are mapped before the matching starts, so we can simply pass an
                 * annotation index instead of annotation names, which would be too slow.
                 *
                 * @return number of mapped annotations.
                 */
                @Override
                public int getNumberOfAnnotations() {
                    return MockForwardIndexAccessor.this.numberOfAnnotations();
                }

                @Override
                public ForwardIndexDocument getForwardIndexDoc(int segmentDocId) {
                    if (segmentDocId != 0)
                        throw new IllegalArgumentException("Unknown document " + segmentDocId);
                    return new ForwardIndexDocumentIntArray(termIds);
                }

                @Override
                public int getDocLength(int segmentDocId) {
                    if (segmentDocId != 0)
                        throw new IllegalArgumentException("Unknown document " + segmentDocId);
                    return termIds.length;
                }

                @Override
                public int[] getChunkSegmentTermIds(int annotIndex, int segmentDocId, int start, int end) {
                    throw new UnsupportedOperationException();
                }

            };
        }

        public String getTermString(int annotIndex, int termId) {
            for (Entry<String, Integer> e : terms.entrySet()) {
                if (e.getValue() == termId) {
                    return e.getKey();
                }
            }
            return null;
        }

    }

    static class ForwardIndexDocumentIntArray implements ForwardIndexDocument {

        private final int[] input;

        ForwardIndexDocumentIntArray(int[] input) {
            this.input = input;
        }

        @Override
        public int getSegmentDocId() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getTokenSegmentTermId(int annotIndex, int pos) {
            if (!validPos(pos))
                return -1;
            return input[pos];
        }

        @Override
        public int getTokenSegmentSortPosition(int annotIndex, int pos, MatchSensitivity sensitivity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean validPos(int pos) {
            return pos >= 0 && pos < input.length;
        }

        @Override
        public String getTermString(int annotIndex, int segmentTermId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean segmentTermsEqual(int annotIndex, int[] segmentTermId, MatchSensitivity sensitivity) {
            throw new UnsupportedOperationException();
        }
    }

    private static final List<Integer> NO_MATCHES = Collections.emptyList();

    private static void test(BLSpanQuery q, ForwardIndexAccessor fiAccessor, int startPos, int direction, int tests,
            List<Integer> matches) {
        // The NFA
        Nfa frag = q.getNfa(fiAccessor, direction);
        frag.finish();
        frag.lookupAnnotationIndexes(fiAccessor);
        NfaState start = frag.getStartingState().forLeafReaderContext(null);

        ForwardIndexDocument fiDoc = fiAccessor.getForwardIndexAccessorLeafReader(null).getForwardIndexDoc(0);
        for (int i = 0; i < tests; i++) {
            Assert.assertEquals("Test " + i, matches.contains(i),
                    start.matches(fiDoc, startPos + direction * i, direction));
        }
    }

    private static SpanQueryRepetition rep(BLSpanQuery clause, int min, int max) {
        return new SpanQueryRepetition(clause, min, max);
    }

    private static BLSpanTermQuery term(String w) {
        return new BLSpanTermQuery(QueryInfo.create(new MockBlackLabIndex()), new Term("contents%word@i", w));
    }

    private static SpanQuerySequence seq(BLSpanQuery... clauses) {
        return new SpanQuerySequence(clauses);
    }

    private static BLSpanOrQuery or(BLSpanQuery... clauses) {
        return new BLSpanOrQuery(clauses);
    }

    private static SpanQueryAnd and(BLSpanQuery... clauses) {
        return new SpanQueryAnd(clauses);
    }

    private static SpanQueryAnyToken any(int min, int max) {
        return new SpanQueryAnyToken(QueryInfo.create(new MockBlackLabIndex()), min, max, "contents%word@i");
    }

    private static BLSpanQuery exp(BLSpanQuery clause, Direction direction, int min, int max) {
        return new SpanQueryExpansion(clause, direction, min, max);
    }

    private static SpanQueryNot not(BLSpanQuery clause) {
        return new SpanQueryNot(clause);
    }

    @Test
    public void testNfaSingleWord() {
        // The test document
        ForwardIndexAccessor fiAccessor = new MockForwardIndexAccessor("This", "is", "a", "test");

        // The query: "test"
        BLSpanQuery q = term("test");

        test(q, fiAccessor, 0, 1, 4, List.of(3));
        test(q, fiAccessor, 3, -1, 4, List.of(0));
    }

    @Test
    public void testNfaSequence() {
        // The test document
        ForwardIndexAccessor fiAccessor = new MockForwardIndexAccessor("This", "is", "a", "test");

        // The query: "a" "test"
        BLSpanQuery q = seq(term("a"), term("test"));

        test(q, fiAccessor, 0, 1, 4, List.of(2));
        test(q, fiAccessor, 3, -1, 4, List.of(0));
    }

    @Test
    public void testNfaRep1() {
        // The test document
        ForwardIndexAccessor fiAccessor = new MockForwardIndexAccessor("This", "is", "very", "very", "very", "fun");

        // The query: "very"+
        BLSpanQuery q = rep(term("very"), 1, BLSpanQuery.MAX_UNLIMITED);

        test(q, fiAccessor, 0, 1, 6, Arrays.asList(2, 3, 4));
        test(q, fiAccessor, 5, -1, 6, Arrays.asList(1, 2, 3));
    }

    @Test
    public void testNfaRep2() {
        // The test document
        ForwardIndexAccessor fiAccessor = new MockForwardIndexAccessor("This", "is", "very", "very", "very", "fun");

        // The query: "is" "very"{1,2}
        BLSpanQuery q = seq(term("is"), rep(term("very"), 1, 2));

        test(q, fiAccessor, 0, 1, 6, List.of(1));
        test(q, fiAccessor, 5, -1, 6, Arrays.asList(2, 3));
    }

    @Test
    public void testNfaOr() {
        // The test document
        ForwardIndexAccessor fiAccessor = new MockForwardIndexAccessor("This", "is", "very", "very", "very", "fun");

        // The query: "is" "very" | "very" "fun"
        BLSpanQuery q = or(seq(term("is"), term("very")), seq(term("very"), term("fun")));

        test(q, fiAccessor, 0, 1, 6, Arrays.asList(1, 4));
        test(q, fiAccessor, 5, -1, 6, Arrays.asList(0, 3));
    }

    @Test
    public void testNfaAny() {
        // The test document
        ForwardIndexAccessor fiAccessor = new MockForwardIndexAccessor("This", "is", "very", "very", "very", "fun");

        // The query: []{3,3}
        BLSpanQuery q = any(3, 3);

        test(q, fiAccessor, 0, 1, 6, Arrays.asList(0, 1, 2, 3));
        test(q, fiAccessor, 5, -1, 6, Arrays.asList(0, 1, 2, 3));
    }

    @Test
    public void testNfaAnd() {
        // The test document
        ForwardIndexAccessor fiAccessor = new MockForwardIndexAccessor("This", "is", "very", "very", "very", "fun");

        // The query: ("very" "very" []) & ([] "very" "very")
        BLSpanQuery q = and(seq(term("very"), term("very"), any(1, 1)), seq(any(1, 1), term("very"), term("very")));

        test(q, fiAccessor, 0, 1, 6, List.of(2));
        test(q, fiAccessor, 5, -1, 6, List.of(1));
    }

    @Test
    public void testNfaExpansionLeft() {
        // The test document
        ForwardIndexAccessor fiAccessor = new MockForwardIndexAccessor("This", "is", "very", "very", "very", "fun");

        // The query: []{1,2} very
        BLSpanQuery q = exp(term("very"), Direction.LEFT, 1, 2);

        test(q, fiAccessor, 0, 1, 6, Arrays.asList(0, 1, 2, 3));
        test(q, fiAccessor, 5, -1, 6, Arrays.asList(1, 2, 3));
    }

    @Test
    public void testNfaExpansionRight() {
        // The test document
        ForwardIndexAccessor fiAccessor = new MockForwardIndexAccessor("This", "is", "very", "very", "very", "fun");

        // The query: "is" []{0,3}
        BLSpanQuery q = exp(term("is"), Direction.RIGHT, 0, 3);

        test(q, fiAccessor, 0, 1, 6, List.of(1));
        test(q, fiAccessor, 5, -1, 6, Arrays.asList(1, 2, 3, 4));
    }

    @Test
    public void testNfaNot() {
        // The test document
        ForwardIndexAccessor fiAccessor = new MockForwardIndexAccessor("This", "is", "very", "very", "very", "fun");

        // The query: [word != "very"]
        BLSpanQuery q = not(term("very"));

        test(q, fiAccessor, 0, 1, 6, Arrays.asList(0, 1, 5));
        test(q, fiAccessor, 5, -1, 6, Arrays.asList(0, 4, 5));
    }

    @Test
    public void testNfaComplex0() {
        // The test document
        ForwardIndexAccessor fiAccessor = new MockForwardIndexAccessor("This", "is", "lots", "and", "lots", "and",
                "lots", "of", "fun");

        // The query: [word != "lots"] "of"
        BLSpanQuery q = seq(not(term("lots")), term("of"));

        test(q, fiAccessor, 0, 1, 9, NO_MATCHES);
        test(q, fiAccessor, 8, -1, 9, NO_MATCHES);
    }

    @Test
    public void testNfaComplex1() {
        // The test document
        ForwardIndexAccessor fiAccessor = new MockForwardIndexAccessor("This", "is", "lots", "and", "lots", "and",
                "lots", "of", "fun");

        // The query: []? "is" ("lots" "and"){1,3} [word != "lots"] "of"
        BLSpanQuery q = seq(exp(term("is"), Direction.LEFT, 0, 1), rep(seq(term("lots"), term("and")), 1, 3), not(term("lots")),
                term("of"));

        test(q, fiAccessor, 0, 1, 9, NO_MATCHES);
        test(q, fiAccessor, 8, -1, 9, NO_MATCHES);
    }

    @Test
    public void testNfaComplex2() {
        // The test document
        ForwardIndexAccessor fiAccessor = new MockForwardIndexAccessor("This", "is", "lots", "and", "lots", "and",
                "lots", "of", "fun");

        // The query: []? "is" ("lots" "and"){1,3} "lots" "of"
        BLSpanQuery q = seq(exp(term("is"), Direction.LEFT, 0, 1), rep(seq(term("lots"), term("and")), 1, 3), term("lots"),
                term("of"));

        test(q, fiAccessor, 0, 1, 9, Arrays.asList(0, 1));
        test(q, fiAccessor, 8, -1, 9, List.of(1));
    }

    @Test
    public void testNfaRepeatedNot() {
        // The test document
        ForwardIndexAccessor fiAccessor = new MockForwardIndexAccessor("This", "is", "lots", "and", "lots", "and",
                "lots", "of", "fun");

        // The query: "and" [word != "of"]{2}
        BLSpanQuery q = seq(term("and"), rep(not(term("of")), 2, 2));

        test(q, fiAccessor, 0, 1, 9, List.of(3));
        test(q, fiAccessor, 8, -1, 9, List.of(3));
    }
}
