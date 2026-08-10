package nl.inl.blacklab.indexers.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Different types of annotations that can be defined in a standoffAnnotations block in a .blf.yaml file. */
public enum AnnotationType {
    /** Annotation on a single token. */
    TOKEN,

    /** A span annotation: some words in an annotated field, e.g. a sentence or a named entity. Has type and optionally
     * attributes. */
    SPAN,

    /** A relation annotation: a relation between two words or two groups of words. Has a
     * relation type and optionally attributes. Can point to the same annotated field or to another.
     * For example dependency relations, or parallel alignment relations. */
    RELATION,

    /** A fragment; not really an annotation, more like a "subdocument".
     * A part of a document (actually part of an annotated field) that can have its own metadata
     * just like a whole document can. It inherits metadata from the document level and from any enclosing fragments.
     * Indexing and searching this metadata works essentially the same as for documents.
     * I.e. if part of the text was written in different years or by different authors. */
    FRAGMENT;

    @JsonCreator
    public static AnnotationType fromStringValue(String t) {
        return switch (t.toLowerCase()) {
            case "token" -> TOKEN;
            case "span" -> SPAN;
            case "relation" -> RELATION;
            case "fragment" -> FRAGMENT;
            default -> throw new IllegalArgumentException("Unknown standoff annotation type: " + t);
        };
    }

    @Override
    @JsonValue
    public String toString() {
        return super.toString().toLowerCase();
    }
}
