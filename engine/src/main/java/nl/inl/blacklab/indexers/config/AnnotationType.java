package nl.inl.blacklab.indexers.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum AnnotationType {
    TOKEN,
    SPAN,
    RELATION;

    @JsonCreator
    public static AnnotationType fromStringValue(String t) {
        return switch (t.toLowerCase()) {
            case "token" -> TOKEN;
            case "span" -> SPAN;
            case "relation" -> RELATION;
            default -> throw new IllegalArgumentException("Unknown standoff annotation type: " + t);
        };
    }

    @Override
    @JsonValue
    public String toString() {
        return super.toString().toLowerCase();
    }
}
