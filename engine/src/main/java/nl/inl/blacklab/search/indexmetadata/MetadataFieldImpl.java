package nl.inl.blacklab.search.indexmetadata;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlTransient;
import nl.inl.blacklab.indexers.config.ConfigMetadataField;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.webservice.WebserviceParameter;

/**
 * A metadata field in an index.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class MetadataFieldImpl extends FieldImpl implements MetadataField {
    
//    private static final Logger logger = LogManager.getLogger(MetadataFieldImpl.class);

    public static MetadataFieldImpl fromConfig(ConfigMetadataField config,
            MetadataFieldsImpl metadataFields) {
        MetadataFieldImpl field = new MetadataFieldImpl(config.getName(), config.getType(),
                metadataFields.getMetadataFieldValuesFactory());

        // Custom properties
        field.putCustom("displayName", config.getDisplayName());
        field.putCustom("description", config.getDescription());
        field.putCustom("uiType", config.getUiType());
        field.putCustom("unknownCondition", (
                config.getUnknownCondition() == null ?
                        UnknownCondition.fromStringValue(metadataFields.defaultUnknownCondition()) :
                        config.getUnknownCondition()
                ).stringValue()
        );
        field.putCustom("unknownValue", config.getUnknownValue() == null ? metadataFields.defaultUnknownValue() :
                config.getUnknownValue());
        field.putCustom("displayValues", config.getDisplayValues());
        field.putCustom("displayOrder", config.getDisplayOrder());

        field.setAnalyzer(!StringUtils.isEmpty(config.getAnalyzer()) ? config.getAnalyzer() : metadataFields.defaultAnalyzerName());
        return field;
    }

    @XmlTransient
    private boolean keepTrackOfValues = true;

    /**
     * The field type: text, untokenized or numeric.
     */
    private FieldType type = FieldType.TOKENIZED;

    /**
     * The analyzer to use for indexing and querying this field.
     */
    private String analyzer = "DEFAULT";

    /**
     * Values for this field and their frequencies.
     */
    @XmlTransient
    private MetadataFieldValues values;

    @XmlTransient
    private MetadataFieldValues.Factory factory;

    // For JAXB deserialization
    @SuppressWarnings("unused")
    MetadataFieldImpl() {
    }

    MetadataFieldImpl(String fieldName, FieldType type, MetadataFieldValues.Factory factory) {
        super(fieldName);
        this.type = type;
        this.factory = factory;
        ensureValuesCreated();
    }

	/**
	 * Should we store the values for this field?
	 *
	 * We're moving away from storing values separately, because we can just
	 * use DocValues to find the values when we need them.
	 *
	 * @param keepTrackOfValues whether or not to store values here
	 */
    public void setKeepTrackOfValues(boolean keepTrackOfValues) {
        this.keepTrackOfValues = keepTrackOfValues;
    }

    @Override
    public FieldType type() {
        return type;
    }

    /**
     * @deprecated Use {@link #custom()} with .get("displayOrder", Collections.emptyList()) instead.
     */
    @Override
    @Deprecated
    public List<String> displayOrder() {
        return custom.get("displayOrder", Collections.emptyList());
    }

    @Override
    public String analyzerName() {
        return analyzer;
    }

    /**
     * @deprecated Use {@link #custom()} with .get("unknownValue", "unknown") instead.
     */
    @Override
    @Deprecated
    public String unknownValue() {
        return custom.get("unknownValue", "unknown");
    }

    /**
     * @deprecated Use {@link #custom()} with .get("unknownCondition", "never") instead.
     */
    @Override
    @Deprecated
    public UnknownCondition unknownCondition() {
        String strUnknownCondition = custom.get("unknownCondition", UnknownCondition.NEVER.stringValue());
        return UnknownCondition.fromStringValue(strUnknownCondition);
    }

    @Override
    public MetadataFieldValues values(long maxValues) {
        if (values == null || !values.canTruncateTo(maxValues))
            values = factory.create(name(), type, maxValues);
        return values.truncated(maxValues);
    }

    /**
     * @deprecated Use {@link #custom()} with .get("displayValues", Collections.emptyMap()) instead.
     */
    @Override
    @Deprecated
    public Map<String, String> displayValues() {
        return custom.get("displayValues", Collections.emptyMap());
    }

    /**
     * @deprecated Use {@link #custom()} with .get("uiType", "") instead.
     */
    @Override
    @Deprecated
    public String uiType() {
        return custom.get("uiType", "");
    }
    
    @Override
    public String offsetsField() {
        return name();
    }

    // Methods that mutate data
    // -------------------------------------------------
    


    
    public void setAnalyzer(String analyzer) {
        if (this.analyzer == null || !this.analyzer.equals(analyzer)) {
            ensureNotFrozen();
            this.analyzer = analyzer;
        }
    }

    /**
     * @deprecated Use {@link #custom()} with .put("unknownValue", ...) instead.
     */
    @Deprecated
    public void setUnknownValue(String unknownValue) {
        if (!this.unknownValue().equals(unknownValue)) {
            ensureNotFrozen();
            custom.put("unknownValue", unknownValue);
        }
    }

    /**
     * @deprecated Use {@link #custom()} with .put("unknownCondition", unknownCondition.stringValue()) instead.
     */
    @Deprecated
    public void setUnknownCondition(UnknownCondition unknownCondition) {
        if (!this.unknownCondition().equals(unknownCondition)) {
            ensureNotFrozen();
            this.custom.put("unknownCondition", unknownCondition.stringValue());
        }
    }

    /**
     * @deprecated Use {@link #custom()} with .put("displayValue", ...) instead.
     */
    @Deprecated
    @SuppressWarnings("DeprecatedIsStillUsed")
    void setDisplayValues(JsonNode displayValues) {
        ensureNotFrozen();
        Map<String, String> map = new HashMap<>();
        Iterator<Entry<String, JsonNode>> it = displayValues.fields();
        while (it.hasNext()) {
            Entry<String, JsonNode> entry = it.next();
            String value = entry.getKey();
            String displayValue = entry.getValue().textValue();
            map.put(value, displayValue);
        }
        custom.put("displayValues", map);
    }

    void setValueListComplete(boolean valueListComplete) {
        if (this.values.shouldAddValuesWhileIndexing()) {
            // We're reading values from indexmetadata.yaml (old external index)
            ensureNotFrozen();
            ensureValuesCreated();
            this.values.setComplete(valueListComplete);
        }
    }

    private void ensureValuesCreated() {
        if (this.values == null)
            this.values = factory.create(fieldName, type, WebserviceParameter.DEF_VAL_LIMIT_VALUES);
    }

    /**
     * Keep track of unique values of this field so we can store them in the
     * metadata file.
     *
     * @param value field value
     */
    public synchronized void addValue(String value) {
        if (!keepTrackOfValues)
            return;
        ensureNotFrozen();
        ensureValuesCreated();
        values.addValue(value);
    }

    /**
     * Remove a previously added value so we can keep track of unique 
     * values of this field correctly
     *
     * @param value field value to remove
     */
    public synchronized void removeValue(String value) {
        ensureNotFrozen();
        ensureValuesCreated();
        values.removeValue(value);
    }

    /**
     * Reset the information that is dependent on input data (i.e. list of values,
     * etc.) because we're going to (re-)index the data.
     */
    public void resetForIndexing() {
        ensureNotFrozen();
        ensureValuesCreated();
        values.reset();
    }

    public void fixAfterDeserialization(BlackLabIndex index, String fieldName, MetadataFieldValues.Factory factory) {
        super.fixAfterDeserialization(index, fieldName);
        this.factory = factory;
        assert values == null;
        ensureValuesCreated();
        setKeepTrackOfValues(false); // integrated uses DocValues for this
    }
}
