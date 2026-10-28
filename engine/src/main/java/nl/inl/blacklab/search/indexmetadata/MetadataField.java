package nl.inl.blacklab.search.indexmetadata;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** A metadata field. */
public interface MetadataField extends Field {

    FieldType type();

    String analyzerName();

    MetadataFieldValues values(long maxValues);

    boolean occursInFragments();

    @Override
    default String contentsFieldName() {
        return name();
    }

    /** "Special" roles a metadata field can have, i.e. if it's the persistent identifier. */
    enum Role {
        /** This field is a regular metadata field. */
        REGULAR,

        /** This field is a "pid" field, i.e. it contains the document's unique identifier. */
        PID;

        @JsonCreator
        public static Role forValue(String value) {
            if (value == null)
                return REGULAR;
            return switch (value.toLowerCase()) {
                case "pid" -> PID;
                case "regular" -> REGULAR;
                default -> throw new IllegalArgumentException("Unknown role: " + value);
            };
        }

        @JsonValue
        @Override
        public String toString() {
            return switch (this) {
                case REGULAR -> "regular";
                case PID -> "pid";
            };
        }
    }
}
