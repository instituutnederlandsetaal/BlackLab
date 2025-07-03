package nl.inl.blacklab.search.indexmetadata;

import java.util.Iterator;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * List of values with freqencies of a metadata field.
 *
 * This is the version that is stored in the index metadata file.
 */
class MetadataFieldValuesInMetadataFile implements MetadataFieldValues {

    static class Factory implements MetadataFieldValues.Factory {
        @Override
        public MetadataFieldValues create(String fieldName, FieldType fieldType, long limitValues) {
            // ignore limitValues here, we load whatever was stored in the file.
            return new MetadataFieldValuesInMetadataFile(fieldName, IndexMetadataExternal.maxMetadataValuesToStore());
        }
    }

    private static final Logger logger = LogManager.getLogger(MetadataFieldValuesInMetadataFile.class);

    private static final int MAX_VALUE_STORE_LENGTH = 256;

    /**
     * The values this field can have. Note that this may not be the complete list;
     * check valueListComplete.
     */
    private TruncatableFreqList values;

    private final int maxValues;

    /**
     * Did we encounter a value that was too long to store and warn the user about it?
     */
    private boolean warnedAboutValueLength = false;

    /**
     * Field name for use in warning message
     */
    private final String fieldName;

    private MetadataFieldValuesInMetadataFile(String fieldName, int maxValues) {
        this.fieldName = fieldName;
        this.maxValues = maxValues;
        values = new TruncatableFreqList(maxValues);
    }

    @Override
    public boolean canTruncateTo(long maxValues) {
        return true;
    }

    @Override
    public boolean shouldAddValuesWhileIndexing() { return false; }

    @Override
    public MetadataFieldValues truncated(long maxValues) {
        // Ignore this for the legacy external index format
        return this;
    }

    @Override
    public TruncatableFreqList valueList() {
        return values;
    }

    @Override
    public void setValues(JsonNode values) {
        this.values = new TruncatableFreqList(maxValues);
        Iterator<Map.Entry<String, JsonNode>> it = values.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            String value = entry.getKey();
            int count = entry.getValue().asInt();
            this.values.add(value, count);
        }
    }

    @Override
    public void setComplete(boolean complete) {
        this.values.setTruncated(!complete);
    }

    @Override
    public void addValue(String value) {
        // If we've seen a value, assume we'll get to see all values;
        // when it turns out there's too many or they're too long,
        // we'll change the value to NO.
        if (value.length() > MAX_VALUE_STORE_LENGTH) {
            // Value too long to store.
            values.setTruncated(true);
            if (!warnedAboutValueLength) {
                warnedAboutValueLength = true;
                logger.warn(
                        "Metadata field " + fieldName + " includes a value too long to store in indexmetadata.yaml ("
                                + value.length() + " > " + MAX_VALUE_STORE_LENGTH
                                + "). Will not store this value and will set valueListComplete to false. The value will still be indexed/stored in Lucene as normal. This warning only appears once.");
            }
            return;
        }
        // New value; add it
        values.add(value);
    }

    @Override
    public void removeValue(String value) {
        values.subtract(value, 1);
    }

    @Override
    public void reset() {
        this.values = new TruncatableFreqList(maxValues);
    }
}
