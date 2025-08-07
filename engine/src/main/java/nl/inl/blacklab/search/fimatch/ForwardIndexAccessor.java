package nl.inl.blacklab.search.fimatch;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreMode;

/**
 * Allows the forward index matching subsystem to access the forward indices,
 * including an easy and fast way to read any annotation at any position from a
 * document.
 *
 * {@link #getForwardIndexAccessorLeafReader(LeafReaderContext)} is threadsafe.
 * The other methods are not, but are called from a single thread while initializing
 * the NFA matching process (see {@link nl.inl.blacklab.search.lucene.SpanQueryFiSeq#createWeight(IndexSearcher, ScoreMode, float)}).
 */
public interface ForwardIndexAccessor {

    /**
     * Get the index number corresponding to the given annotation name.
     *
     * @param annotationName annotation to get the index for
     * @return index for this annotation
     */
    int getAnnotationIndex(String annotationName);

    /**
     * Get an accessor for forward index documents from this leafreader.
     *
     * The returned accessor may not be threadsafe, which is okay, because it is only used
     * from Spans (which are always single-threaded).
     *
     * @param readerContext index reader
     * @return reader-specific accessor
     */
    ForwardIndexAccessorLeafReader getForwardIndexAccessorLeafReader(LeafReaderContext readerContext);
}
