package nl.inl.blacklab.resultproperty;

import java.io.IOException;

import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.FieldType;
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
                if (index.metadataField(fieldName).type().equals(FieldType.NUMERIC)) {
                    // NOTE: can be null! This is valid and indicates the documents in this segment does
                    // not contain any values for this field.
                    numericDocValues = DocValuesUtil.cacher(lrc.reader().getNumericDocValues(fieldName));
                } else {
                    // regular string doc values.
                    LeafReader r = lrc.reader();
                    // NOTE: can be null! This is valid and indicates the documents in this segment does not contain any values for this field.
                    sortedSetDocValues = DocValuesUtil.cacher(r.getSortedSetDocValues(fieldName));
                    sortedDocValues = DocValuesUtil.cacher(r.getSortedDocValues(fieldName));
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
        if (sortedDocValues != null) {
            // old index, only one value
            String value = sortedDocValues.get(docId);
            return value == null ? new String[0] : new String[] { value };
        } else if (sortedSetDocValues != null) {
            // newer index, (possibly) multiple values.
            return sortedSetDocValues.get(docId);
        } else if (numericDocValues != null) {
            return new String[] { Long.toString(numericDocValues.get(docId)) };
        } else {
            // If no docvalues for this segment - no values were indexed for this field (in this segment).
            // So returning the empty array is good.
            return new String[0];
        }
    }

    @Override
    public long getLong(int docId) {
        return numericDocValues != null ? numericDocValues.get(docId) : DocValuesGetter.super.getLong(docId);
    }
}
