package nl.inl.blacklab.search.indexmetadata;

/** Possible types of metadata fields. */
public enum FieldType {
    TOKENIZED,
    NUMERIC,
    UNTOKENIZED;

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

    public String stringValue() {
        return toString().toLowerCase();
    }
}
