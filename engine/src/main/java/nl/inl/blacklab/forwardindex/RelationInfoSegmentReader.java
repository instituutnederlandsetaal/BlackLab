package nl.inl.blacklab.forwardindex;

import java.util.Map;

import net.jcip.annotations.NotThreadSafe;

/**
 * Provides read access to the forward indexes in a single Lucene index segment.
 *
 * Implementations are not intended to be threadsafe, but to be used by a single
 * thread.
 */
@NotThreadSafe
public interface RelationInfoSegmentReader {

    /** Retrieve parts of a document from a forward index.
     *
     * @param luceneField lucene field to retrieve snippet from
     * @param docId segment-local docId of document to retrieve snippet from
     * @param relationId relation id
     * @return attributes
     */
    Map<String, String> getAttributes(String luceneField, int docId, int relationId);
}
