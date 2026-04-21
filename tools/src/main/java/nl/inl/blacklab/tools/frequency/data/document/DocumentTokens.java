package nl.inl.blacklab.tools.frequency.data.document;

import java.util.Arrays;
import java.util.Objects;

public record DocumentTokens(int[][] tokens, int[][] sorting) {
    @Override
    public String toString() {
        return "DocumentTokens{" +
                "tokens=" + Arrays.toString(tokens) +
                ", sorting=" + Arrays.toString(sorting) +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DocumentTokens that))
            return false;
        return Objects.deepEquals(tokens, that.tokens) && Objects.deepEquals(sorting, that.sorting);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.deepHashCode(tokens), Arrays.deepHashCode(sorting));
    }
}
