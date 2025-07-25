package nl.inl.blacklab.search.indexmetadata;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlTransient;
import nl.inl.blacklab.indexers.config.ConfigAnnotatedField;
import nl.inl.blacklab.indexers.config.ConfigAnnotation;
import nl.inl.blacklab.indexers.config.ConfigStandoffAnnotations;
import nl.inl.blacklab.search.BlackLabIndex;

@XmlAccessorType(XmlAccessType.FIELD)
public final class AnnotatedFieldsImpl implements AnnotatedFields, Freezable {

    /** Our index */
    @XmlTransient
    private BlackLabIndex index;

    /** The annotated fields in our index */
    @JsonProperty("fields")
    private final Map<String, AnnotatedFieldImpl> annotatedFields;
    
    /**
     * The main contents field in our index. This is the first annotated field found.
     */
    @XmlTransient
    private AnnotatedFieldImpl mainAnnotatedField;

    /**
     * The main contents field in our index. This is the first annotated field found.
     */
    @JsonProperty("mainAnnotatedField")
    private String mainAnnotatedFieldName;

    @XmlTransient
    private CustomPropsMap topLevelCustom;

    /** Frozen or not? */
    @XmlTransient
    FreezeStatus freezeStatus = new FreezeStatus();

    public AnnotatedFieldsImpl() {
        this(null);
    }

    public AnnotatedFieldsImpl(BlackLabIndex index) {
        this.index = index;
        annotatedFields = new TreeMap<>();
    }

    @Override
    public AnnotatedField main() {
        return mainAnnotatedField;
    }

    @Override
    public Iterator<AnnotatedField> iterator() {
        Iterator<AnnotatedFieldImpl> it = annotatedFields.values().iterator();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public AnnotatedField next() {
                return it.next();
            }
        };
    }

    @Override
    public Stream<AnnotatedField> stream() {
        return annotatedFields.values().stream().map(f -> f);
    }

    @Override
    public AnnotatedFieldImpl get(String fieldName) {
        if (!annotatedFields.containsKey(fieldName))
            return null;
        return annotatedFields.get(fieldName);
    }

    @Override
    public boolean exists(String fieldName) {
        return annotatedFields.containsKey(fieldName);
    }

    @Override
    public boolean freeze() {
        boolean b = freezeStatus.freeze();
        if (b)
            annotatedFields.values().forEach(AnnotatedFieldImpl::freeze);
        return b;
    }

    @Override
    public boolean isFrozen() {
        return freezeStatus.isFrozen();
    }

    public void put(String fieldName, AnnotatedFieldImpl fieldDesc) {
        annotatedFields.put(fieldName, fieldDesc);
        if (mainAnnotatedField == null && mainAnnotatedFieldName == null) {
            mainAnnotatedFieldName = fieldName;
            mainAnnotatedField = fieldDesc;
        }
    }

    public void setMainAnnotatedField(AnnotatedFieldImpl mainAnnotatedField) {
        this.mainAnnotatedField = mainAnnotatedField;
        this.mainAnnotatedFieldName = mainAnnotatedField == null ? null : mainAnnotatedField.name();
    }

    private Map<String, AnnotationGroups> annotationGroupsPerField() {
        Map<String, List<Map<String, Object>>> serialized = (Map<String, List<Map<String, Object>>>)topLevelCustom.get("annotationGroups");
        Map<String, AnnotationGroups> map = new HashMap<>();
        if (serialized != null) {
            for (Map.Entry<String, List<Map<String, Object>>> e: serialized.entrySet()) {
                String fieldName = e.getKey();
                AnnotationGroups groups = AnnotationGroups.fromCustom(fieldName, e.getValue());
                map.put(fieldName, groups);
            }
        }
        return map;
    }

    public void clearAnnotationGroups() {
        topLevelCustom.put("annotationGroups", new LinkedHashMap<>());
    }

    public void putAnnotationGroups(String fieldName, AnnotationGroups annotationGroups) {
        Map<String, List<Map<String, Object>>> groups = topLevelCustom.computeIfAbsent("annotationGroups",
                __ -> new LinkedHashMap<>());
        groups.put(fieldName, annotationGroups.toCustom());
    }
    
    @Override
    public AnnotationGroups annotationGroups(String fieldName) {
        return annotationGroupsPerField().get(fieldName);
    }

    public void fixAfterDeserialization(BlackLabIndex index, IndexMetadataIntegrated metadata) {
        this.index = index;
        setTopLevelCustom(metadata.custom());

        for (Map.Entry<String, AnnotatedFieldImpl> e: annotatedFields.entrySet()) {
            e.getValue().fixAfterDeserialization(index, e.getKey());
        }
        setMainAnnotatedField(annotatedFields.get(mainAnnotatedFieldName));
    }

    void setTopLevelCustom(CustomPropsMap custom) {
        this.topLevelCustom = custom;
    }

    @Override
    public void addFromConfig(ConfigAnnotatedField f) {
        AnnotatedFieldImpl annotatedField = new AnnotatedFieldImpl(index, f.getName());
        annotatedField.putCustom("displayName", f.getDisplayName());
        annotatedField.putCustom("description", f.getDescription());
        List<String> displayOrder = new ArrayList<>();

        if (!f.getAnnotations().isEmpty())
            annotatedField.setMainAnnotationName(f.getAnnotations().values().iterator().next().getName());
        boolean isFirstAnnotation = true;
        boolean hasOffsets;
        for (ConfigAnnotation configAnnot: f.getAnnotations().values()) {
            hasOffsets = isFirstAnnotation; // first annotation gets offsets
            addAnnotationInfo(annotatedField, configAnnot, hasOffsets, displayOrder);
            isFirstAnnotation = false;
            for (ConfigAnnotation subAnnot: configAnnot.getSubAnnotations()) {
                addAnnotationInfo(annotatedField, subAnnot, false, displayOrder);
            }
        }
        for (ConfigStandoffAnnotations standoff: f.getStandoffAnnotations()) {
            for (ConfigAnnotation configAnnot: standoff.getAnnotations().values()) {
                hasOffsets = isFirstAnnotation; // first annotation gets offsets
                addAnnotationInfo(annotatedField, configAnnot, hasOffsets, displayOrder);
                isFirstAnnotation = false;
            }
        }
        annotatedField.putCustom("displayOrder", displayOrder);
        put(annotatedField.name(), annotatedField);
    }

    public void addAnnotationInfo(AnnotatedFieldImpl f, ConfigAnnotation config, boolean hasOffsets, List<String> displayOrder) {
        AnnotationImpl annotation = new AnnotationImpl(f, config.getName());
        CustomPropsMap custom = annotation.custom();
        custom.put("displayName", config.getDisplayName());
        custom.put("description", config.getDescription());
        custom.put("uiType", config.getUiType());

        annotation.createSensitivities(config.getSensitivitySetting());
        if (hasOffsets)
            annotation.setOffsetsMatchSensitivity(annotation.mainSensitivity().sensitivity());

        f.putAnnotation(annotation);
        displayOrder.add(annotation.name());
    }
}
