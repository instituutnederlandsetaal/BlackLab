package nl.inl.blacklab.tools.frequency.data;

import java.util.List;

public record DocumentTokens(List<int[]> tokens, List<int[]> sorting) {
}
