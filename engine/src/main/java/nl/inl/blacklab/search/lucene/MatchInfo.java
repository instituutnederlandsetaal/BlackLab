package nl.inl.blacklab.search.lucene;

import nl.inl.blacklab.search.indexmetadata.AnnotatedField;

/**
 * Information about a match (captured while matching).
 *
 * This can be a captured group, or a relation (e.g. a dependency relation
 * or an inline tag) used in the query.
 */
public abstract class MatchInfo implements Comparable<MatchInfo> {

    /** Get a match info by index.
     * <p>
     * Because the array length can vary because of how matching happens
     * (different segments may capture different match info), this method
     * will return null if the index is out of bounds.
     */
    public static MatchInfo get(MatchInfo[] matchInfo, int index) {
        assert index >= 0 : "Match info index must be non-negative, got " + index;
        return index < matchInfo.length ? matchInfo[index] : null;
    }

    /**
     * Merge the match info from one array into another.
     * <p>
     * This will overwrite the target array's match info with the
     * match info from the toMerge array if the toMerge array has
     * a non-null value at that index.
     * <p>
     * It will also take the length of both arrays into account.
     *
     * @param target
     * @param toMerge
     */
    public static void mergeInto(MatchInfo[] target, MatchInfo[] toMerge) {
        if (toMerge != null) {
            int n = Math.min(target.length, toMerge.length);
            for (int i = 0; i < n; i++) {
                if (toMerge[i] != null) // don't overwrite other clause's captures!
                    target[i] = toMerge[i];
            }
        }
    }

    /**
     * The type of match info.
     */
    public enum Type {
        SPAN,
        RELATION,
        LIST_OF_RELATIONS,
        INLINE_TAG;

        public String jsonName() {
            return switch (this) {
                case SPAN -> "span";
                case RELATION -> "relation";
                case LIST_OF_RELATIONS -> "list";
                case INLINE_TAG -> "tag";
            };
        }
    }

    /**
     * Null-safe equality test of two MatchInfos.
     *
     * Returns true if both are null, or if they are equal.
     *
     * @param a the first MatchInfo
     * @param b the second MatchInfo
     * @return true iff both are null, or if they are equal
     */
    public static boolean areEqual(MatchInfo[] a, MatchInfo[] b) {
        if ((a == null) != (b == null)) {
            // One is null, the other is not.
            return false;
        }
        if (a == null) {
            // Both null
            return true;
        }
        // Both set
        return a.equals(b);
    }

    /** Field this match info is from.
     *  If this is not a parallel corpus, this will always be the field that was searched.
     *  Never null.
     */
    private final AnnotatedField field;

    MatchInfo(AnnotatedField field) {
        assert field != null;
        this.field = field;
    }

    public AnnotatedField getField() {
        return field;
    }

    protected String toStringOptFieldName(String defaultField) {
        return field.equals(defaultField) ? "" : " (" + getField() + ")";
    }

    public abstract Type getType();

    @Override
    public String toString() {
        return toString("");
    }

    public abstract String toString(String defaultField);

    @Override
    public int compareTo(MatchInfo o) {
        // Subclasses should compare properly if types match;
        // if not, just compare class names
        return getClass().getName().compareTo(o.getClass().getName());
    }

    @Override
    public abstract boolean equals(Object o);

    @Override
    public abstract int hashCode();

    /** Will return the start of the match info span.
     * <p>
     * Note that for relations, this is <code>min(sourceStart, targetStart)</code>,
     * and for lists of match info, it's the minimum of all their span starts.
     */
    public abstract int getSpanStart();

    /** Will return the end of the match info span.
     * <p>
     * Note that for relations, this is <code>max(sourceEnd, targetEnd)</code>,
     * and for lists of match info, it's the maximum of all their span ends.
     */
    public abstract int getSpanEnd();

    /**
     * Get the start of either the full span or just the source or target (if applicable).
     *
     * Relations have a source and target, other match infos do not and will always
     * use the full span.
     *
     * @param mode span mode to use
     * @return span start
     */
    public int spanStart(RelationInfo.SpanMode mode) {
        return getSpanStart();
    }


    /**
     * Get the end of either the full span or just the source or target (if applicable).
     *
     * Relations have a source and target, other match infos do not and will always
     * use the full span.
     *
     * @param mode span mode to use
     * @return span end
     */
    public int spanEnd(RelationInfo.SpanMode mode) {
        return getSpanEnd();
    }

    public boolean isSpanEmpty() {
        return getSpanStart() == getSpanEnd();
    }

    /** Match info definition: name, type, (optionally) overridden field */
    public static class Def {
        /** This group's index in the captured group array */
        private final int index;

        /** This group's name */
        private final String name;

        /** What type of match info is this? (span, tag, relation, list of relations) */
        private Type type;

        /** What field is this match info for? Never null. */
        private final AnnotatedField field;

        /** Target field of (list of) relations, or null if same as source or not applicable. */
        private final AnnotatedField targetField;

        public Def(int index, String name, Type type, AnnotatedField field, AnnotatedField targetField) {
            assert index >= 0 : "Match info may not have negative index";
            this.index = index;
            this.name = name;
            this.type = type;
            assert field != null;
            this.field = field;
            this.targetField = targetField == null || targetField.equals(field) ? null : targetField;
        }

        public int getIndex() {
            return index;
        }

        public String getName() {
            return name;
        }

        public Type getType() {
            return type;
        }

        public AnnotatedField getField() {
            return field;
        }

        public AnnotatedField getTargetField() {
            return targetField;
        }

        public void updateType(Type type) {
            if (type == null) {
                // This just means "we don't know the type at this point in the query",
                // which is fine (any match info can be used as a regular span). The type
                // will be known at one location in the query, so this will eventually get set.
                return;
            }
            assert this.type == null || type == this.type :
                    "Trying to overwrite match info '" + name + "' type from " + this.type + " to " + type;
            this.type = type;
        }

        public boolean isForeignHit() {
            return name.endsWith(SpanQueryCaptureRelationsBetweenSpans.TAG_MATCHINFO_TARGET_HIT);
        }
    }
}
