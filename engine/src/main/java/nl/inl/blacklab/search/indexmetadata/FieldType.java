package nl.inl.blacklab.search.indexmetadata;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Possible types of metadata fields. */
public enum FieldType {
    TOKENIZED,
    NUMERIC,
    UNTOKENIZED;

    @JsonCreator
    public static FieldType fromStringValue(String v) {
        return switch (v.toLowerCase()) {
            case "tokenized", "text" -> // deprecated
                    TOKENIZED;
            case "untokenized" -> UNTOKENIZED;
            case "numeric" -> NUMERIC;
            default -> throw new IllegalArgumentException(
                    "Unknown string value for FieldType: " + v + " (should be tokenized|untokenized|numeric)");
        };
    }

    @JsonValue
    public String stringValue() {
        return toString().toLowerCase();
    }

    public static FieldType defaultValue() {
        return TOKENIZED;
    }
}
