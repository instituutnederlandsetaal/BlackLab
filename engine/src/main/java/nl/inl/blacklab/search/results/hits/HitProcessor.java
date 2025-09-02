package nl.inl.blacklab.search.results.hits;

/**
 * Deals with hits found in a segment. Works with HitCollector.
 */
public interface HitProcessor {
    /**
     * Called when the SpansReader has reached the end of a document.
     *
     * @param results the hits collected so far
     * @return whether to continue storing hits, or just count them, or stop altogether
     */
    SpansReader.Phase onDocumentBoundary(HitsMutable results, long counted);

    /**
     * Called when the SpansReader is done.
     *
     * @param results the hits collected so far
     */
    void onFinished(HitsMutable results, long counted);

    long globalProcessedSoFar();

    long globalCountedSoFar();
}
