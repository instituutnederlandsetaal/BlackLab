package nl.inl.blacklab.resultproperty;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

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

class DocValuesGetterGlobal implements DocValuesGetter {

    private final BlackLabIndex index;

    /**
     * The DocValues per segment (keyed by docBase), or null if we don't have docValues. New indexes all have SortedSetDocValues, but some very old indexes may still contain regular SortedDocValues!
     */
    private Map<LeafReaderContext, SortedDocValuesCacher> sortedDocValues = null;

    /**
     * The DocValues per segment (keyed by docBase), or null if we don't have docValues. New indexes all have SortedSetDocValues, but some very old indexes may still contain regular SortedDocValues!
     */
    private Map<LeafReaderContext, SortedSetDocValuesCacher> sortedSetDocValues = null;

    /**
     * Null unless the field is numeric.
     */
    private Map<LeafReaderContext, NumericDocValuesCacher> numericDocValues = null;

    public DocValuesGetterGlobal(BlackLabIndex index, String fieldName) {
        this.index = index;
        try {
            if (index.reader() != null) { // skip for MockIndex (testing)
//                boolean isNumericMetadataField = index.metadataField(fieldName).type().equals(FieldType.NUMERIC);
                for (LeafReaderContext lrc: index.reader().leaves()) {
                    LeafReader leafReader = lrc.reader();
                    // NOTE: can be null! This is valid and indicates the documents in this segment does not contain any values for this field.
                    NumericDocValues numericDv = leafReader.getNumericDocValues(fieldName);
                    if (numericDv != null) {
                        if (numericDocValues == null)
                            numericDocValues = new HashMap<>();
                        numericDocValues.put(lrc, DocValuesUtil.withRandomAccess(numericDv));
                    } else {
                        SortedSetDocValues sortedSetDv = leafReader.getSortedSetDocValues(fieldName);
                        if (sortedSetDv != null) {
                            if (sortedSetDocValues == null)
                                sortedSetDocValues = new HashMap<>();
                            sortedSetDocValues.put(lrc, DocValuesUtil.withRandomAccess(sortedSetDv));
                        } else {
                            SortedDocValues sortedDv = leafReader.getSortedDocValues(fieldName);
                            if (sortedDv != null) {
                                if (sortedDocValues == null)
                                    sortedDocValues = new HashMap<>();
                                sortedDocValues.put(lrc, DocValuesUtil.withRandomAccess(sortedDv));
                            }
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
        LeafReaderContext lrc = index.getLeafReaderContext(docId);
        int segmentDocId = docId - lrc.docBase;
        if (sortedSetDocValues != null) {
            // newer index, (possibly) multiple values.
            return sortedSetDocValues.get(lrc).get(segmentDocId);
        } else if (numericDocValues != null) {
            // numeric field
            long value = numericDocValues.get(lrc).get(segmentDocId);
            return new String[] { value + "" };
        } else if (sortedDocValues != null) {
            // old index, only one value
            String value = sortedDocValues.get(lrc).get(segmentDocId);
            if (value != null)
                return new String[] { value };
        }
        return new String[0];
    }

    @Override
    public long getLong(int docId) {
        if (numericDocValues != null) {
            LeafReaderContext lrc = index.getLeafReaderContext(docId);
            int segmentDocId = docId - lrc.docBase;
            return numericDocValues.get(lrc).get(segmentDocId);
        }
        return DocValuesGetter.super.getLong(docId);
    }
}
