package nl.inl.blacklab.search.indexmetadata;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;

import nl.inl.util.StringUtil;

/**
 * Desired match sensitivity.
 * 
 * (Previously called "alternative" when talking about Lucene field names,
 * and "case/diacritics-sensitivity" when talking about matching, but
 * those are the same thing)
 */
public enum MatchSensitivity {
    
    SENSITIVE(true, true, "s"),
    INSENSITIVE(false, false, "i"),
    CASE_INSENSITIVE(false, true, "ci"),
    DIACRITICS_INSENSITIVE(true, false, "di");
    
    public static MatchSensitivity get(boolean caseSensitive, boolean diacriticsSensitive) {
        if (caseSensitive)
            return diacriticsSensitive ? SENSITIVE : DIACRITICS_INSENSITIVE;
        else
            return diacriticsSensitive ? CASE_INSENSITIVE : INSENSITIVE;
    }
    
    public static MatchSensitivity caseAndDiacriticsSensitive(boolean b) {
        return b ? SENSITIVE : INSENSITIVE;
    }

    public static MatchSensitivity fromLuceneFieldSuffix(String code) {
        return switch (code.toLowerCase()) {
            case "s" -> SENSITIVE;
            case "i" -> INSENSITIVE;
            case "ci" -> CASE_INSENSITIVE;
            case "di" -> DIACRITICS_INSENSITIVE;
            default -> throw new IllegalArgumentException("Unknown sensitivity field code: " + code);
        };
    }

    @JsonCreator
    public static MatchSensitivity fromName(String value) {
        if (value.equalsIgnoreCase("sensitive"))
            return SENSITIVE;
        if (value.equalsIgnoreCase("insensitive"))
            return INSENSITIVE;
        return fromLuceneFieldSuffix(value);
    }

    private final boolean caseSensitive;
    
    private final boolean diacriticsSensitive;
    
    private final String luceneFieldCode;

    MatchSensitivity(boolean caseSensitive, boolean diacriticsSensitive, String luceneFieldCode) {
        this.caseSensitive = caseSensitive;
        this.diacriticsSensitive = diacriticsSensitive;
        this.luceneFieldCode = luceneFieldCode;
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    public boolean isDiacriticsSensitive() {
        return diacriticsSensitive;
    }
    
    /** @return Suffix used for corresponding Lucene field */
    public String luceneFieldSuffix() {
        return luceneFieldCode;
    }

    @Override
    public String toString() {
        return luceneFieldSuffix();
    }

    public String desensitize(String input) {
        // TODO: instead of Locale.ROOT we should probably use the configured locale here!
        return switch (this) {
            case CASE_INSENSITIVE -> input.toLowerCase(Locale.ROOT);
            case DIACRITICS_INSENSITIVE ->
                    StringUtil.removeCharsIgnoredByInsensitiveCollator(StringUtil.stripAccents(input));
            case INSENSITIVE ->
                    StringUtil.removeCharsIgnoredByInsensitiveCollator(StringUtil.stripAccents(input).toLowerCase(Locale.ROOT));
            case SENSITIVE -> input;
        };
    }
}
