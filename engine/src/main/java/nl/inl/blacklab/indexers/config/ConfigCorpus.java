package nl.inl.blacklab.indexers.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Settings that will be used to write the indexmetadata file for any corpus we
 * create from this format.
 *
 * Stuff used by BLS and user interfaces.
 *
 * None of these settings have any impact on indexing. All fields are optional.
 */
public class ConfigCorpus {

    /** Corpus display name */
    private String displayName = "";

    /** Corpus description */
    private String description = "";

    /** May end user fetch contents of whole documents? [false] */
    private boolean contentViewable = false;

    /** What is the text direction of the script used? (e.g. LTR / RTL) */
    private TextDirection textDirection = TextDirection.LEFT_TO_RIGHT;

    /** Special field roles, such as pidField, titleField, etc. */
    final Map<String, String> specialFields = new LinkedHashMap<>();

    /** How to group metadata fields */
    final List<ConfigMetadataFieldGroup> metadataFieldGroups = new ArrayList<>();

    public void setMetadataFieldGroups(List<ConfigMetadataFieldGroup> groups) {
        this.metadataFieldGroups.clear();
        metadataFieldGroups.addAll(groups);
    }

    public List<ConfigMetadataFieldGroup> getMetadataFieldGroups() {
        return Collections.unmodifiableList(metadataFieldGroups);
    }

    void addMetadataFieldGroup(ConfigMetadataFieldGroup g) {
        metadataFieldGroups.add(g);
    }

    /** How to group annotated fields' annotations */
    final Map<String, List<ConfigAnnotationGroup>> annotationGroups = new LinkedHashMap<>();

    public void setAnnotationGroups(Map<String, List<ConfigAnnotationGroup>> groups) {
        this.annotationGroups.clear();
        annotationGroups.putAll(groups);
    }

    public Map<String, List<ConfigAnnotationGroup>> getAnnotationGroups() {
        return Collections.unmodifiableMap(annotationGroups);
    }

    public void addAnnotationGroups(String name, List<ConfigAnnotationGroup> groups) {
        annotationGroups.put(name, groups);
    }

    public Map<String, String> getSpecialFields() {
        return Collections.unmodifiableMap(specialFields);
    }

    public boolean isContentViewable() {
        return contentViewable;
    }

    public void setContentViewable(boolean contentViewable) {
        this.contentViewable = contentViewable;
    }

    public TextDirection getTextDirection() {
        return this.textDirection;
    }

    public void setTextDirection(TextDirection textDirection) {
        this.textDirection = textDirection;
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
        return "ConfigCorpus [displayName=" + displayName + "]";
    }

}
