package nl.inl.blacklab.search.results.hits.fetch;

import nl.inl.blacklab.search.results.hits.HitsMutable;

/**
 * Deals with hits found in a segment. Works with HitCollector.
 *
 * Implementations don't need to be thread-safe, as each segment is handled by a single thread.
 */
public interface HitProcessor {
    /**
     * Called when the SpansReader has reached the end of a document.
     *
     * @param results the hits collected so far
     * @return whether to continue storing hits, or just count them, or stop altogether
     */
    HitFetcher.Phase onDocumentBoundary(HitsMutable results, long counted);

    /**
     * Called when the SpansReader is done.
     *
     * @param results the hits collected so far
     */
    void onFinished(HitsMutable results, long counted);

    long globalProcessedSoFar();

    long globalCountedSoFar();
}
