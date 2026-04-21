package nl.inl.blacklab.tools.frequency.data.document;

import java.util.Arrays;
import java.util.Objects;

public record DocumentMetadata(int[] values, int hash) {
    @Override
    public String toString() {
        return "DocumentMetadata{" +
                "values=" + Arrays.toString(values) +
                ", hash=" + hash +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DocumentMetadata that))
            return false;
        return hash == that.hash && Objects.deepEquals(values, that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(values), hash);
    }
}
