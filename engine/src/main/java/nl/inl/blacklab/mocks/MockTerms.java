package nl.inl.blacklab.mocks;

import java.text.Collator;

import org.eclipse.collections.api.set.primitive.MutableIntSet;

import nl.inl.blacklab.forwardindex.Collators;
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
    public int indexOf(String word) {
        for (int i = 0; i < numberOfTerms(); i++) {
            if (words[i].equals(word)) {
                return i;
            }
        }
        return -1; // Not found
    }

    @Override
    public void indexOf(MutableIntSet results, String term, MatchSensitivity sensitivity) {
        Collator collator = Collators.getDefault().get(sensitivity);
        for (int i = 0; i < numberOfTerms(); i++) {
            if (collator.compare(term, words[i]) == 0) {
                results.add(i);
            }
        }
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
