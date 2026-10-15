package nl.inl.blacklab.search.textpattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.util.StringUtil;

/**
 * Represents a regex with anchors and sensitivity prefix stripped.
 * <p>
 * ^ and trailing $ anchors, which are implied and not needed for regex matching.
 * Sensitivity is preserved in the sensitivity field.
 *
 * @param regex       regex after any stripping
 * @param sensitivity sensitivity to use for comparison (if no prefix is found), or null for default
 */
public record RegexStripped(String regex, MatchSensitivity sensitivity) {

    public static final String REGEX_SENSITIVITY_PREFIX = "\\(\\?(s|-?i|c|d)\\)";

    public static final String REGEX_LETTERS_AND_DIGITS = "[\\w\\d]+";

    public static final Pattern PATT_SENSITIVITY_PREFIX = Pattern.compile(REGEX_SENSITIVITY_PREFIX);

    public static final Pattern PATT_ONLY_LETTERS_AND_DIGITS =
            Pattern.compile(REGEX_LETTERS_AND_DIGITS, Pattern.UNICODE_CHARACTER_CLASS);

    public RegexStripped(String regex) {
        this(regex, null);
    }

    public RegexStripped(String regex, MatchSensitivity sensitivity) {
        if (regex.startsWith("^"))
            regex = regex.substring(1);
        Matcher matcher = PATT_SENSITIVITY_PREFIX.matcher(regex);
        if (matcher.find()) {
            sensitivity = switch (matcher.group(1)) {
                case TextPattern.REGEX_PREFIX_SENSITIVE,
                     TextPattern.REGEX_PREFIX_SENSITIVE_ALT -> MatchSensitivity.SENSITIVE;
                case TextPattern.REGEX_PREFIX_INSENSITIVE -> MatchSensitivity.INSENSITIVE;
                case TextPattern.REGEX_PREFIX_DIACRITICS_SENSITIVE -> MatchSensitivity.CASE_INSENSITIVE;
                case TextPattern.REGEX_PREFIX_CASE_SENSITIVE -> MatchSensitivity.DIACRITICS_INSENSITIVE;
                default -> sensitivity;
            };
            String remainingRegex = regex.substring(matcher.group(1).length() + 3);
            this.regex = stripAnchors(remainingRegex);
            this.sensitivity = sensitivity;
        } else {
            this.regex = stripAnchors(regex);
            this.sensitivity = sensitivity;
        }
    }

    private static String stripAnchors(String regex) {
        if (regex.startsWith("^"))
            regex = regex.substring(1);
        if (regex.endsWith("$"))
            regex = regex.substring(0, regex.length() - 1);
        return regex;
    }

    public boolean isAnyToken() {
        // See if a sensitivity prefix was provided. Also strip off any leading ^ and trailing $ anchors.
        return regex().equals(StringUtil.REGEX_ANY_VALUE);
    }

    public boolean isLettersAndDigits() {
        return PATT_ONLY_LETTERS_AND_DIGITS.matcher(regex()).matches();
    }
}
