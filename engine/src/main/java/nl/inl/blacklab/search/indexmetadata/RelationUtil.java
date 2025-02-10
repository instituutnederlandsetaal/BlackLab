package nl.inl.blacklab.search.indexmetadata;

import nl.inl.blacklab.search.QueryExecutionContext;

public class RelationUtil {

    /** Relation class used for inline tags. Deliberately obscure to avoid collisions with "real" relations. */
    public static final String CLASS_INLINE_TAG = "__tag";

    /** Relation class to use for relations if not specified (both during indexing and searching). */
    public static final String CLASS_DEFAULT = "rel";

    /** Relation class to use for dependency relations (by convention). */
    public static final String CLASS_DEPENDENCY = "dep";

    /** Relation class to use for alignment relations in parallel corpus (by convention).
     * Note that this will be suffixed with the target version, e.g. "al__de" for an alignment
     * relation to the field "contents__de".
     */
    @SuppressWarnings("unused")
    public static final String CLASS_ALIGNMENT = "al";

    /** Default relation type: any */
    public static final String ANY_TYPE_REGEX = ".+";

    /** Separator between relation class (e.g. "__tag", "dep" for dependency relation, etc.) and relation type
     *  (e.g. "s" for sentence tag, or "nsubj" for dependency relation "nominal subject") */
    public static final String CLASS_TYPE_SEPARATOR = "::";

    public static boolean isFullType(String relationType) {
        return relationType.contains(CLASS_TYPE_SEPARATOR);
    }

    /**
     * Split a full relation type into relation class and relation type.
     * <p>
     * Relations are indexed with a full type, consisting of a relation class and a relation type.
     * The class is used to distinguish between different groups of relations, e.g. inline tags
     * and dependency relations.
     *
     * @param fullRelationType full relation type
     * @return relation class and relation type
     */
    private static String[] classAndType(String fullRelationType) {
        int sep = fullRelationType.indexOf(CLASS_TYPE_SEPARATOR);
        if (sep < 0)
            return new String[] { "", fullRelationType };
        return new String[] {
                fullRelationType.substring(0, sep),
                fullRelationType.substring(sep + CLASS_TYPE_SEPARATOR.length())
        };
    }

    /**
     * Get the relation class from a full relation type [regex]
     * <p>
     * Relations are indexed with a full type, consisting of a relation class and a relation type.
     * The class is used to distinguish between different groups of relations, e.g. inline tags
     * and dependency relations.
     *
     * @param fullRelationType full relation type
     * @return relation class
     */
    public static String classFromFullType(String fullRelationType) {
        return classAndType(fullRelationType)[0];
    }


    /**
     * Get the relation type from a full relation type [regex]
     * <p>
     * Relations are indexed with a full type, consisting of a relation class and a relation type.
     * The type is used to distinguish between different types of relations within a class.
     *
     * @param fullRelationType full relation type
     * @return relation type
     */
    public static String typeFromFullType(String fullRelationType) {
        return classAndType(fullRelationType)[1];
    }

    /**
     * Optionally surround a regular expression with parens.
     *
     * Necessary when concatenating certain regexes, e.g. ones that use the "|" operator.
     *
     * If it is already parenthesized, or clearly doesn't need parens
     * (simple regexes like e.g. ".*" or "hello"), leave it as is.
     *
     * Not very smart, but it doesn't hurt to add parens.
     *
     * @param regex regular expression
     * @return possibly parenthesized regex
     */
    public static String optParRegex(String regex) {
        if (regex.startsWith("(") && regex.endsWith(")") || regex.matches("\\.[*+?]|\\w+"))
            return regex;
        return "(" + regex + ")";
    }

    /**
     * Get the full relation type for a relation class and relation type regex.
     *
     * @param relClass relation class regex, e.g. "dep" for dependency relations
     * @param type relation type regex, e.g. "nsubj" for a nominal subject
     * @return full relation type regex
     */
    public static String fullTypeRegex(String relClass, String type) {
        return optParRegex(relClass) + CLASS_TYPE_SEPARATOR + optParRegex(type);
    }

    /**
     * Get the full relation type for a relation class and relation type.
     *
     * @param relClass relation class, e.g. "dep" for dependency relations
     * @param type relation type, e.g. "nsubj" for a nominal subject
     * @return full relation type
     */
    public static String fullType(String relClass, String type) {
        return relClass + CLASS_TYPE_SEPARATOR + type;
    }

    /** If the relationtype regex doesn't specify a relation class yet, prepend the default class to it. */
    public static String optPrependDefaultClass(String relationTypeRegex, QueryExecutionContext context) {
        if (!relationTypeRegex.contains(CLASS_TYPE_SEPARATOR)) {
            String defaultClass = context.resolveDefaultRelationClass();
            relationTypeRegex = fullTypeRegex(defaultClass, optParRegex(relationTypeRegex));
        }
        return relationTypeRegex;
    }
}
