package nl.inl.blacklab.search.indexmetadata;

import org.apache.commons.lang3.StringUtils;

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

    @Override
    public String analyzerName() {
        return analyzer;
    }

    @Override
    public MetadataFieldValues values(long maxValues) {
        if (values == null || !values.canTruncateTo(maxValues))
            values = factory.create(name(), type, maxValues);
        return values.truncated(maxValues);
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

    public void fixAfterDeserialization(BlackLabIndex index, String fieldName, MetadataFieldValues.Factory factory) {
        super.fixAfterDeserialization(index, fieldName);
        this.factory = factory;
        assert values == null;
        ensureValuesCreated();
        setKeepTrackOfValues(false); // integrated uses DocValues for this
    }
}
