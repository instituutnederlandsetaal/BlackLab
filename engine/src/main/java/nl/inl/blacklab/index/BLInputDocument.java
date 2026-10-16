package nl.inl.blacklab.index;

import java.nio.charset.StandardCharsets;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.IndexableFieldType;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;
import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * A BlackLab document being indexed.
 *
 * Ensure each document is stored with a _doc_type field, so we can distinguish between full documents,
 * fragments, and index metadata documents.
 * Also takes care of some other conventions for BlackLab documents, such as storing docvalues for certain fields.
 */
public class BLInputDocument {

    private static final int MAX_DOCVALUES_LENGTH = Short.MAX_VALUE - 100; // really - 1, but let's be extra safe

    /** Document type: document (regular full document), fragment (part of document, refers to pid of full doc),
     *  indexmetadata (special index metadata document) */
    public static final String DOC_TYPE_FIELD_NAME = "_doc_type";

    protected final Document document;
    
    /** The document type (document/fragment/indexmetadata)
     *
     * Needed because _doc_type is not a stored field, so we can't retrieve it directly from the document
     */
    protected DocType docType;

    public BLInputDocument(DocType type) {
        document = new Document();
        this.docType = type;
        String docTypeValue = type.getValue();
        addIndexedAndDocValues(DOC_TYPE_FIELD_NAME, docTypeValue);
    }

    public static @NonNull Query docTypeQuery(DocType type) {
        return new TermQuery(new Term(DOC_TYPE_FIELD_NAME, type.value));
    }

    /** The different document types in an index. */
    public enum DocType {
        DOCUMENT("document"),
        FRAGMENT("fragment"),
        INDEXMETADATA("indexmetadata");

        private final String value;

        DocType(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }

        @JsonCreator
        public static DocType forValue(String value) {
            for (DocType type : DocType.values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown LuceneDocumentTypes value: " + value);
        }
    }

    /** Prefix for special fields in fragment Lucene documents. */
    public static final String FRAG_PREFIX = "_frag_";

    /** Annotated field this fragment is from */
    public static final String FRAG_FIELD_ANNOTATED_FIELD = FRAG_PREFIX + "annotatedField";

    /** Field pointing to the start of the fragment within the full document */
    public static final String FRAG_FIELD_START = FRAG_PREFIX + "start";

    /** Field pointing to the end of the fragment within the full document */
    public static final String FRAG_FIELD_END = FRAG_PREFIX + "end";

    public void addField(String name, String value, IndexableFieldType fieldType) {
        document.add(new Field(name, value, fieldType));
    }

    public void addStoredField(String name, String value) {
        document.add(new StoredField(name, value));
    }

    public void addAnnotationField(String name, TokenStream tokenStream, IndexableFieldType fieldType) {
        document.add(new Field(name, tokenStream, fieldType));
    }

    public void addStoredNumericField(String name, int value, boolean addDocValue) {
        addNumericField(name, value, true, true, addDocValue);
    }

    public void addNumericField(String name, int value, boolean index, boolean store, boolean docValue) {
        if (index)
            document.add(new IntPoint(name, value));
        if (store)
            document.add(new StoredField(name, value));
        if (docValue)
            document.add(new NumericDocValuesField(name, value));
    }

    public void addIndexedAndDocValues(String name, String value) {
        document.add(new Field(name, value, BLFieldType.STRING_UNTOKENIZED_UNSTORED));
        document.add(new SortedDocValuesField(name, new BytesRef(value.getBytes())));
    }

    public void addTextualMetadataField(String name, String value, IndexableFieldType type) {
        addField(name, value, type);

        // If a value is too long (more than 32K), just truncate it a bit.
        // This should be very rare and would generally only affect sorting/grouping, if anything.
        value = BLInputDocument.truncateValue(value);
        // docvalues for efficient sorting/grouping
        document.add(new SortedSetDocValuesField(name, new BytesRef(value)));
    }

    public Document getDocument() {
        return document;
    }

    public DocType getDocType() {
        return docType;
    }

    public String get(String name) {
        return document.get(name);
    }

    public static String truncateValue(String value) {
        // If a value is too long (more than 32K), just truncate it a bit.
        // This should be very rare and would generally only affect sorting/grouping, if anything.
        if (value.length() > MAX_DOCVALUES_LENGTH / 6) { // only when it might be too large...
            // While it's really too large
            byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
            while (utf8.length > MAX_DOCVALUES_LENGTH) {
                // assume all characters take two bytes, truncate and try again
                int overshoot = utf8.length - MAX_DOCVALUES_LENGTH;
                int truncateAt = value.length() - 2 * overshoot;
                if (truncateAt < 1)
                    truncateAt = 1;
                value = value.substring(0, truncateAt);
                utf8 = value.getBytes(StandardCharsets.UTF_8);
            }
        }
        return value;
    }

}
