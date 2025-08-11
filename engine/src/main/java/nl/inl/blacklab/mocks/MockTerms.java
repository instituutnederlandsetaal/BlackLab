package nl.inl.blacklab.mocks;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

public class MockTerms implements Terms {

    final String[] words;

    public MockTerms(String... words) {
        this.words = words;
    }

    @Override
    public int termToSortPosition(String term, MatchSensitivity sensitivity) {
        for (int i = 0; i < numberOfTerms(); i++) {
            if (get(i).equals(term))
                return idToSortPosition(i, sensitivity);
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

}
