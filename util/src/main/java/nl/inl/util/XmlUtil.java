package nl.inl.util;

import java.util.regex.Pattern;

/**
 * Utilities for working with XML.
 */
public class XmlUtil {

    /**
     * Valid XML element names. Field and annotation names should generally conform to
     * this.
     */
    private static final Pattern REGEX_VALID_XML_ELEMENT_NAME = Pattern.compile("[a-zA-Z_][a-zA-Z\\d\\-_.]*");

    private XmlUtil() {
    }

    /**
     * Takes an XML input string and... - removes tags - replaces entities with
     * characters - normalizes whitespace
     *
     * @param conc the input XML string
     * @return the plain text output string
     */
    public static String xmlToPlainText(String conc) {
        return xmlToPlainText(conc, false);
    }

    /**
     * Sanitize name if necessary, replacing forbidden characters with underscores.
     *
     * Also prepends an underscore if the name start in an invalid way (with the letters "xml" or not with letter or underscore).
     *
     * @param name           name to sanitize
     * @return sanitized name
     */
    public static String sanitizeXmlElementName(String name) {
        if (name.isEmpty())
            return "_EMPTY_";
        // can only contain letter, digit, dash (used to be disallowed in config v1, but no more), underscore and period
        name = name.replaceAll("[^\\p{L}\\p{N}_.\\-]", "_");
        if (name.matches("^[^\\p{L}_].*$") || name.toLowerCase().startsWith("xml")) { // must start with letter or underscore, may not start with "xml"
            name = "_" + name;
        }
        return name;
    }

    /**
     * Is the specified name a valid XML element name?
     *
     * Generally, field and annotation names should be valid XML element names, so we
     * don't have to sanitize them when generating output XML.
     *
     * @param name name to check
     * @return true iff it's a valid XML element name
     */
    public static boolean isValidXmlElementName(String name) {
        return REGEX_VALID_XML_ELEMENT_NAME.matcher(name).matches();
    }

    /**
     * States of the xmlToPlainText() state machine
     */
    private enum XmlToPlainTextState {

        /** Copy these characters to the destination */
        COPY,

        /** We're inside a tag; don't copy */
        IN_TAG,

        /** We're inside an entity; don't copy, but add appropriate character at end */
        IN_ENTITY,
    }

    /**
     * Takes an XML input string and... * removes tags * replaces entities with
     * characters * normalizes whitespace * (optionally) replaces spaces with
     * non-breaking spaces
     *
     * @param conc the input XML string
     * @param makeNonBreaking if true, the output string only contains non-breaking
     *            spaces
     * @return the plain text output string
     */
    public static String xmlToPlainText(String conc, boolean makeNonBreaking) {
        // Allocate buffer.
        int inputLength = conc.length();
        char[] src = new char[inputLength];

        // Get character array
        conc.getChars(0, inputLength, src, 0);

        // Loop through character array
        int dstIndex = 0;
        XmlToPlainTextState state = XmlToPlainTextState.COPY;
        int entityStart = -1;
        char space = ' ';
        if (makeNonBreaking)
            space = StringUtil.CHAR_NON_BREAKING_SPACE; // Non-breaking space (codepoint 160)
        boolean lastCopiedASpace = false; // To normalize whitespace
        for (int srcIndex = 0; srcIndex < inputLength; srcIndex++) {
            char c = src[srcIndex];
            switch (c) {
            case '<':
                // Entering tag
                state = XmlToPlainTextState.IN_TAG;
                break;

            case '>':
                // Leaving tag, back to copy
                state = XmlToPlainTextState.COPY;
                break;

            case '&':
                // Entering entity (NOTE: ignore entities if we're inside a tag)
                if (state != XmlToPlainTextState.IN_TAG) {
                    // Go to entity state
                    state = XmlToPlainTextState.IN_ENTITY;
                    entityStart = srcIndex + 1;
                }
                break;

            case ';':
                if (state == XmlToPlainTextState.IN_ENTITY) {
                    // Leaving entity
                    char whichEntity;
                    String entityName = conc.substring(entityStart, srcIndex);
                    if (entityName.equals("lt"))
                        whichEntity = '<';
                    else if (entityName.equals("gt"))
                        whichEntity = '>';
                    else if (entityName.equals("amp"))
                        whichEntity = '&';
                    else if (entityName.equals("quot"))
                        whichEntity = '"';
                    else if (entityName.startsWith("#x")) {
                        // Hex entity
                        whichEntity = (char) Integer.parseInt(entityName.substring(2), 16);
                    } else if (!entityName.isEmpty() && entityName.charAt(0) == '#') {
                        // Decimal entity
                        whichEntity = (char) Integer.parseInt(entityName.substring(1), 10);
                    } else {
                        // Unknown entity!
                        whichEntity = '?';
                    }

                    // Put character in destination buffer
                    src[dstIndex] = whichEntity;
                    dstIndex++;
                    lastCopiedASpace = false; // should be: whichEntity == ' ' || ...

                    // Back to copy state
                    state = XmlToPlainTextState.COPY;
                } else if (state == XmlToPlainTextState.COPY) {
                    // Not in entity or tag, just copy character
                    src[dstIndex] = c;
                    dstIndex++;
                    lastCopiedASpace = false;
                }
                // else: inside tag, ignore all characters until end of tag
                break;

            case ' ':
            case '\t':
            case '\n':
            case '\r':
            case '\u00A0':
                if (state == XmlToPlainTextState.COPY && !lastCopiedASpace) {
                    // First space in a run; copy it
                    src[dstIndex] = space;
                    dstIndex++;
                    lastCopiedASpace = true;
                }
                break;

            default:
                if (state == XmlToPlainTextState.COPY) {
                    // Copy character
                    src[dstIndex] = c;
                    dstIndex++;
                    lastCopiedASpace = false;
                }
                break;
            }
        }

        return new String(src, 0, dstIndex);
    }

}
