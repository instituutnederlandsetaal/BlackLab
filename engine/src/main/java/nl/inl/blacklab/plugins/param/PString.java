package nl.inl.blacklab.plugins.param;

import java.util.regex.Pattern;

import nl.inl.util.StringUtil;

public record PString(
        String name,
        boolean isRequired,
        Pattern regex,           // must match
        int maxLength
) implements PluginParam {

    // letters, digits, underscore, dash, start with letter
    public static final Pattern REGEX_IDENTIFIER = Pattern.compile("[\\p{L}_][\\p{L}0-9_\\-]*");

    public static final int DEFAULT_MAX_LENGTH = 256;

    public PString {
        assert regex != null;
    }

    public static PString matching(String name, Pattern regex, boolean isRequired, int maxLength) {
        return new PString(name, isRequired, regex, maxLength);
    }

    public static PString matching(String name, String regex, boolean isRequired, int maxLength) {
        return matching(name, Pattern.compile(regex), isRequired, maxLength);
    }

    public static PString matching(String name, String regex, boolean isRequired) {
        return matching(name, Pattern.compile(regex), isRequired, DEFAULT_MAX_LENGTH);
    }

    public static PString matching(String name, String regex) {
        return matching(name, Pattern.compile(regex), false, DEFAULT_MAX_LENGTH);
    }

    public static PString any(String name, boolean isRequired, int maxLength) {
        return matching(name, StringUtil.PATT_ANY_VALUE, isRequired, maxLength);
    }

    public static PString any(String name, boolean isRequired) {
        return any(name, isRequired, DEFAULT_MAX_LENGTH);
    }

    public static PString any(String name) {
        return any(name, false);
    }

    public static PString identifier(String name, boolean isRequired, int maxLength) {
        return matching(name, REGEX_IDENTIFIER, isRequired, maxLength);
    }

    public static PString identifier(String name, boolean isRequired) {
        return identifier(name, isRequired, DEFAULT_MAX_LENGTH);
    }

    public static PString identifier(String name) {
        return identifier(name, false);
    }

    @Override
    public Object validate(Object raw) {
        String rawStr = raw.toString();
        if (regex != null && !regex.matcher(rawStr).matches())
            throw new InvalidPluginParameters(msgNamePrefix() + "value '" + rawStr + "' does not match regex /" + regex + "/: ");
        if (rawStr.length() > maxLength)
            throw new InvalidPluginParameters(msgNamePrefix() + "value exceeds maximum length of " + maxLength + " characters: " + rawStr);
        return rawStr;
    }
}
