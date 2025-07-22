package nl.inl.blacklab.server.lib.results;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

/** Versions of the BlackLab webservice API.
 *
 *  Right now, they only differ in small details.
 */
public enum ApiVersion {

    /** Fixes a few small annoyances such as inconsistent names, datatypes,
     *  field info where it doesn't belong, etc.
     *  Adds new keys to the response, but also leaves older keys, leading to
     *  some duplicate information. v5 will remove the old keys.
     *  A "transitional" API version that is mostly compatible with the previous one.
     */
    V4_0(4, 0),

    /** Experimental. An evolution of API v4 that removes parameters and keys from v3 that
     *  have a v4 equivalent.
     *  A stricter, cleaner version of the v4 API.
     */
    V5_0(5, 0),

    /** (this is NOT a supported API version; used for testing that "v0" indicates the latest minor in 0 series) */
    TEST_V0_0(0,0),

    /** (this is NOT a supported API version; used for testing that "v0" indicates the latest minor in 0 series) */
    TEST_V0_1(0,1),

    /** (this is NOT a supported API version; used for testing suffix) */
    TEST_V0_2_EXP(0, 2, "exp");

    /** Valid version indicators, e.g. 3, 3.0, 3.0-beta */
    private static final Pattern PATT_VERSION_STRING = Pattern.compile("(\\d+)(?:\\.(\\d+)(?:\\-(.*))?)?");

    /** What's considered the current version */
    public static final ApiVersion CURRENT = V5_0;

    /** An experimental future version of the API, if there is one; the current one, otherwise */
    public static final ApiVersion EXPERIMENTAL = V5_0;

    /** Latest in the version 4 series */
    public static final ApiVersion V4_LATEST = V4_0;

    /** (this is NOT a supported API version; used for testing that "v0" indicates the latest minor in 0 series) */
    public static final ApiVersion TEST_V0_LATEST = TEST_V0_1;

    /**
     * Return the appropriate API version from the given value
     *
     * Optional v is stripped from start of string.
     * Suffix can only be specified if minor version is specified as well.
     * If no minor version is given, the latest minor version is assumed.
     *
     * @param s version string, e.g. "3", "3.0", "3.0-beta"
     * @return closest version found
     * @throws IllegalArgumentException if no matching version was found
     */
    public static ApiVersion fromValue(String s) {
        s = s.toLowerCase();

        // Special value?
        if (StringUtils.isEmpty(s) || s.equals("cur") || s.equals("current"))
            return CURRENT;
        if (s.equals("exp") || s.equals("experimental"))
            return EXPERIMENTAL;

        // Skip optional v
        if (s.length() > 0 && s.charAt(0) == 'v')
            s = s.substring(1);

        // Parse the string
        Matcher m = PATT_VERSION_STRING.matcher(s);
        if (!m.matches())
            throw new IllegalArgumentException("Invalid API version: " + s);
        int major = Integer.parseInt(m.group(1));
        int minor = m.group(2) == null ? -1 : Integer.parseInt(m.group(2));
        String suffix = m.group(3) == null ? "" : m.group(3);

        // Find the closest (or exact, if minor/suffix specified) version
        ApiVersion versionFound = null;
        int versionSimilarity = Integer.MAX_VALUE;
        for (ApiVersion v: values()) {
            int similarity = v.isMatch(major, minor, suffix);
            if (similarity < versionSimilarity) {
                versionFound = v;
                versionSimilarity = similarity;
            }
        }
        if (versionFound == null)
            throw new IllegalArgumentException("Invalid API version: " + s);

        // Return version found, or just use the current version
        return versionFound;
    }


    /** The major version number */
    public final int major;

    /** The minor version number */
    public final int minor;

    /** Additional version info, if any */
    public final String suffix;

    private ApiVersion(int major, int minor) {
        this(major, minor, "");
    }

    private ApiVersion(int major, int minor, String suffix) {
        this.major = major;
        this.minor = minor;
        this.suffix = suffix == null ? "" : suffix;
    }

    private int isMatch(int major, int minor, String suffix) {
        if (minor >= 0 || !StringUtils.isEmpty(suffix)) {
            // We're looking for an exact match.
            return (this.major == major && this.minor == minor && this.suffix.equals(suffix)) ? 0 : Integer.MAX_VALUE;
        }
        // We're looking for the latest minor version of this major version.

        // Check that major version matches and we don't have a suffix
        if (this.major != major || !this.suffix.isEmpty())
            return Integer.MAX_VALUE;

        // Return the distance between the minor version we're looking for and the minor version of this version,
        // so we can find the closest match.
        // (if no minor version was specified, we want the highest minor version, so we pass some high number)
        return Math.abs(this.minor - (minor < 0 ? 1000 : minor));
    }

    @Override
    public String toString() {
        return major + "." + minor + (StringUtils.isEmpty(suffix) ? "" : "-" + suffix);
    }

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    public String getSuffix() {
        return suffix;
    }
}
