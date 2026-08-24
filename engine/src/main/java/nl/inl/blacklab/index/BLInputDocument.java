package nl.inl.blacklab.index;

import java.nio.charset.StandardCharsets;

import org.apache.lucene.analysis.TokenStream;

/**
 * Generic interface for a BlackLab document being indexed.
 *
 * Either implemented using Lucene's Document class directly,
 * or through Solr's SolrInputDocument intermediary (which adds
 * schema validation, copyfields, etc.).
 */
public interface BLInputDocument {

    int MAX_DOCVALUES_LENGTH = Short.MAX_VALUE - 100; // really - 1, but let's be extra safe

    /** Prefix for special fields in fragment Lucene documents. */
    String FRAG_PREFIX = "_frag_";

    /** Field pointing to the document this is fragment of, referencing it by its pidField */
    String FRAG_FIELD_DOC = FRAG_PREFIX + "doc";

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
