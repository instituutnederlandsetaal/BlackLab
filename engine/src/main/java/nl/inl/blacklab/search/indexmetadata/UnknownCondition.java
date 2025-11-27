package nl.inl.blacklab.search.indexmetadata;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Conditions for using the unknown value */
public enum UnknownCondition {
    NEVER, // never use unknown value
    MISSING, // use unknown value when field is missing (not when empty)
    EMPTY, // use unknown value when field is empty (not when missing)
    MISSING_OR_EMPTY; // use unknown value when field is empty or missing

    @JsonCreator
    public static UnknownCondition fromStringValue(String string) {
        return valueOf(string.toUpperCase());
    }

    @JsonValue
    public String stringValue() {
        return toString().toLowerCase();
    }
}
