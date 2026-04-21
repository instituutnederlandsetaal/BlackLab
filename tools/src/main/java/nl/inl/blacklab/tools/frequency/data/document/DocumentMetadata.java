package nl.inl.blacklab.tools.frequency.data.document;

import java.util.Arrays;

public record DocumentMetadata(int[] values, int hash) {
    @Override
    public String toString() {
        return "DocumentMetadata{" +
                "values=" + Arrays.toString(values) +
                ", hash=" + hash +
                '}';
    }
}
