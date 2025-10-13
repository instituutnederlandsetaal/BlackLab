package nl.inl.util;

import java.io.IOException;

import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.search.DocIdSetIterator;
import org.eclipse.collections.api.map.primitive.MutableIntLongMap;
import org.eclipse.collections.impl.map.mutable.primitive.IntLongHashMap;

import net.jcip.annotations.NotThreadSafe;
import nl.inl.blacklab.exceptions.BlackLabException;

/**
 * Wrap a NumericDocValues to enable random access.
 * <p>
 * Not thread-safe.
 */
@NotThreadSafe
public class NumericDocValuesCacher {

    /**
     * DocValues we're reading from
     */
    private final NumericDocValues source;

    /**
     * Have we called nextDoc on the source yet?
     */
    private boolean sourceNexted;

    /**
     * DocValues already read
     */
    private final MutableIntLongMap cache;

    protected NumericDocValuesCacher(NumericDocValues source) {
        this.source = source;
        this.sourceNexted = false;
        this.cache = new IntLongHashMap();
    }

    public synchronized long get(int docId) {
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
                    cache.put(curDocId, source.longValue());
                }
                if (source.docID() == docId)
                    return source.longValue();
            }
            return 0L; // default missing value
        } catch (IOException e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

}
