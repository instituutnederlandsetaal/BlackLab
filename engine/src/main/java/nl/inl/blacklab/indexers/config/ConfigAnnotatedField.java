package nl.inl.blacklab.indexers.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

/**
 * This class represents an AnnotatedField (i.e. a collection of Annotations - formerly "complex field")
 * as defined in a *.blf.yaml/*.blf.json file.
 * It is mainly used by the various {@link DocIndexerConfig} classes to extract data from input text/documents.
 */
public class ConfigAnnotatedField implements ConfigWithAnnotations {

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
    private String tokenIdPath = null;

    /** Punctuation between words (or null if we don't need/want to capture this) */
    private String punctPath = null;

    /** Annotations on our words */
    private final Map<String, ConfigAnnotation> annotations = new LinkedHashMap<>();

    /** Annotations on our words, defined elsewhere in the document */
    private final List<ConfigStandoffAnnotations> standoffAnnotations = new ArrayList<>();

    /** Inline tags within body text */
    private final List<ConfigInlineTag> inlineTags = new ArrayList<>();

    private Map<String, ConfigAnnotation> annotationsFlattened;

    /** If true, this is a dummy annotated field that only exists to store linked documents, e.g. "metadata". */
    private boolean dummyForStoringLinkedDocument = false;

    ConfigAnnotatedField(String fieldName) {
        setName(fieldName);
    }

    public void validate() {
        String t = "annotated field";
        ConfigInputFormat.req(name, t, "name");
        if (dummyForStoringLinkedDocument)
            return; // dummy doesn't need anything other than a name
        ConfigInputFormat.req(containerPath, t, "containerPath");
        ConfigInputFormat.req(wordPath, t, "wordPath");
        for (ConfigAnnotation a : annotations.values())
            a.validate();
        for (ConfigStandoffAnnotations s : standoffAnnotations)
            s.validate();
        for (ConfigInlineTag tag : inlineTags)
            tag.validate();
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
        for (ConfigAnnotation a : annotations.values())
            result.addAnnotation(a.copy());
        for (ConfigStandoffAnnotations a : standoffAnnotations)
            result.addStandoffAnnotation(a.copy());
        for (ConfigInlineTag t : inlineTags)
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

    public void addInlineTag(ConfigInlineTag inlineTag) {
        this.inlineTags.add(inlineTag);
    }

    @Override
    public synchronized void addAnnotation(ConfigAnnotation annotation) {
        this.annotations.put(annotation.getName(), annotation);
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

    public String getWordsPath() {
        return wordPath;
    }

    public String getTokenIdPath() {
        return tokenIdPath;
    }

    public String getPunctPath() {
        return punctPath;
    }

    public List<ConfigInlineTag> getInlineTags() {
        return inlineTags;
    }

    @Override
    public Map<String, ConfigAnnotation> getAnnotations() {
        return Collections.unmodifiableMap(annotations);
    }

    @Override
    public synchronized Map<String, ConfigAnnotation> getAnnotationsFlattened() {
        if (annotationsFlattened == null) {
            annotationsFlattened = new LinkedHashMap<>();
            for (Entry<String, ConfigAnnotation> entry: annotations.entrySet()) {
                annotationsFlattened.put(entry.getKey(), entry.getValue());
                List<ConfigAnnotation> subannot = entry.getValue().getSubAnnotations();
                if (!subannot.isEmpty()) {
                    for (ConfigAnnotation a: subannot) {
                        annotationsFlattened.put(a.getName(), a);
                    }
                }
            }
        }
        return annotationsFlattened;
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

    public boolean isDummyForStoringLinkedDocuments() {
        return dummyForStoringLinkedDocument;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ConfigAnnotatedField that = (ConfigAnnotatedField) o;
        return dummyForStoringLinkedDocument == that.dummyForStoringLinkedDocument && Objects.equals(name,
                that.name) && Objects.equals(displayName, that.displayName) && Objects.equals(
                description, that.description) && Objects.equals(containerPath, that.containerPath)
                && Objects.equals(wordPath, that.wordPath) && Objects.equals(tokenIdPath,
                that.tokenIdPath) && Objects.equals(punctPath, that.punctPath) && Objects.equals(
                annotations, that.annotations) && Objects.equals(standoffAnnotations, that.standoffAnnotations)
                && Objects.equals(inlineTags, that.inlineTags) && Objects.equals(annotationsFlattened,
                that.annotationsFlattened);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, displayName, description, containerPath, wordPath, tokenIdPath, punctPath,
                annotations,
                standoffAnnotations, inlineTags, annotationsFlattened, dummyForStoringLinkedDocument);
    }
}
