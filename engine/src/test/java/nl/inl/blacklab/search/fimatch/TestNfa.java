package nl.inl.blacklab.search.fimatch;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.lucene.index.LeafReaderContext;
import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.forwardindex.TermsSegmentReader;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

public class TestNfa {

    static final class MockFiAccessor implements ForwardIndexAccessor {

        @Override
        public int getAnnotationIndex(String annotName) {
            if (!annotName.equals("word"))
                throw new IllegalArgumentException("only 'word' is valid annotation");
            return 0;
        }

        @Override
        public ForwardIndexAccessorLeafReader getForwardIndexAccessorLeafReader(LeafReaderContext readerContext) {
            return new ForwardIndexAccessorLeafReader() {
                @Override
                public ForwardIndexDocument getForwardIndexDoc(int segmentDocId) {
                    return null;
                }

                @Override
                public int getDocLength(int segmentDocId) {
                    return 0;
                }

                @Override
                public int[] getChunkSegmentTermIds(int annotIndex, int segmentDocId, int start, int end) {
                    return new int[0];
                }

                @Override
                public int getNumberOfAnnotations() {
                    return 0;
                }

                @Override
                public TermsSegmentReader terms(int annotIndex) {
                    return new TermsSegmentReader() {
                        @Override
                        public String get(int id) {
                            return Character.toString((char) id);
                        }

                        @Override
                        public boolean termsEqual(int[] termIds, MatchSensitivity sensitivity) {
                            return termIds.length == 2 && termIds[0] == termIds[1];
                        }

                        @Override
                        public int idToSortPosition(int id, MatchSensitivity sensitivity) {
                            return id;
                        }

                        @Override
                        public void toSortOrder(int[] termIds, int[] sortOrder, MatchSensitivity sensitivity) {
                            for (int i = 0; i < termIds.length; i++) {
                                sortOrder[i] = termIds[i];
                            }
                        }

                        @Override
                        public int indexOf(String term) {
                            return term.charAt(0);
                        }

                        @Override
                        public int numberOfTerms() {
                            return 0;
                        }
                    };
                }
            };
        }

    }

    static class ForwardIndexDocumentString implements ForwardIndexDocument {

        private final String input;

        ForwardIndexDocumentString(String input) {
            this.input = input;
        }

        @Override
        public int getSegmentDocId() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getTokenSegmentTermId(int annotIndex, int pos) {
            if (annotIndex != 0)
                throw new IllegalArgumentException("only 0 is valid annotation");
            if (!validPos(pos))
                return -1;
            return input.charAt(pos);
        }

        @Override
        public int getTokenSegmentSortPosition(int annotIndex, int pos, MatchSensitivity sensitivity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean validPos(int pos) {
            return pos >= 0 && pos < input.length();
        }

        @Override
        public String getTermString(int annotIndex, int segmentTermId) {
            if (annotIndex != 0)
                throw new IllegalArgumentException("only 0 is valid annotation");
            return Character.toString((char) segmentTermId);
        }

        @Override
        public boolean segmentTermsEqual(int annotIndex, int[] segmentTermId, MatchSensitivity sensitivity) {
            if (annotIndex != 0)
                throw new IllegalArgumentException("only 0 is valid annotation");
            for (int i = 1; i < segmentTermId.length; i++) {
                if (segmentTermId[i] != segmentTermId[0])
                    return false;
            }
            return true;
        }
    }

    @Test
    public void testNfaSimple() {
        // Test simple NFA matching ab|ba
        NfaState ab = NfaState.token("contents%word@i", "a", NfaState.token("contents%word@i", "b", null));
        NfaState ba = NfaState.token("contents%word@i", "b", NfaState.token("contents%word@i", "a", null));
        NfaState start = NfaState.or(false, Arrays.asList(ab, ba), true);
        start.finish();
        start.lookupAnnotationIndexes(new MockFiAccessor());
        start = start.forLeafReaderContext(null);

        ForwardIndexDocumentString fiDoc = new ForwardIndexDocumentString("abatoir");
        Assert.assertTrue(start.matches(fiDoc, 0, 1));
        Assert.assertTrue(start.matches(fiDoc, 1, 1));
        Assert.assertFalse(start.matches(fiDoc, 2, 1));
        Assert.assertFalse(start.matches(fiDoc, 6, 1));
    }

    private static NfaState getNfaWithRepetition() {
        NfaState c = NfaState.token("contents%word@i", "c", null);
        NfaState split = NfaState.or(true, Arrays.asList(c, NfaState.token("contents%word@i", "e", null)), false);
        NfaState start = NfaState.token("contents%word@i", "a", split);
        c.setNextState(0, split); // loopback
        start.finish();
        start.lookupAnnotationIndexes(new MockFiAccessor());
        start = start.forLeafReaderContext(null);
        return start;
    }

    @Test
    public void testNfaRepetition() {
        // Test NFA matching ac*e
        NfaState start = getNfaWithRepetition();

        // Forward matching
        Assert.assertTrue(start.matches(new ForwardIndexDocumentString("access"), 0, 1));
        Assert.assertTrue(start.matches(new ForwardIndexDocumentString("aces"), 0, 1));
        Assert.assertTrue(start.matches(new ForwardIndexDocumentString("aether"), 0, 1));
        Assert.assertFalse(start.matches(new ForwardIndexDocumentString("acquire"), 0, 1));
        Assert.assertFalse(start.matches(new ForwardIndexDocumentString("cesium"), 0, 1));

        // Backward matching
        Assert.assertTrue(start.matches(new ForwardIndexDocumentString("ideaal"), 3, -1));
    }

    @Test
    public void testNfaCopy() {
        // Test NFA matching ac*e
        Set<NfaState> dangling = new HashSet<>();
        NfaState start = getNfaWithRepetition().copy(dangling, null);

        // Forward matching
        Assert.assertTrue(start.matches(new ForwardIndexDocumentString("access"), 0, 1));
        Assert.assertTrue(start.matches(new ForwardIndexDocumentString("aces"), 0, 1));
        Assert.assertTrue(start.matches(new ForwardIndexDocumentString("aether"), 0, 1));
        Assert.assertFalse(start.matches(new ForwardIndexDocumentString("acquire"), 0, 1));
        Assert.assertFalse(start.matches(new ForwardIndexDocumentString("cesium"), 0, 1));

        // Backward matching
        Assert.assertTrue(start.matches(new ForwardIndexDocumentString("ideaal"), 3, -1));
    }

}
