package nl.inl.blacklab.mocks;

import com.ibm.icu.text.Collator;

import nl.inl.blacklab.forwardindex.Collators;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

public class MockTerms implements Terms {

    final String[] words;

    public MockTerms(String... words) {
        this.words = words;
    }

    public int id(String word) {
        return indexOf(word, MatchSensitivity.SENSITIVE);
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
    public int indexOf(String word, MatchSensitivity sensitivity) {
        Collator coll = Collators.getDefault().get(sensitivity);
        for (int i = 0; i < numberOfTerms(); i++) {
            if (coll.compare(words[i], word) == 0) {
                return i;
            }
        }
        return -1; // Not found
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
