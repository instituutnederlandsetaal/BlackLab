package nl.inl.blacklab.resultproperty;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

import nl.inl.blacklab.analysis.BuiltinAnalyzers;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.FieldType;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.results.DocResult;
import nl.inl.blacklab.util.PropertySerializeUtil;
import nl.inl.util.DocValuesUtil;
import nl.inl.util.LuceneUtil;
import nl.inl.util.NumericDocValuesCacher;
import nl.inl.util.SortedDocValuesCacher;
import nl.inl.util.SortedSetDocValuesCacher;
import nl.inl.util.StringUtil;

/**
 * For grouping DocResult objects by the value of a stored field in the Lucene
 * documents. The field name is given when instantiating this class, and might
 * be "author", "year", and such.
 *
 * This class is thread-safe.
 * (using synchronization on DocValues instance; DocValues are stored for each LeafReader,
 *  and each of those should only be used from one thread at a time)
 */
public class DocPropertyStoredField extends DocProperty {

    public static final String ID = "field";

    /** Lucene field name */
    private final String fieldName;

    /** Display name for the field */
    private final String friendlyName;

    /** The DocValues per segment (keyed by docBase), or null if we don't have docValues. New indexes all have SortedSetDocValues, but some very old indexes may still contain regular SortedDocValues! */
    private Map<Integer, Pair<SortedDocValuesCacher, SortedSetDocValuesCacher>> docValues = null;
    /** Null unless the field is numeric. */
    private Map<Integer, NumericDocValuesCacher> numericDocValues = null;

    /** Our index */
    private final BlackLabIndex index;

    public DocPropertyStoredField(DocPropertyStoredField prop, boolean invert) {
        super(prop, invert);
        this.index = prop.index;
        this.fieldName = prop.fieldName;
        this.friendlyName = prop.friendlyName;
    }

    @Override
    public Class<? extends PropertyValue> getValueType() {
        return PropertyValueString.class;
    }

    public DocPropertyStoredField(BlackLabIndex index, String fieldName) {
        this(index, fieldName, fieldName);
    }

    public DocPropertyStoredField(BlackLabIndex index, String fieldName, String friendlyName) {
        this.index = index;
        this.fieldName = fieldName;
        this.friendlyName = friendlyName;

        try {
            if (index.reader() != null) { // skip for MockIndex (testing)
                if (index.metadataField(fieldName).type().equals(FieldType.NUMERIC)) {
                    numericDocValues = new TreeMap<>();
                    for (LeafReaderContext rc : index.reader().leaves()) {
                        LeafReader r = rc.reader();
                        // NOTE: can be null! This is valid and indicates the documents in this segment does not contain any values for this field.
                        NumericDocValues values = r.getNumericDocValues(fieldName);
                        numericDocValues.put(rc.docBase, DocValuesUtil.cacher(values));
                    }
                } else { // regular string doc values.
                    docValues = new TreeMap<>();
                    for (LeafReaderContext rc : index.reader().leaves()) {
                        LeafReader r = rc.reader();
                        // NOTE: can be null! This is valid and indicates the documents in this segment does not contain any values for this field.
                        SortedSetDocValues sortedSetDocValues = r.getSortedSetDocValues(fieldName);
                        SortedDocValues sortedDocValues = r.getSortedDocValues(fieldName);
                        if (sortedSetDocValues != null || sortedDocValues != null) {
                            docValues.put(rc.docBase, Pair.of(DocValuesUtil.cacher(sortedDocValues), DocValuesUtil.cacher(sortedSetDocValues)));
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
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    /**
     * Get the raw values straight from lucene.
     * The returned array is in whichever order the values were originally added to the document.
     *
     */
    public String[] get(int docId) {
        if  (docValues != null) {
            // Find the value in the correct segment
            Entry<Integer, Pair<SortedDocValuesCacher, SortedSetDocValuesCacher>> target = null;
            for (Entry<Integer, Pair<SortedDocValuesCacher, SortedSetDocValuesCacher>> e : this.docValues.entrySet()) {
                if (e.getKey() > docId) { break; }
                target = e;
            }

            String[] ret = new String[0];
            if (target != null) {
                final int targetDocBase = target.getKey();
                final Pair<SortedDocValuesCacher, SortedSetDocValuesCacher> targetDocValues = target.getValue();
                if (targetDocValues != null) {
                    SortedDocValuesCacher a = targetDocValues.getLeft();
                    SortedSetDocValuesCacher b = targetDocValues.getRight();
                    if (a != null) { // old index, only one value
                        String value = a.get(docId - targetDocBase);
                        ret = value == null ? new String[0] : new String[] { value };
                    } else { // newer index, (possibly) multiple values.
                        ret = b.get(docId - targetDocBase);
                    }
                }
                // If no docvalues for this segment - no values were indexed for this field (in this segment).
                // So returning the empty array is good.
            }
            return ret;
        } else if (numericDocValues != null) {
            // Find the value in the correct segment
            Entry<Integer, NumericDocValuesCacher> target = null;
            for (Entry<Integer, NumericDocValuesCacher> e : this.numericDocValues.entrySet()) {
                if (e.getKey() > docId) { break; }
                target = e;
            }

            final List<String> ret = new ArrayList<>();
            if (target != null) {
                final Integer targetDocBase = target.getKey();
                final NumericDocValuesCacher targetDocValues = target.getValue();
                if (targetDocValues != null) {
                    ret.add(Long.toString(targetDocValues.get(docId - targetDocBase)));
                }
                // If no docvalues for this segment - no values were indexed for this field (in this segment).
                // So returning the empty array is good.
            }
            return ret.toArray(new String[0]);
        }

        // We don't have DocValues; just get the property from the document.
        return index.luceneDoc(docId).getValues(fieldName);
    }

    /**
     * Get the raw values straight from lucene.
     * The returned array is in whichever order the values were originally added to the document.
     *
     * @param doc a Lucene doc value that we can get metadata values from
     * @return metadata value(s)
     */
    public String[] get(PropertyValueDoc doc) {
        return get(doc.value());
    }

    /** Get the values as PropertyValue. */
    @Override
    public PropertyValueString get(DocResult result) {
        String[] values = get(result.identity());
        return fromArray(values);
    }

    /** Get the first value. The empty string is returned if there are no values for this document */
    public String getFirstValue(DocResult result) {
        return getFirstValue(result.identity());
    }

    /** Get the first value. The empty string is returned if there are no values for this document */
    public String getFirstValue(PropertyValueDoc doc) {
        return getFirstValue(doc.value());
    }

    /** Get the first value. The empty string is returned if there are no values for this document */
    public String getFirstValue(int docId) {
        String[] values = get(docId);
        return values.length > 0 ? values[0] : "";
    }

    /** Convert an array of string values to a PropertyValueString. */
    public static PropertyValueString fromArray(String[] values) {
        return new PropertyValueString(StringUtils.join(values, " · "));
    }

    /**
     * Compares two docs on this property
     *
     * @param docId1 first doc
     * @param docId2 second doc
     * @return 0 if equal, negative if a < b, positive if a > b.
     */
    public int compare(int docId1, int docId2) {
        return fromArray(get(docId1)).compareTo(fromArray(get(docId2))) * (reverse ? -1 : 1);
    }

    /**
     * Compares two docs on this property
     *
     * @param a first doc
     * @param b second doc
     * @return 0 if equal, negative if a < b, positive if a > b.
     */
    @Override
    public int compare(DocResult a, DocResult b) {
        PropertyValue v1 = get(a);
        PropertyValue v2 = get(b);
        return v1.compareTo(v2) * (reverse ? -1 : 1);
    }

    @Override
    public String name() {
        return friendlyName;
    }

    @Override
    public String serialize() {
        return serializeReverse() + PropertySerializeUtil.combineParts(ID, fieldName);
    }

    @Override
    public DocProperty reverse() {
        return new DocPropertyStoredField(this, true);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((fieldName == null) ? 0 : fieldName.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        DocPropertyStoredField other = (DocPropertyStoredField) obj;
        if (fieldName == null) {
            if (other.fieldName != null)
                return false;
        } else if (!fieldName.equals(other.fieldName))
            return false;
        return true;
    }

    @Override
    public Query query(BlackLabIndex index, PropertyValue value) {
        MetadataField metadataField = index.metadataField(fieldName);
        if (value.toString().isEmpty())
            return null; // Cannot search for empty string (to avoid this problem, configure ans "Unknown value")
        if (!value.toString().isEmpty() && metadataField.type() == FieldType.TOKENIZED) {
            String strValue = "\"" + value.toString().replaceAll("\"", "\\\\\"") + "\"";
            try {
                Analyzer analyzer = BuiltinAnalyzers.fromString(metadataField.analyzerName()).getAnalyzer();
                return LuceneUtil.parseLuceneQuery(index, strValue, analyzer, fieldName);
            } catch (ParseException e) {
                return null;
            }
        } else {
            return new TermQuery(new Term(fieldName, StringUtil.desensitize(value.toString())));
        }
        //return new TermQuery(new Term(fieldName, strValue));
    }

    @Override
    public boolean canConstructQuery(BlackLabIndex index, PropertyValue value) {
        return !value.toString().isEmpty();
    }

    public String getField() {
        return fieldName;
    }
}
