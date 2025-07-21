package nl.inl.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.search.DocIdSetIterator;

import nl.inl.blacklab.exceptions.InvalidIndex;

public class DocValuesUtil {

    private DocValuesUtil() {
    }

    public static SortedDocValuesCacher cacher(SortedDocValues dv) {
        return dv == null ? null : new SortedDocValuesCacher(dv);
    }

    public static SortedSetDocValuesCacher cacher(SortedSetDocValues dv) {
        return dv == null ? null : new SortedSetDocValuesCacher(dv);
    }

    public static NumericDocValuesCacher cacher(NumericDocValues dv) {
        return dv == null ? null : new NumericDocValuesCacher(dv);
    }

    /**
     * Get the appropriate DocValues instance for the given field.
     *
     * @param r index segment
     * @param fieldName field to get DocValues for
     * @param isNumeric whether the field is numeric
     * @return DocValues instance, or null if field has no values in this segment
     */
    public static DocIdSetIterator docValuesIterator(LeafReader r, String fieldName, boolean isNumeric) throws
            IOException {
        DocIdSetIterator dv;
        if (isNumeric) {
            dv = r.getNumericDocValues(fieldName);
        } else {
            dv = r.getSortedSetDocValues(fieldName);
            if (dv == null) {
                dv = r.getSortedDocValues(fieldName);
            }
        }
        return dv;
    }

    /**
     * Get the current value from a DocValues instance and cast to string.
     *
     * NOTE: For multi-value fields, only returns the first value!
     *
     * @param dv DocValues instance positioned at a valid document
     * @return value for this document
     */
    public static List<String> getCurrentValues(DocIdSetIterator dv) {
        try {
            List<String> key = null;
            if (dv instanceof NumericDocValues)
                key = List.of(Long.toString(((NumericDocValues) dv).longValue()));
            else if (dv instanceof SortedSetDocValues ssdv) {
                for (int i = 0; i < ssdv.docValueCount(); i++) {
                    long ord = ssdv.nextOrd();
                    if (key == null)
                        key = new ArrayList<>();
                    key.add(ssdv.lookupOrd(ord).utf8ToString());
                }
            } else if (dv instanceof SortedDocValues sdv) {
                // OPT: avoid looking up the value and just use the ord directly if possible
                // (e.g. while determining frequencies in MetadataFieldVAluesFromIndex.determineValueDistribution())
                // See LUCENE-9796 in https://lucene.apache.org/core/9_0_0/MIGRATE.html
                key = List.of(sdv.lookupOrd(sdv.ordValue()).utf8ToString());
            } else {
                throw new IllegalStateException("Unexpected DocValues type");
            }
            return key == null ? Collections.emptyList() : key;
        } catch (IOException e) {
            throw new InvalidIndex(e);
        }
    }
}
