package nl.inl.blacklab.search.indexmetadata;

import org.apache.commons.lang3.StringUtils;

/**
 * Some utility functions for dealing with annotated field names.
 */
public final class AnnotatedFieldNameUtil {

    /** Used in integrated index format to store content in Lucene doc */
    public static final String BOOKKEEP_CONTENT_STORE = "cs";

    /** Records the length of the annotated field in number of tokens
     *  (could also be read from the forward index - maybe drop this field in the future?)
     */
    public static final String BOOKKEEP_LENGTH_TOKENS = "length_tokens";

    public static final String RELATIONS_ANNOT_NAME = "_relation";

    /** Annotation name for the spaces and punctuation between words */
    public static final String PUNCTUATION_ANNOT_NAME = "punct";

    /**
     * Subannotations are indexed in a separate field, prefixed with their main
     * annotation name, so a subannotation of "pos" named "number" will be indexed
     * in a field "pos_number". This is the separator used for this prefix.
     */
    public static final String SUBANNOTATION_FIELD_PREFIX_SEPARATOR = "_";

    /** For parallel corpora, this separator goes between the field name and the version name, e.g.
     *  contents__nl, contents__de, etc.
     */
    public static final String PARALLEL_VERSION_SEPARATOR = "__";

    /**
     * String used to separate the base field name (say, contents) and the field
     * annotation (pos, lemma, etc.)
     */
    public static final String ANNOT_SEP = "%";

    /**
     * String used to separate the field/annotation name (say, contents_lemma) and the
     * alternative (e.g. "s" for case-sensitive)
     */
    static final String SENSITIVITY_SEP = "@";

    /**
     * String used to separate the field/annotation name (say, contents_lemma) and the
     * alternative (e.g. "s" for case-sensitive)
     */
    static final String BOOKKEEPING_SEP = "#";

    private AnnotatedFieldNameUtil() {
    }

    public static String contentStoreField(String fieldName) {
        return bookkeepingField(fieldName, BOOKKEEP_CONTENT_STORE);
    }

    public static String lengthTokensField(String fieldName) {
        return bookkeepingField(fieldName, BOOKKEEP_LENGTH_TOKENS);
    }

    /**
     * Construct Lucene field name for annotated field bookkeeping subfield.
     *
     * @param fieldName the base field name
     * @param bookkeepName name of the bookkeeping value
     * @return the Lucene field name
     */
    static String bookkeepingField(String fieldName, String bookkeepName) {
        if (fieldName == null || fieldName.isEmpty()) {
            throw new IllegalArgumentException("Must specify a base field name: " + fieldName
                    + ", " + bookkeepName);
        }
        if (bookkeepName == null || bookkeepName.isEmpty())
            return fieldName;
        return fieldName + BOOKKEEPING_SEP + bookkeepName;
    }

    /**
     * Construct Lucene field name for annotation on annotated field.
     *
     * @param fieldName the base field name
     * @param annotName the annotation name
     * @param sensitivity the sensitivity
     * @return the Lucene field name
     */
    public static String luceneName(String fieldName, String annotName, MatchSensitivity sensitivity) {
        if (fieldName == null || annotName == null || sensitivity == null) {
            throw new IllegalArgumentException("All parameters must be specified: " + fieldName + ", " + annotName + ", " + sensitivity);
        }
        return fieldName + ANNOT_SEP + annotName + SENSITIVITY_SEP + sensitivity.luceneFieldSuffix();
    }

    /**
     * Gets the different components of a annotated field annotation (alternative) name.
     *
     * @param luceneFieldName the Lucene index field name, with possibly a annotation
     *            and/or alternative added
     * @return an array of size 1-4, containing the field name, and optionally the
     *         annotation name, sensitivity name and bookkeeping field name.
     *
     *         Annotation name may be null if this is a main bookkeeping field.
     *         Sensitivity name may be null if this is a bookkeeping field.
     */
    public static String[] getNameComponents(String luceneFieldName) {

        /*
        
        Field names can be one of the following:
        
        (1) Annotation:              base%annot (e.g. word, lemma, pos)
        (2) Main bookkeeping:        base#cs
        (3) Annotation bookkeeping:  base%annot#fiid
        (4) Annotation sensitivity:  base%annot@sensitivity (e.g. base%lemma@i)
        
        */

        String baseName, annotName, sensitivityName, bookkeepingName;

        int annotSepPos = luceneFieldName.indexOf(ANNOT_SEP);
        int altSepPos = luceneFieldName.indexOf(SENSITIVITY_SEP);
        int bookkeepingSepPos;
        bookkeepingSepPos = luceneFieldName.indexOf(BOOKKEEPING_SEP);

        // Strip off annotation and possible alternative
        if (annotSepPos >= 0) {
            // Annotation given (1/3/4)
            baseName = luceneFieldName.substring(0, annotSepPos);
            int afterAnnotSepPos = annotSepPos + ANNOT_SEP.length();

            if (altSepPos >= 0) {
                // Annotation and sensitivity given (4)
                annotName = luceneFieldName.substring(afterAnnotSepPos, altSepPos);
                sensitivityName = luceneFieldName.substring(altSepPos + SENSITIVITY_SEP.length());
                return new String[] { baseName, annotName, sensitivityName };
            }

            // Maybe it's a bookkeeping field?
            if (bookkeepingSepPos >= 0 && bookkeepingSepPos > annotSepPos) {
                // Annotation plus bookkeeping subfield given. (3)
                annotName = luceneFieldName.substring(afterAnnotSepPos, bookkeepingSepPos);
                bookkeepingName = luceneFieldName.substring(bookkeepingSepPos + BOOKKEEPING_SEP.length());
                return new String[] { baseName, annotName, null, bookkeepingName };
            }

            // Plain annotation, no annotationsensitivity or bookkeeping (1)
            annotName = luceneFieldName.substring(afterAnnotSepPos);
            return new String[] { baseName, annotName };
        }

        // No annotation given. Alternative?
        if (altSepPos >= 0) {
            throw new IllegalArgumentException("Basename+altname is not a valid field name! (" + luceneFieldName + ")");
        }

        if (bookkeepingSepPos >= 0) {
            // Main bookkeeping field (2)
            baseName = luceneFieldName.substring(0, bookkeepingSepPos);
            sensitivityName = luceneFieldName.substring(bookkeepingSepPos + BOOKKEEPING_SEP.length());
            return new String[] { baseName, null, null, sensitivityName };
        }

        // Just the base name given (A).
        // This means it's a metadata field.
        return new String[] { luceneFieldName };
    }

    /**
     * Gets the base annotated field name from a Lucene index field name. So
     * "contents" and "contents%pos" would both yield "contents".
     *
     * @param luceneFieldName the Lucene index field name, with possibly a annotation
     *            added
     * @return the base annotated field name
     */
    public static String getBaseName(String luceneFieldName) {
        // Strip off annotation and possible alternative
        int pos = luceneFieldName.indexOf(ANNOT_SEP);
        if (pos >= 0) {
            return luceneFieldName.substring(0, pos);
        }
        pos = luceneFieldName.indexOf(BOOKKEEPING_SEP);
        if (pos >= 0) {
            return luceneFieldName.substring(0, pos);
        }
        pos = luceneFieldName.indexOf(SENSITIVITY_SEP);
        if (pos >= 0) {
            throw new IllegalArgumentException("Illegal field name: " + luceneFieldName);
        }
        return luceneFieldName;
    }

    public static MatchSensitivity sensitivity(String luceneField) {
        int i = luceneField.indexOf(SENSITIVITY_SEP);
        if (i < 0)
            throw new IllegalArgumentException("luceneField contains no " + SENSITIVITY_SEP);
        return MatchSensitivity.fromLuceneFieldSuffix(luceneField.substring(i + 1));
    }

    /**
     * Is the Lucene field name part of an annotated field or not?
     * 
     * @param luceneFieldName the field name
     * @return true if it's part of an annotated field, false if not
     */
    public static boolean isAnnotatedField(String luceneFieldName) {
        return luceneFieldName.contains(ANNOT_SEP) || luceneFieldName.contains(SENSITIVITY_SEP);
    }

    /**
     * Check if the field name is a parallel field name.
     * <p>
     * Parallel field names have a suffix with a version code, e.g. "contents__nl" or "contents__de".
     *
     * @param fieldName field name to check
     * @return true if it's a parallel field name, false if not
     */
    public static boolean isParallelField(String fieldName) {
        return fieldName.contains(PARALLEL_VERSION_SEPARATOR);
    }

    /**
     * Split a parallel field name into the field base name and version.
     * <p>
     * If the field name doesn't have a version suffix, the second element in the
     * returned array will be empty.
     * <p>
     * Examples:
     * - "contents__de" will return ["contents", "de"]
     * - "contents" will return ["contents", ""]
     *
     * @param fieldName field name to split
     * @return array with two elements: field base name and version
     */
    private static String[] splitParallelFieldName(String fieldName) {
        // Careful not to interpret special relation class name __tag as a parallel version!
        if (fieldName.contains(PARALLEL_VERSION_SEPARATOR) && !fieldName.startsWith(PARALLEL_VERSION_SEPARATOR))
            return fieldName.split(PARALLEL_VERSION_SEPARATOR, 2);
        else
            return new String[] { fieldName, "" };
    }


    /**
     * Get the field base name from a parallel field name.
     * <p>
     * If the field name doesn't have a version suffix, the
     * fieldName is returned unchanged.
     * <p>
     * Examples:
     * - "contents__de" will return "contents"
     * - "contents" will return "contents"
     *
     * @param fieldName field name to split
     * @return field base name
     */
    public static String baseFromParallelFieldName(String fieldName) {
        return splitParallelFieldName(fieldName)[0];
    }


    /**
     * Get the field base name from a parallel field name.
     * <p>
     * If the field name doesn't have a version suffix, the
     * empty string is returned.
     * <p>
     * Examples:
     * - "contents__de" will return "de"
     * - "contents" will return ""
     *
     * @param fieldName field name to split
     * @return version
     */
    public static String versionFromParallelFieldName(String fieldName) {
        return splitParallelFieldName(fieldName)[1];
    }

    /**
     * Get the parallel field name for a field name and version.
     *
     * @param baseName field base name
     * @param version version
     * @return parallel field name
     */
    public static String getParallelFieldName(String baseName, String version) {
        return baseName + PARALLEL_VERSION_SEPARATOR + version;
    }

    /**
     * Get a different version of the given parallel field name.
     * <p>
     * Examples:
     * - getParallelFieldVersion("contents__de", "nl") will return "contents__nl"
     * - getParallelFieldVersion("contents", "de") will return "contents__de"
     * - getParallelFieldVersion("contents", "") will return "contents"
     *
     * @param parallelFieldName a parallel field name with a version suffix
     * @param newVersion version to substitute
     * @return the parallel field name with the indicated version
     */
    public static String changeParallelFieldVersion(String parallelFieldName, String newVersion) {
        if (StringUtils.isEmpty(newVersion))
            return parallelFieldName;
        return baseFromParallelFieldName(parallelFieldName) + PARALLEL_VERSION_SEPARATOR + newVersion;
    }

}
