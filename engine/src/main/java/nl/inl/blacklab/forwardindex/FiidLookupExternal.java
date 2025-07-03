package nl.inl.blacklab.forwardindex;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;

import net.jcip.annotations.NotThreadSafe;
import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.search.indexmetadata.Annotation;

/**
 * Class for looking up forward index id, using DocValues or stored fields.
 *
 * This class is not thread-safe (using DocValues without synchronization).
 *
 * CAUTION: if created without random access enabled, the get() method can
 * only be called with ascending doc ids!
 */
@NotThreadSafe
public class FiidLookupExternal implements FiidLookup {

    /**
     * Index reader, for getting documents (for translating from Lucene doc id to
     * fiid)
     */
    private final IndexReader reader;

    /**
     * fiid field name in the Lucene index (for translating from Lucene doc id to
     * fiid)
     */
    private final String fiidFieldName;

    /** The DocValues per segment (keyed by docBase) */
    private final Map<Integer, NumericDocValues> cachedFiids = new TreeMap<>();

    /** Any cached mappings from Lucene docId to forward index id (fiid) or null if not using cache */
    private final Map<Integer, Long> docIdToFiidCache;

    public FiidLookupExternal(IndexReader reader, Annotation annotation, boolean enableRandomAccess) {
        this.reader = reader;
        this.fiidFieldName = annotation.forwardIndexIdField();
        this.docIdToFiidCache = enableRandomAccess ? new HashMap<>() : null;
        try {
            for (LeafReaderContext rc : reader.leaves()) {
                LeafReader r = rc.reader();
                NumericDocValues numericDocValues = r.getNumericDocValues(fiidFieldName);
                if (numericDocValues == null) {
                    // (should never happen)
                    throw new UnsupportedOperationException("no DocValues available");
                }
                cachedFiids.put(rc.docBase, numericDocValues);
            }
        } catch (IOException e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

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
    @Override
    public int get(int docId) {
        // Is the fiid in the cache (if we have one)?
        if (docIdToFiidCache != null) {
            // Yes; return value from the cache.
            Long fiid = docIdToFiidCache.get(docId);
            if (fiid != null)
                return (int)(long)fiid;
        }

        // Find the fiid in the correct segment
        Entry<Integer, NumericDocValues> prev = null;
        for (Entry<Integer, NumericDocValues> e : cachedFiids.entrySet()) {
            Integer docBase = e.getKey();
            if (docBase > docId) {
                // Previous segment (the highest docBase lower than docId) is the right one
                assert prev != null;
                Integer prevDocBase = prev.getKey();
                NumericDocValues prevDocValues = prev.getValue();
                return getFiidFromDocValues(prevDocBase, prevDocValues, docId);
            }
            prev = e;
        }
        // Last segment is the right one
        assert prev != null;
        Integer prevDocBase = prev.getKey();
        NumericDocValues prevDocValues = prev.getValue();
        return getFiidFromDocValues(prevDocBase, prevDocValues, docId);
    }

    /**
     * Get the requested forward index id from the DocValues object.
     *
     * Optionally caches any skipped values for later.
     *
     * @param docBase doc base for this segement
     * @param docValues doc values for this segment
     * @param docId document to get the fiid for
     * @return forward index id
     */
    private int getFiidFromDocValues(int docBase, NumericDocValues docValues, int docId) {
        try {
            if (docIdToFiidCache == null) {
                // Not caching (because we know our docIds are always increasing)
                docValues.advanceExact(docId - docBase);
                return (int) docValues.longValue();
            } else {
                // Caching; gather all fiid values in our cache until we find the requested one.
                do {
                    docValues.nextDoc();
                    docIdToFiidCache.put(docValues.docID() + docBase, docValues.longValue());
                    if (docValues.docID() == docId - docBase) {
                        // Requested docvalue found.
                        return (int) docValues.longValue();
                    }
                } while (docValues.docID() <= docId - docBase);
                throw new RuntimeException("not found in docvalues");
            }
        } catch (IOException e1) {
            throw BlackLabException.wrapRuntime(e1);
        }
    }

}
