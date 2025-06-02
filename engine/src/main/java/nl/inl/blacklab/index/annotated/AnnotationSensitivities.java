package nl.inl.blacklab.index.annotated;

import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;

/**
 * What sensitivities are indexed for an annotation.
 */
public enum AnnotationSensitivities {
    DEFAULT, // "insensitive (except for some internal annotations)"
    LEGACY_DEFAULT, // "choose default based on field name" (DEPRECATED)
    ONLY_SENSITIVE, // only index case- and diacritics-sensitively
    ONLY_INSENSITIVE, // only index case- and diacritics-insensitively
    SENSITIVE_AND_INSENSITIVE, // case+diac sensitive as well as case+diac insensitive
    CASE_AND_DIACRITICS_SEPARATE; // all four combinations (sens, insens, case-insens, diac-insens)

    public static AnnotationSensitivities fromStringValue(String v) {
        return switch (v.toLowerCase()) {
            case "default", "" -> DEFAULT;
            case "legacy-default" -> LEGACY_DEFAULT;
            case "sensitive", "s" -> ONLY_SENSITIVE;
            case "insensitive", "i" -> ONLY_INSENSITIVE;
            case "sensitive_insensitive", "si" -> SENSITIVE_AND_INSENSITIVE;
            case "case_diacritics_separate", "all" -> CASE_AND_DIACRITICS_SEPARATE;
            default -> throw new IllegalArgumentException("Unknown string value for SensitivitySetting: " + v
                    + " (should be default|sensitive|insensitive|sensitive_insensitive|case_diacritics_separate or s|i|si|all)");
        };
    }

    public String getStringValue() {
        return switch (this) {
            case DEFAULT -> "default";
            case LEGACY_DEFAULT -> "legacy_default";
            case ONLY_SENSITIVE -> "sensitive";
            case ONLY_INSENSITIVE -> "insensitive";
            case SENSITIVE_AND_INSENSITIVE -> "sensitive_insensitive";
            case CASE_AND_DIACRITICS_SEPARATE -> "case_diacritics_separate";
            default -> throw new IllegalArgumentException("Unknown AnnotationSensitivities: " + this);
        };
    }

    public String stringValueForResponse() {
        return switch (this) {
            case DEFAULT, LEGACY_DEFAULT -> getStringValue().toUpperCase();
            case ONLY_SENSITIVE -> "ONLY_SENSITIVE";
            case ONLY_INSENSITIVE -> "ONLY_INSENSITIVE";
            case SENSITIVE_AND_INSENSITIVE -> "SENSITIVE_AND_INSENSITIVE";
            case CASE_AND_DIACRITICS_SEPARATE -> "CASE_AND_DIACRITICS_SEPARATE";
            default -> throw new IllegalArgumentException("Unknown AnnotationSensitivities: " + this);
        };
    }

    @Override
    public String toString() {
        // toString() is informative, not authoritative, but let's return
        // the same as the "official" method getStringValue.
        return getStringValue();
    }

    public static AnnotationSensitivities defaultForAnnotation(String name, int configVersion) {
        if (name.equals(AnnotatedFieldNameUtil.RELATIONS_ANNOT_NAME)) {
            // Relations annotation (which includes inline tags) defaults to
            // insensitive nowadays (used to be sensitive), unless configured otherwise.
            boolean sensitive = BlackLab.config().getIndexing().isRelationsSensitive();
            return sensitive ? AnnotationSensitivities.ONLY_SENSITIVE :
                    AnnotationSensitivities.ONLY_INSENSITIVE;
        }

        // Check for legacy special cases that get sensitive+insensitive by default
        if (configVersion < 2 && AnnotatedFieldNameUtil.defaultSensitiveInsensitive(name))
            return AnnotationSensitivities.SENSITIVE_AND_INSENSITIVE;

        // No special case; default to insensitive unless explicitly set to sensitive
        return AnnotationSensitivities.ONLY_INSENSITIVE;
    }
}
