package nl.inl.blacklab.index;

/**
 * How many documents/tokens were processed
 */
public record IndexerStats(int documents, long tokens) {
}
