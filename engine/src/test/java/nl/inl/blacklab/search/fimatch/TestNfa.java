package nl.inl.blacklab.search.fimatch;

import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;

import org.apache.lucene.index.LeafReaderContext;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

public class TestNfa {

    static final class MockFiAccessor implements ForwardIndexAccessor {

        @Override
        public void getGlobalTermNumbers(MutableIntSet results, int annotNumber, String annotValue,
                MatchSensitivity sensitivity) {
            if (annotNumber != 0)
                throw new IllegalArgumentException("only 0 is valid annotation");
            if (annotValue.length() > 1)
                throw new IllegalArgumentException("only words of length 1 are valid");
            results.add(annotValue.charAt(0));
        }

        @Override
        public int getAnnotationNumber(String annotName) {
            if (!annotName.equals("word"))
                throw new IllegalArgumentException("only 'word' is valid annotation");
            return 0;
        }

        @Override
        public ForwardIndexAccessorLeafReader getForwardIndexAccessorLeafReader(LeafReaderContext readerContext) {
            return null;
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
        start.finish(new HashSet<>());
        start.lookupAnnotationNumbers(new MockFiAccessor(), new IdentityHashMap<>());

        ForwardIndexDocumentString fiDoc = new ForwardIndexDocumentString("abatoir");
        Assert.assertTrue(start.matches(fiDoc, 0, 1));
        Assert.assertTrue(start.matches(fiDoc, 1, 1));
        Assert.assertFalse(start.matches(fiDoc, 2, 1));
        Assert.assertFalse(start.matches(fiDoc, 6, 1));
    }

    @Test
    public void testNfaRepetition() {
        // Test NFA matching ac*e
        NfaState c = NfaState.token("contents%word@i", "c", null);
        NfaState split = NfaState.or(true, Arrays.asList(c, NfaState.token("contents%word@i", "e", null)), false);
        NfaState start = NfaState.token("contents%word@i", "a", split);
        c.setNextState(0, split); // loopback
        start.finish(new HashSet<>());
        start.lookupAnnotationNumbers(new MockFiAccessor(), new IdentityHashMap<>());

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
