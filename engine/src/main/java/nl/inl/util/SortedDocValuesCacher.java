package nl.inl.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.util.BytesRef;

import net.jcip.annotations.NotThreadSafe;
import nl.inl.blacklab.exceptions.BlackLabException;

/**
 * Wrap a SortedDocValues to enable random access.
 * <p>
 * Not thread-safe.
 */
@NotThreadSafe
public class SortedDocValuesCacher {

    /**
     * DocValues we're reading from
     */
    private final SortedDocValues source;

    /**
     * Have we called nextDoc on the source yet?
     */
    private boolean sourceNexted;

    /**
     * DocValues already read
     */
    private final Map<Integer, String> cache;

    protected SortedDocValuesCacher(SortedDocValues source) {
        this.source = source;
        this.sourceNexted = false;
        this.cache = new HashMap<>();
    }

    public synchronized String get(int docId) {
        try {
            // Have we been there before?
            if (sourceNexted && source.docID() > docId) {
                // We should have seen this value already.
                // Produce it from the cache.
                if (cache.containsKey(docId))
                    return cache.get(docId);
            } else {
                // Advance to the requested id,
                // storing all values we encounter.
                while (source.docID() < docId) {
                    int curDocId = source.nextDoc();
                    if (curDocId == DocIdSetIterator.NO_MORE_DOCS)
                        break;
                    sourceNexted = true;
                    BytesRef bytes = source.binaryValue();// equals to a.get(docId - targetDocBase)?
                    String value = new String(bytes.bytes, bytes.offset, bytes.length, StandardCharsets.UTF_8);
                    cache.put(curDocId, value);
                }
                if (source.docID() == docId) {
                    return cache.get(docId);
                }
            }
            return null; // default missing value
        } catch (IOException e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

}
