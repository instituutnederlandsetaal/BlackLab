package nl.inl.blacklab.indexers.config;

public enum AnnotationType {
    TOKEN,
    SPAN,
    RELATION;

    public static AnnotationType fromStringValue(String t) {
        return switch (t.toLowerCase()) {
            case "token" -> TOKEN;
            case "span" -> SPAN;
            case "relation" -> RELATION;
            default -> throw new IllegalArgumentException("Unknown standoff annotation type: " + t);
        };
    }

    @Override
    public String toString() {
        return super.toString().toLowerCase();
    }
}
