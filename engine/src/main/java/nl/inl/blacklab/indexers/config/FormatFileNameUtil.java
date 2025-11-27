package nl.inl.blacklab.indexers.config;

import java.util.regex.Pattern;

/** Utilities for recognizing and working with input format file names and format identifiers. */
public class FormatFileNameUtil {

    private static final String EXT_YAML = ".blf.yaml";

    /** A valid format identifier (letters, digits, underscores and dashes) */
    private static final String REGEX_FORMAT_IDENTIFIER = "[\\w\\-]+";

    /** The .blf part of the file extension (optional in the case of format file upload, but strongly recommended) */
    private static final String REGEX_BLF_EXTENSION = "\\.blf";

    /** The YAML/JSON part of the file extension */
    private static final String REGEX_YAML_JSON_EXTENSION = "\\.(ya?ml|json)";

    /** A valid format identifier (letters, digits, underscores and dashes) */
    private static final Pattern PATT_FORMAT_IDENTIFIER = Pattern.compile(REGEX_FORMAT_IDENTIFIER);

    /** Format file found on disk. Must end in .blf.yaml, .blf.yml or .blf.json */
    private static final Pattern REGEX_FORMAT_FILE_NAME = Pattern.compile(REGEX_FORMAT_IDENTIFIER +
            REGEX_BLF_EXTENSION + REGEX_YAML_JSON_EXTENSION);

    /** Uploaded format file (here the ".blf" part is optional, although strongly recommended) */
    private static final Pattern PATT_UPLOADED_FORMAT_FILE_NAME = Pattern
            .compile(REGEX_FORMAT_IDENTIFIER + "(" + REGEX_BLF_EXTENSION + ")?" + REGEX_YAML_JSON_EXTENSION);

    /** Check if the file is a valid format file name.
     *
     * Base name must be a valid formatIdentifier and the file must end in both .blf and
     * .yaml/.yml/.json extensions.
     *
     * @param fileName file name
     * @return true if valid format file name
     */
    public static boolean isValidFileName(String fileName) {
        return REGEX_FORMAT_FILE_NAME.matcher(fileName).matches();
    }

    /**
     * Check if the uploaded file name is a valid format file name.
     *
     * For uploaded format files, the ".blf" part is optional, although strongly recommended.
     *
     * @param fileName file name
     * @return true if valid uploaded format file name
     */
    public static boolean isValidUploadedFileName(String fileName) {
        return PATT_UPLOADED_FORMAT_FILE_NAME.matcher(fileName).matches();
    }

    /**
     * Remove .blf.yaml/.blf.yml/.blf.json or .yaml/.yml/.json extension from file name.
     *
     * @param fileName file name
     * @return file name without extension
     */
    public static String stripExtensions(String fileName) {
        String name = fileName.replaceAll(REGEX_YAML_JSON_EXTENSION + "$", "");
        if (name.endsWith(".blf"))
            return name.substring(0, name.length() - 4);
        return name;
    }

    /**
     * Check if the format name is a valid format identifier.
     *
     * @param formatName format name
     * @return true if valid format identifier
     */
    public static boolean isValidFormatIdentifier(String formatName) {
        return PATT_FORMAT_IDENTIFIER.matcher(formatName).matches();
    }

    /**
     * Get the file name for a YAML format file given its format identifier.
     *
     * @param formatIdentifier format identifier
     * @return file name
     */
    public static String yamlFormatFileName(String formatIdentifier) {
        return formatIdentifier + EXT_YAML;
    }
}
