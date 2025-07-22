package nl.inl.blacklab.indexers.config;

import java.util.LinkedHashMap;
import java.util.Map;

import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.RelationUtil;

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

    /** The annotations to index at the referenced token positions. */
    private final Map<String, ConfigAnnotation> annotations = new LinkedHashMap<>();

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

    public void validate() {
        String t = "standoff annotations";
        ConfigInputFormat.req(path, t, "path");
        ConfigInputFormat.req(tokenRefPath, t, "tokenRefPath");
        for (ConfigAnnotation a : annotations.values())
            a.validate();
    }

    public ConfigStandoffAnnotations copy() {
        ConfigStandoffAnnotations result = new ConfigStandoffAnnotations(path, tokenRefPath);
        for (ConfigAnnotation a : annotations.values()) {
            result.addAnnotation(a.copy());
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

    public String getSpanEndPath() {
        return spanEndPath;
    }

    public void setSpanEndPath(String spanEndPath) {
        this.spanEndPath = spanEndPath;
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

    public void setValuePath(String valuePath) {
        this.valuePath = valuePath;
    }

    @Override
    public Map<String, ConfigAnnotation> getAnnotations() {
        return annotations;
    }
    
    @Override
    public Map<String, ConfigAnnotation> getAnnotationsFlattened() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addAnnotation(ConfigAnnotation annotation) {
        this.annotations.put(annotation.getName(), annotation);
    }

    @Override
    public String toString() {
        return "ConfigStandoffAnnotations [path=" + path + "]";
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
}
