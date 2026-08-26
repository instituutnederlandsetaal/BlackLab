package nl.inl.blacklab.index;

import java.nio.charset.StandardCharsets;

import org.apache.lucene.analysis.TokenStream;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Generic interface for a BlackLab document being indexed.
 *
 * Either implemented using Lucene's Document class directly,
 * or through Solr's SolrInputDocument intermediary (which adds
 * schema validation, copyfields, etc.).
 */
public interface BLInputDocument {

    int MAX_DOCVALUES_LENGTH = Short.MAX_VALUE - 100; // really - 1, but let's be extra safe

    /** Document type: document (regular full document), fragment (part of document, refers to pid of full doc),
     *  indexmetadata (special index metadata document) */
    String DOC_TYPE_FIELD_NAME = "_docType";

    /** Set the document type */
    void setType(DocType docType);

    /** The different document types in an index. */
    enum DocType {
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
    String FRAG_PREFIX = "_frag_";

    /** Field pointing to the document this is fragment of, referencing it by its pidField */
    String FRAG_FIELD_DOC = FRAG_PREFIX + "doc";

    /** Annotated field this fragment is from */
    String FRAG_FIELD_ANNOTATED_FIELD = FRAG_PREFIX + "annotatedField";

    /** Field pointing to the start of the fragment within the full document */
    String FRAG_FIELD_START = FRAG_PREFIX + "start";

    /** Field pointing to the end of the fragment within the full document */
    String FRAG_FIELD_END = FRAG_PREFIX + "end";

    void addField(String name, String value, BLFieldType fieldType);

    void addStoredField(String name, String value);

    void addAnnotationField(String name, TokenStream tokenStream, BLFieldType fieldType);

    void addStoredNumericField(String name, int value, boolean addDocValue);

    void addTextualMetadataField(String name, String value, BLFieldType type);

    String get(String name);

    static String truncateValue(String value) {
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

    BLIndexObjectFactory indexObjectFactory();

}
