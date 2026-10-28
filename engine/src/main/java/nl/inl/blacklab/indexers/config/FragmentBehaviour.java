package nl.inl.blacklab.indexers.config;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Different behaviours with respect to fragments for indexing metadata fields
 */
public enum FragmentBehaviour {
    DEFAULT,   // if the field is present in at least one fragment, index it only at fragment level;
    // otherwise, index it only at document level
    SEPARATE,  // index at document level and fragment level separately (e.g. pid field)
    DOC_VALUE; // fragments will simply index the value from the document-level, not their own specific value for
               // this field (i.e. no need to apply this metadata rule for each fragment)

    @JsonCreator
    public static FragmentBehaviour forValue(String value) {
        String sanitized = sanitizeName(value);
        switch (sanitized) {
        case "default" -> {
            return DEFAULT;
        }
        case "separate" -> {
            return SEPARATE;
        }
        case "docvalue" -> {
            return DOC_VALUE;
        }
        default -> throw new IllegalArgumentException(
                "Unknown fragments value: " + value + "(valid values: default, separate or docvalue)");
        }
    }

    private static @NonNull String sanitizeName(String value) {
        return value.toLowerCase().replaceAll("[-_]", "");
    }

    @JsonValue
    @Override
    public String toString() {
        return sanitizeName(super.toString());
    }

    public boolean indexAtDocLevel() {
        return this != DEFAULT;
    }

    public boolean applyRuleAtFragLevel() {
        return this != DOC_VALUE;
    }

    public boolean inheritFromDocLevel() {
        return this != SEPARATE;
    }
}
