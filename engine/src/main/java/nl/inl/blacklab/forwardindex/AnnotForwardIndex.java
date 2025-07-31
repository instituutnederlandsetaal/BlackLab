package nl.inl.blacklab.forwardindex;

import java.util.List;

public interface AnnotForwardIndex {
    List<int[]> retrieveParts(int docId, int[] starts, int[] ends);

    int[] retrievePart(int docId, int start, int end);

    long docLength(int docId);

    Terms terms();

    String getLuceneFieldName();
}
