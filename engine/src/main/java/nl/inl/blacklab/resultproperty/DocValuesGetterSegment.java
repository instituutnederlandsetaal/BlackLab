package nl.inl.blacklab.resultproperty;

import java.io.IOException;

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedSetDocValues;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.util.DocValuesUtil;
import nl.inl.util.NumericDocValuesCacher;
import nl.inl.util.SortedDocValuesCacher;
import nl.inl.util.SortedSetDocValuesCacher;

class DocValuesGetterSegment implements DocValuesGetter {
    private SortedDocValuesCacher sortedDocValues;
    private SortedSetDocValuesCacher sortedSetDocValues;
    private NumericDocValuesCacher numericDocValues;

    public DocValuesGetterSegment(BlackLabIndex index, LeafReaderContext lrc, String fieldName) {
        try {
            if (index.reader() != null) { // skip for MockIndex (testing)
                LeafReader leafReader = lrc.reader();
                // NOTE: can be null! This is valid and indicates the documents in this segment does not contain any values for this field.
                NumericDocValues numericDv = leafReader.getNumericDocValues(fieldName);
                if (numericDv != null) {
                    numericDocValues = DocValuesUtil.withRandomAccess(lrc.reader().getNumericDocValues(fieldName));
                } else {
                    SortedSetDocValues sortedSetDv = leafReader.getSortedSetDocValues(fieldName);
                    if (sortedSetDv != null) {
                        sortedSetDocValues = DocValuesUtil.withRandomAccess(sortedSetDv);
                    } else {
                        SortedDocValues sortedDv = leafReader.getSortedDocValues(fieldName);
                        if (sortedDv != null) {
                            sortedDocValues = DocValuesUtil.withRandomAccess(sortedDv);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    public boolean isValid() {
        return sortedSetDocValues != null || sortedDocValues != null || numericDocValues != null;
    }

    public String[] get(int docId) {
        if (sortedSetDocValues != null) {
            // newer index, (possibly) multiple values.
            return sortedSetDocValues.get(docId);
        } else if (numericDocValues != null) {
            // numeric field
            return new String[] { numericDocValues.get(docId) + "" };
        } else if (sortedDocValues != null) {
            // old index, only one value
            String value = sortedDocValues.get(docId);
            if (value != null)
                return new String[] { value };
        }
        // If no docvalues for this segment - no values were indexed for this field (in this segment).
        // So returning the empty array is good.
        return new String[0];
    }

    @Override
    public long getLong(int docId) {
        return numericDocValues != null ? numericDocValues.get(docId) : DocValuesGetter.super.getLong(docId);
    }
}
