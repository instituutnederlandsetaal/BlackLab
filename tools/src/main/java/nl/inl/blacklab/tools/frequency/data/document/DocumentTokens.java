package nl.inl.blacklab.tools.frequency.data.document;

import java.util.Arrays;

public record DocumentTokens(int[][] tokens, int[][] sorting) {
    @Override
    public String toString() {
        return "DocumentTokens{" +
                "tokens=" + Arrays.toString(tokens) +
                ", sorting=" + Arrays.toString(sorting) +
                '}';
    }
}
