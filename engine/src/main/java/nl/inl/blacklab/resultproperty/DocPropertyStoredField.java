package nl.inl.blacklab.resultproperty;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

import nl.inl.blacklab.analysis.BuiltinAnalyzers;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.FieldType;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.results.docs.DocResult;
import nl.inl.blacklab.util.PropertySerializeUtil;
import nl.inl.util.LuceneUtil;
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

    private DocValuesGetter docValuesGetter;

    /** Our index */
    private final BlackLabIndex index;

    public DocPropertyStoredField(DocPropertyStoredField prop, PropContext context, boolean invert) {
        super(prop, context, invert);
        this.index = prop.index;
        this.fieldName = prop.fieldName;
        this.friendlyName = prop.friendlyName;
        this.docValuesGetter = (this.context.lrc() == prop.context.lrc()) ? prop.docValuesGetter :
                DocValuesGetter.get(index, this.context.lrc(), fieldName);
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
        docValuesGetter = DocValuesGetter.get(index, null, fieldName);
    }

    /**
     * Get the raw values straight from lucene.
     * The returned array is in whichever order the values were originally added to the document.
     *
     */
    public String[] get(int docId) {
        return docValuesGetter.get(docId);
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
        return PropertyValueString.fromArray(values, context.collationCache());
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

    /**
     * Compares two docs on this property
     *
     * @param docId1 first doc
     * @param docId2 second doc
     * @return 0 if equal, negative if a < b, positive if a > b.
     */
    public int compare(int docId1, int docId2) {
        PropertyValueString a = PropertyValueString.fromArray(get(docId1), context.collationCache());
        PropertyValueString b = PropertyValueString.fromArray(get(docId2), context.collationCache());
        return a.compareTo(b) * (reverse ? -1 : 1);
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
    public DocPropertyStoredField copyWith(PropContext context, boolean invert) {
        return new DocPropertyStoredField(this, context, invert);
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
    }

    @Override
    public boolean canConstructQuery(BlackLabIndex index, PropertyValue value) {
        return !value.toString().isEmpty();
    }

    public String getField() {
        return fieldName;
    }
}
