package nl.inl.blacklab.search.indexmetadata;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlTransient;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.index.IndexReader;

import com.fasterxml.jackson.annotation.JsonProperty;

import nl.inl.blacklab.index.annotated.AnnotationSensitivities;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.util.LuceneUtil;

/** Annotation on a field. */
@XmlAccessorType(XmlAccessType.FIELD)
public class AnnotationImpl implements Annotation, Freezable {
    
    /** The field this is an annotation for. */
    @XmlTransient
    private AnnotatedField field;
    
    /** The annotation name */
    @XmlTransient
    private String name;

    /** Our custom properties */
    private final CustomPropsMap custom = new CustomPropsMap();

    /**
     * Is this an internal annotation, not relevant for a search interface (e.g. punct, _relation)
     */
    private boolean isInternal;

    /** Reference to match sensitivities this annotation has */
    @XmlTransient
    private final Map<MatchSensitivity, AnnotationSensitivity> sensitivitiesMap = new LinkedHashMap<>();

    /** List of available sensitivities */
    private final List<MatchSensitivity> sensitivities = new ArrayList<>();

    /** Whether or not this annotation has a forward index. */
    @JsonProperty("hasForwardIndex")
    private boolean forwardIndex;

    /**
     * Which of the alternatives is the main one (containing the offset info, if
     * present)
     */
    @XmlTransient
    private AnnotationSensitivity offsetsSensitivity;

    /**
     * Which of the alternatives is the main one (containing the offset info, if
     * present)
     */
    @JsonProperty("offsetsSensitivity")
    private MatchSensitivity offsetsMatchSensitivity;

    @XmlTransient
    private FreezeStatus frozen = new FreezeStatus();
    
    /** Names of our subannotations, if we have any */
    private final Set<String> subannotations = new HashSet<>();
    
    /**
     * If this is a subannotation, what is its parent annotation?
     */
    @XmlTransient
    private Annotation mainAnnotation = null;

    // For JAXB deserialization
    @SuppressWarnings("unused")
    AnnotationImpl() {
        this(null, null);
    }

    AnnotationImpl(AnnotatedField field) {
        this(field, null);
    }

    public AnnotationImpl(AnnotatedField field, String name) {
        this.field = field;
        this.setName(name);
        forwardIndex = false;
    }
    
    @Override
    public AnnotatedField field() {
        return field;
    }
    
    @Override
    public String toString() {
        return luceneFieldPrefix();
    }

    @Override
    public boolean hasForwardIndex() {
        return forwardIndex;
    }

    /**
     * Get this annotation's name
     * 
     * @return the name
     */
    @Override
    public String name() {
        return name;
    }

    @Override
    public Collection<AnnotationSensitivity> sensitivities() {
        return Collections.unmodifiableCollection(sensitivitiesMap.values());
    }
    
    @Override
    public boolean hasSensitivity(MatchSensitivity sensitivity) {
        return sensitivitiesMap.containsKey(sensitivity);
    }

    @Override
    public AnnotationSensitivity sensitivity(MatchSensitivity sensitivity) {
        AnnotationSensitivity s = sensitivitiesMap.get(sensitivity);
        if (s == null)
            throw new UnsupportedOperationException("Specified sensitivity " + sensitivity + " not present for field " + luceneFieldPrefix());
        return s;
    }

    /**
     * Return which alternative contains character offset information.
     *
     * @return the alternative, or null if there is none.
     */
    @Override
    public AnnotationSensitivity offsetsSensitivity() {
        return offsetsSensitivity;
    }

    /**
     * @deprecated use {@link #custom()} with .get("displayName", name) instead
     */
    @Deprecated
    @Override
    public String displayName() {
        String displayName = custom.get("displayName", "");
        if (!displayName.isEmpty())
            return displayName;
        return StringUtils.capitalize(name);
    }

    @Override
    public boolean isInternal() {
        return isInternal;
    }

    /**
     * @deprecated use {@link #custom()} with .get("uiType", name) instead
     */
    @Deprecated
    @Override
    public String uiType() {
        return custom.get("uiType", "");
    }

    /**
     * @deprecated use {@link #custom()} with .get("description", name) instead
     */
    @Deprecated
    @Override
    public String description() {
        return custom.get("description", "");
    }
    
    // Methods that mutate data
    // -----------------------------------------------------

    /**
     * Detect which alternative is the one containing character offsets.
     *
     * Note that there may not be such an alternative.
     *
     * @param reader the index reader
     * @return true if found, false if not
     */
    public boolean detectOffsetsSensitivity(IndexReader reader) {
        ensureNotFrozen();
        // Iterate over the alternatives and for each alternative, find a term
        // vector. If that has character offsets stored, it's our main annotation.
        // If not, keep searching.
        for (AnnotationSensitivity sensitivity: sensitivitiesMap.values()) {
            if (LuceneUtil.hasOffsets(reader, sensitivity.luceneField())) {
                offsetsSensitivity = sensitivity;
                offsetsMatchSensitivity = sensitivity.sensitivity();
                return true;
            }
        }
    
        return false;
    }

    public void createSensitivities(AnnotationSensitivities sensitivitySetting) {
        if (sensitivitySetting == AnnotationSensitivities.DEFAULT)
            throw new IllegalArgumentException("Don't know what to do with DEFAULT sensitivity setting");
        if (sensitivitySetting == AnnotationSensitivities.CASE_AND_DIACRITICS_SEPARATE) {
            addAlternative(MatchSensitivity.CASE_INSENSITIVE);
            addAlternative(MatchSensitivity.DIACRITICS_INSENSITIVE);
        }
        if (sensitivitySetting != AnnotationSensitivities.ONLY_INSENSITIVE) {
            addAlternative(MatchSensitivity.SENSITIVE);
        }
        if (sensitivitySetting != AnnotationSensitivities.ONLY_SENSITIVE) {
            addAlternative(MatchSensitivity.INSENSITIVE);
        }
    }

    /**
     * @deprecated use {@link #custom()} with .put("displayName", ...) instead
     */
    @Deprecated
    public void setDisplayName(String displayName) {
        ensureNotFrozen();
        this.custom.put("displayName", displayName);
    }

    void addAlternative(MatchSensitivity matchSensitivity) {
        AnnotationSensitivity sensitivity = new AnnotationSensitivityImpl(this, matchSensitivity);
        if (!sensitivitiesMap.containsKey(matchSensitivity)) {
            ensureNotFrozen();
            sensitivitiesMap.put(matchSensitivity, sensitivity);
            sensitivities.add(matchSensitivity);
        }
    }

    public void setForwardIndex(boolean b) {
        if (forwardIndex != b) {
            ensureNotFrozen();
            forwardIndex = b;
        }
    }

    public void setName(String annotationName) {
        ensureNotFrozen();
        this.name = annotationName;
        this.isInternal |= nameImpliesInternal();
    }
    
    public void setInternal() {
        ensureNotFrozen();
        this.isInternal = true;
    }

    private boolean nameImpliesInternal() {
        return name != null && (isRelationAnnotation() ||
                name.equals(AnnotatedFieldNameUtil.PUNCTUATION_ANNOT_NAME));
    }

    /**
     * @deprecated use {@link #custom()} with .put("uiType", ...) instead
     */
    @Deprecated
    public void setUiType(String uiType) {
        ensureNotFrozen();
        custom.put("uiType", uiType);
    }


    /**
     * @deprecated use {@link #custom()} with .put("uiType", ...) instead
     */
    @Deprecated
    public void setDescription(String description) {
        ensureNotFrozen();
        custom.put("description", description);
    }
    
    @Override
    public boolean freeze() {
        return frozen.freeze();
    }
    
    @Override
    public boolean isFrozen() {
        return frozen.isFrozen();
    }

    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof AnnotationImpl that))
            return false;
        return field.equals(that.field) && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(field, name);
    }

    public void setOffsetsMatchSensitivity(MatchSensitivity offsetsAlternative) {
        AnnotationSensitivity newValue = sensitivity(offsetsAlternative);
        if (offsetsSensitivity == null || !offsetsSensitivity.equals(newValue)) {
            ensureNotFrozen();
            offsetsSensitivity = newValue;
            offsetsMatchSensitivity = offsetsAlternative;
        }
    }
    
    @Override
    public Set<String> subannotationNames() {
        return subannotations;
    }
    
    /**
     * Indicate that this is a subannotation.
     * 
     * @param parentAnnotation our parent annotation, e.g. "pos" for "pos_number"
     */
    @Override
    public void setSubAnnotation(Annotation parentAnnotation) {
        ensureNotFrozen();
        this.mainAnnotation = parentAnnotation;
    }

    public void setSubannotationNames(List<String> names) {
        subannotations.clear();
        subannotations.addAll(names);
    }

    public CustomPropsMap custom() {
        return custom;
    }

    public void fixAfterDeserialization(BlackLabIndex index, AnnotatedFieldImpl field, String annotationName) {
        this.field = field;
        this.name = annotationName;
        for (MatchSensitivity s: sensitivities) {
            sensitivitiesMap.put(s, new AnnotationSensitivityImpl(this, s));
        }
        offsetsSensitivity = sensitivitiesMap.get(offsetsMatchSensitivity);
        if (!index.indexMode())
            freeze();
    }

    public boolean isSubannotation() {
        return mainAnnotation != null;
    }

    public Annotation parentAnnotation() {
        return mainAnnotation;
    }
}
