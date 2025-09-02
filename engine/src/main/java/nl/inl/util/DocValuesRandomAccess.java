package nl.inl.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SortedDocValues;
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
public class DocValuesRandomAccess<DV extends DocIdSetIterator, V> {

    public static DocValuesRandomAccess<SortedDocValues, String> to(SortedDocValues source) {
        return new DocValuesRandomAccess<>(source, dv -> {
            try {
                return dv.lookupOrd(dv.ordValue()).utf8ToString();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, null);
    }

    public static DocValuesRandomAccess<SortedSetDocValues, String[]> to(SortedSetDocValues source) {
        return new DocValuesRandomAccess<>(source, dv -> {
            try {
                final List<String> ret = new ArrayList<>();
                for (int i = 0; i < source.docValueCount(); i++) {
                    long ord = source.nextOrd();
                    BytesRef val = source.lookupOrd(ord);
                    ret.add(new String(val.bytes, val.offset, val.length, StandardCharsets.UTF_8));
                }
                return ret.toArray(new String[0]);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, null);
    }

    public static DocValuesRandomAccess<NumericDocValues, Long> to(NumericDocValues source) {
        return new DocValuesRandomAccess<>(source, dv -> {
            try {
                return source.longValue();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, null);
    }

    /** DocValues we're reading from */
    private final DV source;

    /** How to get the current value(s) from the DocValues */
    private final Function<DV, V> valueExtractor;

    /** Value to use if no value was registered for this document */
    private final V emptyValue;

    /** Have we called nextDoc on the source yet? */
    private boolean sourceNexted;

    /** the DocValues we've already read */
    private final Map<Integer, V> cache;

    protected DocValuesRandomAccess(DV source, Function<DV, V> valueExtractor, V emptyValue) {
        this.source = source;
        this.valueExtractor = valueExtractor;
        this.emptyValue = emptyValue;
        this.sourceNexted = false;
        this.cache = new HashMap<>();
    }

    public synchronized V get(int docId) {
        try {
            // Have we been there before?
            if (sourceNexted && source.docID() >= docId) {
                // We should have seen this value already.
                // Produce it from the cache.
                return cache.get(docId);
            }
            // Advance to the requested id,
            // storing all values we encounter.
            V currentValue = emptyValue;
            while (source.docID() < docId) {
                int curDocId = source.nextDoc();
                if (curDocId == DocIdSetIterator.NO_MORE_DOCS) {
                    break;
                }
                sourceNexted = true;
                currentValue = valueExtractor.apply(source);
                cache.put(curDocId, currentValue);
            }
            if (source.docID() == docId)
                return currentValue;
            // No values found; return empty value
            cache.put(docId, emptyValue);
            return emptyValue;
        } catch (IOException e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }
}
