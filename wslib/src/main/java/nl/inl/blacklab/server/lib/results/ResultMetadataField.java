package nl.inl.blacklab.server.lib.results;

import java.util.Map;

import nl.inl.blacklab.search.indexmetadata.MetadataField;

public class ResultMetadataField {
    private final String indexName;
    private final MetadataField fieldDesc;
    private final boolean listValues;
    private final Map<String, Long> fieldValues;
    private final boolean valueListComplete;

    ResultMetadataField(String indexName, MetadataField fieldDesc, boolean listValues,
            Map<String, Long> fieldValues, boolean valueListComplete) {
        this.indexName = indexName;
        this.fieldDesc = fieldDesc;
        this.listValues = listValues;
        this.fieldValues = fieldValues;
        this.valueListComplete = valueListComplete;
    }

    /** Get index name
     *
     * @return index name
     * @deprecated used by APIv4, no longer needed for APIv5
     */
    @Deprecated(since = "5.0.0", forRemoval = true)
    public String getIndexName() {
        return indexName;
    }

    public MetadataField getFieldDesc() {
        return fieldDesc;
    }

    public boolean isListValues() {
        return listValues;
    }

    public Map<String, Long> getFieldValues() {
        return fieldValues;
    }

    public boolean isValueListComplete() {
        return valueListComplete;
    }
}
