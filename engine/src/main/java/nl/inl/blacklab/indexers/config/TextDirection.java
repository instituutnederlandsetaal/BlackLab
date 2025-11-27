package nl.inl.blacklab.indexers.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Text direction: left-to-right or right-to-left (and possible future values?)
 */
public enum TextDirection {
    LEFT_TO_RIGHT("ltr", "lefttoright"),
    RIGHT_TO_LEFT("rtl", "righttoleft");

    private final String[] codes;

    TextDirection(String... codes) {
        this.codes = codes;
    }

    /**
     * Get the primary string code for this TextDirection.
     * 
     * @return string code
     */
    @JsonValue
    public String getCode() {
        return this.codes[0];
    }

    /**
     * Does the specified code match this value?
     * 
     * @param code code to match, e.g. "LTR" or "right-to-left"
     * @return true if it matches, false if not
     */
    public boolean matchesCode(String code) {
        code = code.toLowerCase().replaceAll("[^a-z]", ""); // remove any spaces, dashes, etc.
        for (String checkCode : codes) {
            if (checkCode.equals(code))
                return true;
        }
        return false;
    }

    /**
     * Return a TextDirection from a string code (e.g. "LTR", "right-to-left", etc.)
     *
     * @param code textDirection code to recognize
     * @return corresponding TextDirection
     */
    @JsonCreator
    public static TextDirection fromCode(String code) {
        for (TextDirection d : TextDirection.values()) {
            if (d.matchesCode(code))
                return d;
        }
        return LEFT_TO_RIGHT; // default value
    }
}
