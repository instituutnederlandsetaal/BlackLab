package nl.inl.blacklab.forwardindex;

import net.jcip.annotations.NotThreadSafe;

/**
 * Class for looking up forward index id, using DocValues or stored fields.
 *
 * Not all implementations are thread-safe (using DocValues without synchronization).
 *
 * CAUTION: if created without random access enabled, the get() method can
 * only be called with ascending doc ids!
 */
@NotThreadSafe
public interface FiidLookup {

    /**
     * The dummy FiidLookup just returns the docId. This is used for the integrated index,
     * where the forward index is part of the Lucene index, so there are no separate ids.
     */
    FiidLookup USE_DOC_ID = docId -> docId;

    /**
     * Return the forward index id for the given Lucene doc id.
     *
     * Uses DocValues to retrieve the fiid from the Lucene Document.
     *
     * CAUTION: docId must always be equal to or greater than the previous docId
     * this method was called with! (because DocValues API is sequential now)
     *
     * @param docId Lucene doc id
     * @return forward index id (fiid)
     */
    int get(int docId);
}
