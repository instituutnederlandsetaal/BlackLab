package nl.inl.util;

import java.io.IOException;

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.search.DocIdSetIterator;

public class DocValuesUtil {

    private DocValuesUtil() {
    }

    /** Make the DocValues random-access instead of only in order */
    public static SortedDocValuesCacher withRandomAccess(SortedDocValues dv) {
        return dv == null ? null : new SortedDocValuesCacher(dv);
    }

    /** Make the DocValues random-access instead of only in order */
    public static SortedSetDocValuesCacher withRandomAccess(SortedSetDocValues dv) {
        return dv == null ? null : new SortedSetDocValuesCacher(dv);
    }

    /** Make the DocValues random-access instead of only in order */
    public static NumericDocValuesCacher withRandomAccess(NumericDocValues dv) {
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

}
