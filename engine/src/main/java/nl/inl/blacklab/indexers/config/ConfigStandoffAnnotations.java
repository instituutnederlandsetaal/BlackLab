package nl.inl.blacklab.indexers.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import nl.inl.blacklab.exceptions.InvalidInputFormatConfig;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.RelationUtil;
import nl.inl.util.XPathUtil;

/**
 * Configuration for a block of standoff annotations (annotations that don't
 * reside under the word tag but elsewhere in the document).
 */
public class ConfigStandoffAnnotations implements ConfigWithAnnotations {

    /**
     * The type of standoff annotation (e.g. "token" (default), "span" or "relation")
     */
    private AnnotationType type = AnnotationType.TOKEN;

    /**
     * Path to the elements containing the values to index (values may apply to
     * multiple token positions)
     */
    private String path;

    /**
     * Unique id of the token position(s) to index these values at. A uniqueId must
     * be defined for words.
     * If this is a span (that is, spanEndPath is not empty), this refers to the start of
     * the span.
     */
    private String tokenRefPath;

    /**
     * How to find the end of the span or target of the relation. Empty for token annotations.
     */
    private String spanEndPath = "";

    /**
     * If this is a span, does spanEndPath refer to the last token inside the span (inclusive)
     * or the first token outside the span (exclusive)?
     */
    private boolean spanEndIsInclusive = true;

    /**
     * XPath needed to find the name of the span or type of relation, if this is one (i.e. type is not "token").
     * E.g. for a sentence this will usually resolve to "s".
     */
    private String valuePath;

    /** Metadata (for type=FRAGMENT only!) */
    @JsonDeserialize(using = ConfigInputFormat.MetadataDeserializer.class)
    @JsonPropertyDescription("Block(s) that configure how to index metadata fields.")
    private final List<ConfigMetadataBlock> metadata = new ArrayList<>();

    public List<ConfigMetadataBlock> getMetadata() {
        return metadata;
    }

    /** The annotations to index at the referenced token positions. */
    private final List<ConfigAnnotation> annotations = new ArrayList<>();

    public void setAnnotations(List<ConfigAnnotation> annotations) {
        this.annotations.clear();
        for (ConfigAnnotation a : annotations) {
            addAnnotation(a);
        }
    }

    public List<ConfigAnnotation> getAnnotations() {
        return Collections.unmodifiableList(annotations);
    }

    @Override
    @JsonIgnore
    public List<ConfigAnnotation> getAnnotationsFlattened() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addAnnotation(ConfigAnnotation annotation) {
        this.annotations.add(annotation);
    }

    /** For relations: the relation class to index this as. If not specified, "rel" is used
     */
    private String relationClass = null;

    /** For relations: target field for the relation. Defaults to empty, meaning 'this field'.
     *
     * NOTE: targetField and targetVersion are combined into a single field in the index.
     * For example, if targetField is empty and targetVersion is "de", and this field is "contents__nl",
     * the target field for the relations will be the field will be "contents__de".
     */
    private String targetField = "";

    /** For relations: how to find target version for the relation. Defaults to empty, meaning 'this version'.
     *
     * NOTE: targetField and targetVersion are combined into a single field in the index.
     * For example, if targetField is empty and targetVersion resolves to "de", and this field is "contents__nl",
     * the target field for the relations will be the field will be "contents__de".
     */
    private String targetVersionPath = "";

    public ConfigStandoffAnnotations() {
    }

    public ConfigStandoffAnnotations(String path, String tokenRefPath) {
        this.path = path;
        this.tokenRefPath = tokenRefPath;
    }

    void validate(InputFormatMessages messages) {
        String t = "standoff annotations";
        messages.mustHave(t, path, "path");
        messages.mustHave(t, tokenRefPath, "tokenRefPath");
        for (ConfigAnnotation a : annotations)
            a.validate(messages, false);
        if (type == AnnotationType.FRAGMENT) {
            if (!annotations.isEmpty())
                messages.error("Fragments cannot have annotations.");
        } else {
            if (!metadata.isEmpty())
                messages.error("Standoff annotations of type " + type + " cannot have metadata blocks.");
        }
        for (ConfigMetadataBlock m : metadata)
            m.validate(messages);
    }

    public ConfigStandoffAnnotations copy() {
        ConfigStandoffAnnotations result = new ConfigStandoffAnnotations(path, tokenRefPath);
        for (ConfigAnnotation a : annotations) {
            result.addAnnotation(a.copy());
        }
        for (ConfigMetadataBlock m : metadata) {
            result.metadata.add(m.copy());
        }
        return result;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getTokenRefPath() {
        return tokenRefPath;
    }

    public void setTokenRefPath(String path) {
        this.tokenRefPath = path;
    }

    // synonym for span type
    public void setSpanStartPath(String path) {
        setTokenRefPath(path);
    }

    // synonym for relation type
    public void setSourcePath(String path) {
        setTokenRefPath(path);
    }

    public String getSpanEndPath() {
        return spanEndPath;
    }

    public void setSpanEndPath(String spanEndPath) {
        this.spanEndPath = spanEndPath;
        // Used to be implicit for annotation/span, so maintain backward compatibility.
        // Type must be explicitly set for relations though.
        setType(AnnotationType.SPAN);
    }

    // synonym for relation type
    public void setTargetPath(String targetPath) {
        setSpanEndPath(targetPath);
        setType(AnnotationType.RELATION);
    }

    public boolean isSpanEndIsInclusive() {
        return spanEndIsInclusive;
    }

    public void setSpanEndIsInclusive(boolean spanEndIsInclusive) {
        this.spanEndIsInclusive = spanEndIsInclusive;
    }

    public String getValuePath() {
        return valuePath;
    }

    public void setValue(String value) {
        this.valuePath = XPathUtil.fixedStringToXpath(value);
    }

    public void setValuePath(String valuePath) {
        this.valuePath = valuePath;
    }

    @Override
    public String toString() {
        return "ConfigStandoffAnnotations [path=" + path + "]";
    }

    public void setType(String type) {
        setType(AnnotationType.fromStringValue(type));
    }

    public void setType(AnnotationType type) {
        this.type = type;
    }

    public AnnotationType getType() {
        return type;
    }

    public String getRelationClass() {
        // Return default value for relation class if not specified
        return relationClass != null ? relationClass : RelationUtil.CLASS_DEFAULT;
    }

    public void setRelationClass(String relationClass) {
        this.relationClass = relationClass;
    }

    /**
     * Based on the configured targetField and/or targetVersion, return the field name to use.
     *
     * If neither targetField nor targetVersion is set, just returns the default field name.
     *
     * @param defaultTargetField if no targetField given, what field to use?
     * @param targetVersion target version to use (if empty, keep the one from the targetField)
     * @return resolved target field
     */
    public String resolveTargetField(String defaultTargetField, String targetVersion) {
        String f = targetField.isEmpty() ? defaultTargetField : targetField;
        return AnnotatedFieldNameUtil.changeParallelFieldVersion(f, targetVersion);
    }

    /**
     * Determine the actual relation class to index, including the parallel target version if applicable.
     *
     * Examples (if defaultTargetField == "contents__nl"):
     * - relationClass = "dep", targetField = "", targetVersion = "" --> "dep"
     * - relationClass = "al", targetField = "contents__de", targetVersion = "" --> "al__de"
     * - relationClass = "al", targetField = "contents__nl", targetVersion = "de" --> "al__de"
     *
     * @param defaultTargetField if no target field was specified, use this (i.e. the annotated field we belong to)
     * @param targetVersion target version to use (if empty, keep the one from the targetField)
     * @return the relation class to index
     */
    public String resolveRelationClass(String defaultTargetField, String targetVersion) {
        String actualTargetField = resolveTargetField(defaultTargetField, targetVersion);
        if (actualTargetField.equals(defaultTargetField)) {
            // Not a cross-field relation
            return getRelationClass();
        } else if (AnnotatedFieldNameUtil.isSameParallelBaseField(defaultTargetField, actualTargetField)) {
            // Cross-field relation to a different version of the same parallel field,
            // e.g. contents__nl --> contents__de
            String actualTargetVersion = AnnotatedFieldNameUtil.versionFromParallelFieldName(actualTargetField);
            return getRelationClass() + AnnotatedFieldNameUtil.PARALLEL_VERSION_SEPARATOR + actualTargetVersion;
        } else {
            // Cross-field relation to a different field
            // e.g. contents --> metadata
            // (we don't support this yet, but might want to in the future; this might be a reasonable way to
            //  index it)
            return getRelationClass() + AnnotatedFieldNameUtil.PARALLEL_VERSION_SEPARATOR
                                 + AnnotatedFieldNameUtil.PARALLEL_VERSION_SEPARATOR + actualTargetField;
        }
    }

    public String getTargetField() {
        return targetField;
    }

    public void setTargetField(String targetField) {
        this.targetField = targetField;
    }

    public String getTargetVersionPath() {
        return targetVersionPath;
    }

    public void setTargetVersionPath(String targetVersionPath) {
        this.targetVersionPath = targetVersionPath;
    }

    public void setRefTokenPositionIdPath(String v) {
        throw new InvalidInputFormatConfig("Encountered removed key 'refTokenPositionIdPath' (rename to 'tokenRefPath')");
    }

    public void setSpanNamePath(String v) {
        throw new InvalidInputFormatConfig("Encountered removed key 'spanNamePath' (rename to 'valuePath')");
    }
}
