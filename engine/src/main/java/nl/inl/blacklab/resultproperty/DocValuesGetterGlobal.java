package nl.inl.blacklab.resultproperty;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedSetDocValues;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.FieldType;
import nl.inl.util.DocValuesUtil;
import nl.inl.util.NumericDocValuesCacher;
import nl.inl.util.SortedDocValuesCacher;
import nl.inl.util.SortedSetDocValuesCacher;

class DocValuesGetterGlobal implements DocValuesGetter {

    /**
     * The DocValues per segment (keyed by docBase), or null if we don't have docValues. New indexes all have SortedSetDocValues, but some very old indexes may still contain regular SortedDocValues!
     */
    private Map<Integer, Pair<SortedDocValuesCacher, SortedSetDocValuesCacher>> docValues = null;

    /**
     * Null unless the field is numeric.
     */
    private Map<Integer, NumericDocValuesCacher> numericDocValues = null;

    public DocValuesGetterGlobal(BlackLabIndex index, String fieldName) {
        try {
            if (index.reader() != null) { // skip for MockIndex (testing)
                if (index.metadataField(fieldName).type().equals(FieldType.NUMERIC)) {
                    numericDocValues = new TreeMap<>();
                    for (LeafReaderContext lrc: index.reader().leaves()) {
                        LeafReader r = lrc.reader();
                        // NOTE: can be null! This is valid and indicates the documents in this segment does not contain any values for this field.
                        NumericDocValues values = r.getNumericDocValues(fieldName);
                        numericDocValues.put(lrc.docBase, DocValuesUtil.cacher(values));
                    }
                    if (numericDocValues.isEmpty()) {
                        // We don't actually have DocValues.
                        numericDocValues = null;
                    }
                } else { // regular string doc values.
                    docValues = new TreeMap<>();
                    for (LeafReaderContext rc: index.reader().leaves()) {
                        LeafReader r = rc.reader();
                        // NOTE: can be null! This is valid and indicates the documents in this segment does not contain any values for this field.
                        SortedSetDocValues sortedSetDocValues = r.getSortedSetDocValues(fieldName);
                        SortedDocValues sortedDocValues = r.getSortedDocValues(fieldName);
                        if (sortedSetDocValues != null || sortedDocValues != null) {
                            docValues.put(rc.docBase, Pair.of(DocValuesUtil.cacher(sortedDocValues),
                                    DocValuesUtil.cacher(sortedSetDocValues)));
                        } else {
                            docValues.put(rc.docBase, null);
                        }
                    }
                    if (docValues.isEmpty()) {
                        // We don't actually have DocValues.
                        docValues = null;
                    }
                }
            }
        } catch (IOException e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    public boolean isValid() {
        return docValues != null || numericDocValues != null;
    }

    public String[] get(int docId) {
        if (docValues != null) {
            // Find the value in the correct segment
            Map.Entry<Integer, Pair<SortedDocValuesCacher, SortedSetDocValuesCacher>> target = null;
            for (Map.Entry<Integer, Pair<SortedDocValuesCacher, SortedSetDocValuesCacher>> e: this.docValues.entrySet()) {
                if (e.getKey() > docId) {
                    break;
                }
                target = e;
            }
            if (target != null) {
                final int targetDocBase = target.getKey();
                final Pair<SortedDocValuesCacher, SortedSetDocValuesCacher> targetDocValues = target.getValue();
                if (targetDocValues != null) {
                    SortedDocValuesCacher a = targetDocValues.getLeft();
                    SortedSetDocValuesCacher b = targetDocValues.getRight();
                    if (a != null) { // old index, only one value
                        String value = a.get(docId - targetDocBase);
                        return value == null ? new String[0] : new String[] { value };
                    } else { // newer index, (possibly) multiple values.
                        return b.get(docId - targetDocBase);
                    }
                }
                // If no docvalues for this segment - no values were indexed for this field (in this segment).
                // So returning the empty array is good.
            }
        } else if (numericDocValues != null) {
            return new String[] { getLong(docId) + "" };
        }
        return new String[0];
    }

    @Override
    public long getLong(int docId) {
        if (numericDocValues != null) {
            // Find the value in the correct segment
            Map.Entry<Integer, NumericDocValuesCacher> target = null;
            for (Map.Entry<Integer, NumericDocValuesCacher> e: this.numericDocValues.entrySet()) {
                if (e.getKey() > docId) {
                    break;
                }
                target = e;
            }

            if (target != null) {
                final Integer targetDocBase = target.getKey();
                final NumericDocValuesCacher targetDocValues = target.getValue();
                if (targetDocValues != null) {
                    return targetDocValues.get(docId - targetDocBase);
                }
                // If no docvalues for this segment - no values were indexed for this field (in this segment).
                // So returning the empty array is good.
            }
        }
        return DocValuesGetter.super.getLong(docId);
    }
}
