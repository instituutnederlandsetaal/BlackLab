package nl.inl.blacklab.search.lucene;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import nl.inl.blacklab.search.indexmetadata.RelationUtil;

/**
 * Information about a relation's source and target,
 * and optionally the relation type.
 * <p>
 * Note that this is not named MatchInfoRelation, as it is
 * used while indexing as well as matching.
 */
public class RelationInfo extends MatchInfo implements RelationLikeInfo {

    public static RelationInfo create() {
        return new RelationInfo(false, -1, -1, -1, -1, RELATION_ID_NO_INFO, null, null, "", "", false);
    }

    public static RelationInfo createWithFields(String sourceField, String targetField) {
        return new RelationInfo(false, -1, -1, -1, -1, RELATION_ID_NO_INFO, null, null, sourceField, targetField, false);
    }

    public static RelationInfo create(boolean onlyHasTarget, int sourceStart, int sourceEnd, int targetStart, int targetEnd, int relationId, boolean hasExtraInfoStored) {
        return new RelationInfo(onlyHasTarget, sourceStart, sourceEnd, targetStart, targetEnd, relationId, null, null, "", "", hasExtraInfoStored);
    }

    public static RelationInfo create(boolean onlyHasTarget, int sourceStart, int sourceEnd, int targetStart, int targetEnd, int relationId, String fullRelationType, boolean hasExtraInfoStored) {
        return new RelationInfo(onlyHasTarget, sourceStart, sourceEnd, targetStart, targetEnd, relationId, fullRelationType, null, "", "", hasExtraInfoStored);
    }

    /**
     * Create a relation info object for an inline tag.
     *
     * Only used for old external index.
     *
     * @param start start position
     * @param end end position
     * @param tagName tag name
     * @param field field this tag is from
     * @return the relation info object
     */
    public static RelationInfo createTag(int start, int end, String tagName, String field) {
        return new RelationInfo(false, start, start, end, end,
                RELATION_ID_NO_INFO, tagName, null, field, field,
                false);
    }

    /** If a relation has no info stored in the relation info index, it will get this special relation id.
     *  Saves disk space and time. */
    public static final int RELATION_ID_NO_INFO = -1;

    /**
     * Check that this relation has a target set.
     * <p>
     * E.g. when indexing a span ("inline tag"), we don't know the target until we encounter the closing tag,
     * so we can't store the payload until then.
     *
     * @return whether we have a target or not
     */
    public boolean hasTarget() {
        assert targetStart >= 0 && targetEnd >= 0 || targetStart < 0 && targetEnd < 0 : "targetStart and targetEnd inconsistent";
        return targetStart >= 0;
    }

    public String getRelationClass() {
        return RelationUtil.classFromFullType(getFullRelationType());
    }

    public String getRelationType() {
        return RelationUtil.typeFromFullType(getFullRelationType());
    }


    /**
     * Different spans we can return for a relation
     */
    public enum SpanMode {
        // Return the source span
        SOURCE("source"),

        // Return the target span
        TARGET("target"),

        // Return a span covering both source and target
        FULL_SPAN("full"),

        // Expand the current span so it covers sources and targets of all matched relations
        // (only valid for rspan(), not rel())
        ALL_SPANS("all");

        private final String code;

        SpanMode(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }

        @Override
        public String toString() {
            return getCode();
        }

        public static SpanMode fromCode(String code) {
            for (SpanMode mode: values()) {
                if (mode.getCode().equals(code)) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("Unknown span mode: " + code);
        }
    }

    /** Does this relation only have a target? (i.e. a root relation) */
    private boolean onlyHasTarget;

    /** Where does the source of the relation start?
     *  NOTE: if the relation has no source, set this to the targetStart as a convention. Invalid if < 0! */
    private int sourceStart;

    /** Where does the source of the relation end?
     *  NOTE: if the relation has no source, set this to the targetEnd as a convention. Invalid if < 0! */
    private int sourceEnd;

    /** Where does the target of the relation start?
     (the target is called 'dep' in dependency relations) */
    private int targetStart;

    /** Where does the target of the relation end? */
    private int targetEnd;

    /** Unique relation id */
    private int relationId;

    /** Is extra information (i.e. attributes) stored in relation info, or is there no extra info? */
    private boolean hasExtraInfoStored;

    /** Our relation type, or null if not set (set during search by SpansRelations) */
    private String fullRelationType;

    /** Tag attributes (if any), or empty if not set (set during search by SpansRelations).
     *  If this is empty and indexedTerm is set, attributes have not been determined yet (either from the term, or from relation info index). */
    private Map<String, List<String>> attributes;

    /** Field this points to (for non-parallel corpora, this will always be identical to source field). */
    private final String targetField;

    private RelationInfo(boolean onlyHasTarget, int sourceStart, int sourceEnd, int targetStart, int targetEnd,
            int relationId, String fullRelationType, Map<String, List<String>> attributes, String sourceField, String targetField, boolean hasExtraInfoStored) {
        super(sourceField);
        this.fullRelationType = fullRelationType;
        this.attributes = attributes == null ? Collections.emptyMap() : attributes;
        assert sourceField != null;
        assert targetField != null;
        this.targetField = targetField;
        this.onlyHasTarget = onlyHasTarget;
        if (onlyHasTarget && (sourceStart != targetStart || sourceEnd != targetEnd) && (sourceStart != targetStart || sourceEnd != targetStart)) {
            // Root relations must have a fake source (position where they are indexed).
            // Either source must equal target (older convention, remove eventually), e.g. 2-3 -> 2-3, OR
            // source must start at the same position as target and have length 0, e.g. 2-2 -> 2-3
            // (new convention, slightly more space efficient when indexing)
            throw new IllegalArgumentException("By convention, root relations should have a 'fake source' of length 0 that starts at the same position as the target " +
                    "(values here are SRC " + sourceStart + ", " + sourceEnd + " - TGT " + targetStart + ", " + targetEnd + ").");
        }
        this.sourceStart = sourceStart;
        this.sourceEnd = sourceEnd;
        this.targetStart = targetStart;
        this.targetEnd = targetEnd;
        this.relationId = relationId;
        this.hasExtraInfoStored = hasExtraInfoStored;
    }

    public void fill(int relationId, boolean onlyHasTarget, int sourceStart, int sourceEnd, int targetStart,
            int targetEnd, boolean hasExtraInfoStored) {
        this.relationId = relationId;
        this.onlyHasTarget = onlyHasTarget;
        this.sourceStart = sourceStart;
        this.sourceEnd = sourceEnd;
        this.targetStart = targetStart;
        this.targetEnd = targetEnd;
        this.hasExtraInfoStored = hasExtraInfoStored;
    }

    public RelationInfo copy() {
        return new RelationInfo(onlyHasTarget, sourceStart, sourceEnd, targetStart, targetEnd, relationId,
                fullRelationType, attributes, getField(), targetField, hasExtraInfoStored);
    }

    @Override
    public boolean isRoot() {
        return onlyHasTarget;
    }

    @Override
    public int getSourceStart() {
        return sourceStart;
    }

    @Override
    public int getSourceEnd() {
        return sourceEnd;
    }

    @Override
    public int getTargetStart() {
        return targetStart;
    }

    @Override
    public int getTargetEnd() {
        return targetEnd;
    }

    public int getSpanStart() {
        if (!isCrossFieldRelation()) {
            // Regular relation; return full span
            return Math.min(sourceStart, targetStart);
        } else {
            // Relation points to another field; return source span
            return getSourceStart();
        }
    }

    public int getSpanEnd() {
        if (!isCrossFieldRelation()) {
            // Regular relation; return full span
            return Math.max(sourceEnd, targetEnd);
        } else {
            // Relation points to another field; return source span
            return getSourceEnd();
        }
    }

    @Override
    public String getTargetField() {
        return targetField;
    }

    public int spanStart(SpanMode mode) {
        return switch (mode) {
            case SOURCE -> getSourceStart();
            case TARGET -> getTargetStart();
            case FULL_SPAN -> getSpanStart();
            case ALL_SPANS -> throw new IllegalArgumentException("ALL_SPANS should have been handled elsewhere");
            default -> throw new IllegalArgumentException("Unknown mode: " + mode);
        };
    }

    public int spanEnd(SpanMode mode) {
        return switch (mode) {
            case SOURCE -> getSourceEnd();
            case TARGET -> getTargetEnd();
            case FULL_SPAN -> getSpanEnd();
            case ALL_SPANS -> throw new IllegalArgumentException("ALL_SPANS should have been handled elsewhere");
            default -> throw new IllegalArgumentException("Unknown mode: " + mode);
        };
    }

    @Override
    public boolean isCrossFieldRelation() {
        return targetField != null && !targetField.equals(getField());
    }

    /**
     * Does this relation info represent an inline tag?
     * <p>
     * Inline tags are indexed as relations with zero-length source and target.
     * Unlike other relations, source always occurs before target for tag relations.
     * <p>
     * The reason this method exists is that the classic external index doesn't support
     * "regular" relations but does support inline tags. When we eventually drop support
     * for the classic external index format, this method can be removed.
     *
     * @return true if this relation info represents an inline tag
     */
    private boolean isTag() {
        // A tag is a relation with source and target, both of which are length 0, and source occurs before target.
        // (target can also be -1, which means we don't know yet)
        // (or rather, such a relation can be indexed as a tag in the classic external index)
        return !onlyHasTarget && (sourceEnd - sourceStart == 0 && targetEnd - targetStart == 0 &&
                (targetStart == -1 || sourceStart <= targetStart));
    }

    @Override
    public Type getType() {
        return isTag() ? Type.INLINE_TAG : Type.RELATION;
    }

    /** (Used by SpansRelations) */
    public void setFullRelationType(String fullRelationType) {
        this.fullRelationType = fullRelationType;
    }

    /** (Used by SpansRelations) */
    public void setAttributes(Map<String, List<String>> attributes) {
        this.attributes = attributes;
    }

    public int getRelationId() {
        return relationId;
    }

    public boolean mayHaveInfoInRelationIndex() {
        return hasExtraInfoStored;
    }

    /**
     * Get the full relation type, consisting of the class and type.
     *
     * @return full relation type
     */
    public String getFullRelationType() {
        return fullRelationType;
    }

    public Map<String, List<String>> getAttributes() {
        return attributes;
    }

    private String toStringOptSourceTargetFields(String defaultField) {
        if (getField().equals(defaultField)) {
            // Source field is the default field, don't mention it separately
            if (targetField.equals(defaultField)) {
                // Both are default
                return "";
            } else {
                // Source is default, target is not
                return " (-> " + targetField + ")";
            }
        } else {
            // Source field is not the default field
            return " (" + getField() + " -> " + targetField + ")";
        }
    }

    private String attValue(List<String> values) {
        return values.size() == 1 ? values.get(0) : values.toString();
    }

    @Override
    public String toString(String defaultField) {
        // Inline tag
        if (isTag()) {
            String tagName = fullRelationType == null ? "UNKNOWN" : RelationUtil.typeFromFullType(fullRelationType);
            String attr = attributes == null || attributes.isEmpty() ? "" :
                    " " + attributes.entrySet().stream()
                            .map(e -> e.getKey() + "=\"" + attValue(e.getValue()) + "\"")
                            .collect(Collectors.joining(" "));
            return "tag(<" + tagName + attr + "/> at " + getSpanStart() + "-" + getSpanEnd() + " )" +
                    toStringOptFieldName(defaultField);
        }

        // Relation
        int targetLen = targetEnd - targetStart;
        String target = targetStart + (targetLen != 1 ? "-" + targetEnd : "");
        if (isRoot())
            return "rel( ^-" + (fullRelationType == null ? "??" : "") + "-> " + target + ")";
        int sourceLen = sourceEnd - sourceStart;
        String source = sourceStart + (sourceLen != 1 ? "-" + sourceEnd : "");
        return "rel(" + source + " -" + fullRelationType + "-> " + target + ")" +
                toStringOptSourceTargetFields(defaultField);
    }

    @Override
    public int compareTo(MatchInfo o) {
        if (o instanceof RelationInfo)
            return compareTo((RelationInfo) o);
        return super.compareTo(o);
    }

    public int compareTo(RelationInfo o) {
        int n;
        n = -Boolean.compare(onlyHasTarget, o.onlyHasTarget);
        if (n != 0)
            return n;
        if (!onlyHasTarget && !o.onlyHasTarget) {
            n = Integer.compare(sourceStart, o.sourceStart);
            if (n != 0)
                return n;
            n = Integer.compare(sourceEnd, o.sourceEnd);
            if (n != 0)
                return n;
        }
        n = Integer.compare(targetStart, o.targetStart);
        if (n != 0)
            return n;
        n = Integer.compare(targetEnd, o.targetEnd);
        if (n != 0)
            return n;
        n = fullRelationType.compareTo(o.fullRelationType);
        if (n != 0)
            return n;
        if (attributes == null) {
            if (o.attributes != null)
                return -1;
            return 0;
        } else {
            if (o.attributes == null)
                return 1;
            n = Integer.compare(attributes.hashCode(), o.attributes.hashCode());
        }
        return n;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        RelationInfo relationInfo = (RelationInfo) o;
        return onlyHasTarget == relationInfo.onlyHasTarget &&
                sourceStart == relationInfo.sourceStart && sourceEnd == relationInfo.sourceEnd &&
                targetStart == relationInfo.targetStart && targetEnd == relationInfo.targetEnd &&
                Objects.equals(fullRelationType,relationInfo.fullRelationType) &&
                Objects.equals(attributes, relationInfo.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(onlyHasTarget, sourceStart, sourceEnd, targetStart, targetEnd, fullRelationType, attributes);
    }
}
