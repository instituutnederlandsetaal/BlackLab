package nl.inl.blacklab.mocks;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

public class MockTerms implements Terms {

    final String[] words;

    public MockTerms(String... words) {
        this.words = words;
    }

    @Override
    public int indexOf(String term) {
        for (int i = 0; i < numberOfTerms(); i++) {
            if (get(i).equals(term))
                return i;
        }
        throw new IllegalArgumentException("Unknown term '" + term + "'");
    }

    @Override
    public String get(int id) {
        return words[id];
    }

    @Override
    public int numberOfTerms() {
        return words.length;
    }

    @Override
    public int idToSortPosition(int id, MatchSensitivity sensitivity) {
        //
        return id;
    }

    @Override
    public int[] segmentIdsToGlobalIds(int ord, int[] snippet) {
        throw new UnsupportedOperationException();
    }
}
