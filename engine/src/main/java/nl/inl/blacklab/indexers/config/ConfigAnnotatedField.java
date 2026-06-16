package nl.inl.blacklab.indexers.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import nl.inl.blacklab.exceptions.InvalidInputFormatConfig;

/**
 * This class represents an AnnotatedField (i.e. a collection of Annotations - formerly "complex field")
 * as defined in a *.blf.yaml/*.blf.json file.
 * It is mainly used by the various {@link InputFormatTypeConfig} classes to extract data from input text/documents.
 */
public class ConfigAnnotatedField implements ConfigWithAnnotations {

    private static final Logger logger = LogManager.getLogger(ConfigAnnotatedField.class);

    @JsonIgnore
    private String name;

    /** How to display the field in the interface (optional) */
    private String displayName = "";

    /** How to describe the field in the interface (optional) */
    private String description = "";

    /** Where to find this field's annotated text */
    private String containerPath = ".";

    /** Words within body text */
    private String wordPath;

    /** Unique id that will map to this token position */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String tokenIdPath = null;

    /** Punctuation between words (or null if we don't need/want to capture this) */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String punctPath = null;

    /** Main annotation (i.e. the one containing the words from the text). Defaults to the first one defined. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String mainAnnotation = null;

    /** Default annotation to search on, if not the main annotation */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String defaultSearchAnnotation = null;

    /** All annotations on our words. */
    private final List<ConfigAnnotation> annotations = new ArrayList<>();

    /** Non-forEach annotations on our words, as a map. */
    @JsonIgnore
    private final Map<String, ConfigAnnotation> annotationsMap = new LinkedHashMap<>();

    /** All annotations on our words, with subannotations flattened. */
    @JsonIgnore
    private List<ConfigAnnotation> annotationsFlattened;

    public static ConfigAnnotation determineMainAnnotation(ConfigAnnotatedField configField) {
        ConfigAnnotation mainAnnotation;
        if (configField.getMainAnnotation() != null) {
            // Explicitly configured takes precedence
            mainAnnotation = configField.getAnnotation(configField.getMainAnnotation());
        } else {
            // Default: first non-forEach annotation
            mainAnnotation = configField.getAnnotations().stream()
                    .filter(a -> !a.isForEach())
                    .findFirst()
                    .orElseThrow(() -> new InvalidInputFormatConfig(
                            "Could not determine main annotation for field " + configField.getName() +
                                    " (no non-forEach fields found)"));
        }
        return mainAnnotation;
    }

    public List<ConfigAnnotation> getAnnotations() {
        return Collections.unmodifiableList(annotations);
    }

    public void setAnnotations(List<ConfigAnnotation> annotations) {
        this.annotationsMap.clear();
        for (ConfigAnnotation a: annotations) {
            if (!a.isForEach())
                this.annotationsMap.put(a.getName(), a);
        }
        this.annotations.clear();
        this.annotations.addAll(annotations);
    }

    public ConfigAnnotation getAnnotation(String name) {
        return annotationsMap.get(name);
    }

    @Override
    public synchronized List<ConfigAnnotation> getAnnotationsFlattened() {
        if (annotationsFlattened == null) {
            annotationsFlattened = new ArrayList<>();
            for (ConfigAnnotation annot: annotations) {
                annotationsFlattened.add(annot);
                annotationsFlattened.addAll(annot.getSubannotations());
            }
        }
        return Collections.unmodifiableList(annotationsFlattened);
    }

    /** Annotations on our words, defined elsewhere in the document */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private final List<ConfigStandoffAnnotations> standoffAnnotations = new ArrayList<>();

    /** Inline tags within body text */
    private List<ConfigInlineTag> inlineTags = new ArrayList<>();

    public List<ConfigInlineTag> getInlineTags() {
        return inlineTags;
    }

    public void setInlineTags(List<ConfigInlineTag> inlineTags) {
        this.inlineTags = inlineTags;
    }

    public void setSpans(List<ConfigInlineTag> inlineTags) {
        setInlineTags(inlineTags);
    }

    /** If true, this is a dummy annotated field that only exists to store linked documents, e.g. "metadata". */
    @JsonIgnore
    private boolean dummyForStoringLinkedDocument = false;

    ConfigAnnotatedField() {
        this("UNKNOWN");
    }

    ConfigAnnotatedField(String fieldName) {
        setName(fieldName);
    }

    void validate(InputFormatMessages messages) {
        String t = "annotated field";
        messages.mustHave(t, name, "name");
        if (dummyForStoringLinkedDocument)
            return; // dummy doesn't need anything other than a name
        messages.mustHave(t, containerPath, "containerPath");
        messages.mustHave(t, wordPath, "wordPath");
        for (ConfigAnnotation a: annotations)
            a.validate(messages, false);
        for (ConfigStandoffAnnotations s: standoffAnnotations)
            s.validate(messages);
        for (ConfigInlineTag tag: inlineTags)
            tag.validate(messages);
    }

    public ConfigAnnotatedField copy() {
        ConfigAnnotatedField result = new ConfigAnnotatedField(name);
        result.dummyForStoringLinkedDocument = dummyForStoringLinkedDocument;
        result.setDisplayName(displayName);
        result.setDescription(description);
        result.setContainerPath(containerPath);
        result.setWordPath(wordPath);
        result.setTokenIdPath(tokenIdPath);
        result.setPunctPath(punctPath);
        result.setDefaultSearchAnnotation(defaultSearchAnnotation);
        result.setMainAnnotation(mainAnnotation);
        for (ConfigAnnotation a: annotations)
            result.addAnnotation(a.copy());
        for (ConfigStandoffAnnotations a: standoffAnnotations)
            result.addStandoffAnnotation(a.copy());
        for (ConfigInlineTag t: inlineTags)
            result.addInlineTag(t.copy());
        return result;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setContainerPath(String containerPath) {
        this.containerPath = containerPath;
    }

    public void setWordPath(String wordPath) {
        this.wordPath = wordPath;
    }

    public void setTokenIdPath(String tokenIdPath) {
        this.tokenIdPath = tokenIdPath;
    }

    public void setPunctPath(String punctPath) {
        this.punctPath = punctPath;
    }

    public void setMainAnnotation(String mainAnnotation) {
        this.mainAnnotation = mainAnnotation;
    }

    public void setDefaultSearchAnnotation(String defaultSearchAnnotation) {
        this.defaultSearchAnnotation = defaultSearchAnnotation;
    }

    public void addInlineTag(ConfigInlineTag inlineTag) {
        this.inlineTags.add(inlineTag);
    }

    @Override
    public synchronized void addAnnotation(ConfigAnnotation annotation) {
        if (!annotation.isForEach())
            this.annotationsMap.put(annotation.getName(), annotation);
        this.annotations.add(annotation);
        annotationsFlattened = null;
    }

    public void addStandoffAnnotation(ConfigStandoffAnnotations standoff) {
        standoffAnnotations.add(standoff);
    }

    public String getName() {
        return name;
    }

    public String getContainerPath() {
        return containerPath;
    }

    public String getWordPath() {
        return wordPath;
    }

    public String getTokenIdPath() {
        return tokenIdPath;
    }

    public String getPunctPath() {
        return punctPath;
    }

    public String getMainAnnotation() {
        return mainAnnotation;
    }

    public String getDefaultSearchAnnotation() {
        return defaultSearchAnnotation;
    }

    public List<ConfigStandoffAnnotations> getStandoffAnnotations() {
        return Collections.unmodifiableList(standoffAnnotations);
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "ConfigAnnotatedField [name=" + name + "]";
    }

    public static ConfigAnnotatedField createDummyForStoringLinkedDocument(String name) {
        ConfigAnnotatedField f = new ConfigAnnotatedField(name);
        f.dummyForStoringLinkedDocument = true;
        return f;
    }

    @JsonIgnore
    public boolean isDummyForStoringLinkedDocuments() {
        return dummyForStoringLinkedDocument;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        ConfigAnnotatedField that = (ConfigAnnotatedField) o;
        return dummyForStoringLinkedDocument == that.dummyForStoringLinkedDocument && Objects.equals(name,
                that.name) && Objects.equals(displayName, that.displayName) && Objects.equals(
                description, that.description) && Objects.equals(containerPath, that.containerPath)
                && Objects.equals(wordPath, that.wordPath) && Objects.equals(tokenIdPath,
                that.tokenIdPath) && Objects.equals(punctPath, that.punctPath) &&
                Objects.equals(mainAnnotation, that.mainAnnotation) &&
                Objects.equals(defaultSearchAnnotation, that.defaultSearchAnnotation) &&
                Objects.equals(annotations,
                that.annotations) && Objects.equals(standoffAnnotations, that.standoffAnnotations)
                && Objects.equals(inlineTags, that.inlineTags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, displayName, description, containerPath, wordPath, tokenIdPath, punctPath,
                mainAnnotation, defaultSearchAnnotation, annotations, standoffAnnotations, inlineTags,
                dummyForStoringLinkedDocument);
    }

    public void setTokenPositionIdPath(String tokenPositionIdPath) {
        throw new InvalidInputFormatConfig("Encountered removed key 'tokenPositionIdPath' (rename to 'tokenIdPath')");
    }
}
