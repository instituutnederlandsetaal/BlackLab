package nl.inl.blacklab.search.indexmetadata;

import static nl.inl.blacklab.search.indexmetadata.MetadataFields.SPECIAL_FIELD_SETTING_PID;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import nl.inl.blacklab.indexers.config.ConfigMetadataField;

/**
 * The metadata fields in an index.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@JsonPropertyOrder({ "defaultAnalyzer", SPECIAL_FIELD_SETTING_PID, "throwOnMissingField", "fields" })
class MetadataFieldsImpl implements MetadataFieldsWriter, Freezable {

    private static final Logger logger = LogManager.getLogger(MetadataFieldsImpl.class);

    /** All non-annotated fields in our index (metadata fields) and their types. */
    @JsonProperty("fields")
    private final Map<String, MetadataFieldImpl> metadataFieldInfos;

    /** What MetadataFieldValues implementation to use (store in indexmetadata or get from index) */
    @XmlTransient
    private MetadataFieldValues.Factory metadataFieldValuesFactory;

    /**
     * When a metadata field value is considered "unknown"
     * (never|missing|empty|missing_or_empty) [never]
     */
    @XmlTransient
    private String defaultUnknownCondition = "never";

    /** What value to index when a metadata field value is unknown [unknown] */
    @XmlTransient
    private String defaultUnknownValue = "unknown";

    /** Top-level custom props (if using integrated input format), for special fields, etc. */
    @XmlTransient
    private CustomPropsMap topLevelCustom;

    /** Metadata field containing document pid */
    private String pidField;

    /** Default analyzer to use for metadata fields */
    private String defaultAnalyzer = "DEFAULT";

    /** Is the object frozen, not allowing any modifications? */
    @XmlTransient
    private final FreezeStatus frozen = new FreezeStatus();

    /** If we try to get() a missing field, should we throw or return a default config?
     *  Should eventually be eliminated when we can enforce all metadatafields to be declared.
     */
    private boolean throwOnMissingField = false;

    /** If throwOnMissingField is false, the implicit field configs are stored here.
     *  This map may be modified even if this instance is frozen.
     *  Should eventually be eliminated when we can enforce all metadatafields to be declared.
     */
    @XmlTransient
    private final Map<String, MetadataFieldImpl> implicitFields = new ConcurrentHashMap<>();

    @Override
    public MetadataFieldImpl addFromConfig(ConfigMetadataField f) {
        MetadataFieldImpl fieldDesc = MetadataFieldImpl.fromConfig(f, this);
        put(f.getName(), fieldDesc);
        return fieldDesc;
    }

    public MetadataFieldValues.Factory getMetadataFieldValuesFactory() {
        return metadataFieldValuesFactory;
    }

    // For JAXB deserialization
    @SuppressWarnings("unused")
    MetadataFieldsImpl() {
        metadataFieldInfos = new TreeMap<>();
    }

    MetadataFieldsImpl(MetadataFieldValues.Factory metadataFieldValuesFactory) {
        this.metadataFieldValuesFactory = metadataFieldValuesFactory;
        metadataFieldInfos = new TreeMap<>();
    }

    public void setThrowOnMissingField(boolean throwOnMissingField) {
        this.throwOnMissingField = throwOnMissingField;
    }

    @Override
    public String defaultAnalyzerName() {
        return defaultAnalyzer;
    }

    @Override
    public Iterator<MetadataField> iterator() {
        final Iterator<MetadataFieldImpl> it = metadataFieldInfos.values().iterator();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public MetadataField next() {
                return it.next();
            }
        };
    }

    @Override
    public Stream<MetadataField> stream() {
        return metadataFieldInfos.values().stream().map(f -> f);
    }

    @Override
    public MetadataField get(String fieldName) {
        MetadataField d;
        // Synchronized because we sometimes register new metadata fields during indexing
        synchronized (metadataFieldInfos) {
            d = metadataFieldInfos.get(fieldName);
        }
        if (d == null) {
            if (!throwOnMissingField) {
                // Don't throw an exception like we used to do; instead return a default tokenized field.
                // This allows us to handle the situation where not all metadata fields were registered
                // before indexing, and unregistered tokenized metadata fields were added during indexing.
                // (metadata can't change while indexing for integrated index)
                return registerImplicit(fieldName);
            } else {
                // Old behaviour: just throw an exception
                throw new IllegalArgumentException("Metadata field '" + fieldName + "' not found!");
            }
        }
        return d;
    }

    private MetadataFieldImpl registerImplicit(String fieldName) {
        return implicitFields.computeIfAbsent(fieldName,
                __ -> {
                    logger.warn("Encountered undeclared metadata field '" + fieldName + "'. Make sure all metadata fields are declared.");
                    return new MetadataFieldImpl(fieldName, FieldType.TOKENIZED, metadataFieldValuesFactory);
                }
        );
    }

    @Override
    public boolean exists(String name) {
        return metadataFieldInfos.containsKey(name);
    }

    @Override
    public Map<String, ? extends MetadataFieldGroup> groups() {
        Object value = topLevelCustom
                .get("metadataFieldGroups", new ArrayList<Map<String, Object>>());
        List<Map<String, Object>> metadataFieldGroups = value instanceof List ?
                (List<Map<String, Object>>) value : Collections.emptyList();
        Map<String, MetadataFieldGroupImpl> result = metadataFieldGroups.stream()
                .map( MetadataFieldGroupImpl::fromCustom)
                .collect(Collectors.toMap(MetadataFieldGroupImpl::name, Function.identity(),
                        (u, v) -> {
                            throw new IllegalStateException(String.format("Duplicate key %s", u));
                        },
                        LinkedHashMap::new));
        return result;
    }

    @Override
    public MetadataField special(String specialFieldType) {
        if (specialFieldType.equals("pid"))
            return pidField == null ? null : get(pidField);
        String field = topLevelCustom.get(specialFieldType + "Field", "");

        // TODO: if field is set but field doesn't exist, that seems like a bug...
        if (!field.isEmpty() && !metadataFieldInfos.containsKey(field)) {
            logger.warn(
                    "Special field " + specialFieldType + " is set to " + field + ", but that field doesn't exist.");
            return null;
        }

        return field.isEmpty() ? null : get(field);
    }

    @Override
    public MetadataField pidField() {
        return pidField == null ? null : get(pidField);
    }

    public String defaultUnknownCondition() {
        return defaultUnknownCondition;
    }

    public String defaultUnknownValue() {
        return defaultUnknownValue;
    }

    // Methods that mutate data
    // ------------------------------------

    @Override
    public synchronized MetadataFieldImpl register(String fieldName) {
        if (fieldName == null)
            throw new IllegalArgumentException("Tried to register a metadata field with null as name");
        // Synchronized because we might be using the map in another indexing thread
        synchronized (metadataFieldInfos) {
            MetadataFieldImpl mf;
            if (metadataFieldInfos.containsKey(fieldName))
                mf = metadataFieldInfos.get(fieldName);
            else {
                if (isFrozen() && !throwOnMissingField) {
                    // Metadata is frozen. Instead of really registering metadata
                    // field, we'll register an "implicit field". This is a metadata
                    // field whose configuration should match the default.
                    // With throwOnMissingField set to false, get() will also return a
                    // default config for missing fields.
                    return registerImplicit(fieldName);
                }
                // Not registered yet; do so now.
                ensureNotFrozen();
                FieldType fieldType = FieldType.TOKENIZED;
                if (fieldName.equals("fromInputFile")) {
                    // internal bookkeeping field, never tokenize this
                    // (probably better to register this field properly, but this works for now)
                    fieldType = FieldType.UNTOKENIZED;
                }
                mf = new MetadataFieldImpl(fieldName, fieldType, metadataFieldValuesFactory);
                mf.putCustom("unknownCondition", defaultUnknownCondition());
                mf.putCustom("unknownValue", defaultUnknownValue());
                metadataFieldInfos.put(fieldName, mf);
            }
            return mf;
        }
    }

    @Override
    public void setMetadataGroups(Map<String, MetadataFieldGroupImpl> metadataGroups) {
        if (!groups().equals(metadataGroups)) {
            ensureNotFrozen();
            List<Map<String, Object>> metaGroupsCustom = metadataGroups.values().stream()
                    .map(MetadataFieldGroupImpl::toCustom)
                    .toList();
            topLevelCustom.put("metadataFieldGroups", metaGroupsCustom);
        }
    }

    @Override
    public synchronized void put(String fieldName, MetadataFieldImpl fieldDesc) {
        ensureNotFrozen();
        metadataFieldInfos.put(fieldName, fieldDesc);
    }

    @Override
    public void resetForIndexing() {
        ensureNotFrozen();
        for (MetadataFieldImpl f: metadataFieldInfos.values()) {
            f.resetForIndexing();
        }
    }

    @Override
    public void setDefaultUnknownCondition(String unknownCondition) {
        ensureNotFrozen();
        this.defaultUnknownCondition = unknownCondition;
    }

    @Override
    public void setDefaultUnknownValue(String value) {
        ensureNotFrozen();
        this.defaultUnknownValue = value;
    }

    @Override
    public void clearSpecialFields() {
        ensureNotFrozen();
        pidField = null;
        topLevelCustom.put("titleField", null);
        topLevelCustom.put("authorField", null);
        topLevelCustom.put("dateField", null);
    }

    @Override
    public void setSpecialField(String specialFieldType, String fieldName) {
        ensureNotFrozen();
        if (specialFieldType.equals("pid")) // TODO: get rid of this special case?
            setPidField(fieldName);
        else
            topLevelCustom.put(specialFieldType + "Field", fieldName);
    }

    public void setPidField(String pidField) {
        this.pidField = pidField;
    }

    @Override
    public void setDefaultAnalyzer(String name) {
        ensureNotFrozen();
        this.defaultAnalyzer = name;
    }

    @Override
    public boolean freeze() {
        boolean b = frozen.freeze();
        if (b) {
            for (MetadataFieldImpl field: metadataFieldInfos.values()) {
                field.freeze();
            }
        }
        return b;
    }

    @Override
    public boolean isFrozen() {
        return frozen.isFrozen();
    }

    @Override
    public String toString() {
        return metadataFieldInfos.keySet().toString();
    }

    @Override
    public List<String> names() {
        return new ArrayList<>(metadataFieldInfos.keySet());
    }

    public void fixAfterDeserialization(IndexMetadataIntegrated metadata, MetadataFieldValues.Factory factory) {
        setTopLevelCustom(metadata.custom());

        metadataFieldValuesFactory = factory;
//        for (Map.Entry<String, MetadataFieldImpl> e: metadataFieldInfos.entrySet()) {
//            e.getValue().fixAfterDeserialization(metadata.index, e.getKey(), factory);
//        }

        // Find DocValues for all metadata fields in parallel
        metadataFieldInfos.entrySet().parallelStream().
                forEach(e -> e.getValue().fixAfterDeserialization(metadata.index, e.getKey(), factory));
    }

    public void setTopLevelCustom(CustomPropsMap topLevelCustom) {
        this.topLevelCustom = topLevelCustom;
    }

}
