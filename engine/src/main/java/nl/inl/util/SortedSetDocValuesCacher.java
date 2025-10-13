package nl.inl.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.util.BytesRef;

import net.jcip.annotations.NotThreadSafe;
import nl.inl.blacklab.exceptions.BlackLabException;

/**
 * Wrap a SortedSetDocValues to enable random access.
 * <p>
 * Not thread-safe.
 */
@NotThreadSafe
public class SortedSetDocValuesCacher {

    /**
     * DocValues we're reading from
     */
    private final SortedSetDocValues source;

    /**
     * Have we called nextDoc on the source yet?
     */
    private boolean sourceNexted;

    /**
     * DocValues already read
     */
    private final Map<Integer, String[]> cache;

    protected SortedSetDocValuesCacher(SortedSetDocValues source) {
        this.source = source;
        this.sourceNexted = false;
        this.cache = new HashMap<>();
    }

    public synchronized String[] get(int docId) {
        try {
            // Have we been there before?
            if (sourceNexted && source.docID() > docId) {
                // We should have seen this value already.
                // Produce it from the cache.
                if (cache.containsKey(docId)) {
                    return cache.get(docId);
                }
            } else {
                // Advance to the requested id,
                // storing all values we encounter.
                while (source.docID() < docId) {
                    int curDocId = source.nextDoc();
                    if (curDocId == DocIdSetIterator.NO_MORE_DOCS) {
                        break;
                    }
                    sourceNexted = true;

                    final List<String> ret = new ArrayList<>();

                    for (long ord = source.nextOrd(); ord != SortedSetDocValues.NO_MORE_ORDS; ord = source.nextOrd()) {
                        BytesRef val = source.lookupOrd(ord);
                        ret.add(new String(val.bytes, val.offset, val.length, StandardCharsets.UTF_8));
                    }
                    cache.put(curDocId, ret.toArray(new String[0]));
                }
                if (source.docID() == docId) {
                    return cache.get(docId);
                }
            }
            return new String[0]; // no values found; return empty array
        } catch (IOException e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

}
