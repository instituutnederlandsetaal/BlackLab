package nl.inl.blacklab;

/**
 * Constant values used in various places throughout the project.
 */
public class Constants {

    /** Utility class, don't instantiate */
    private Constants() {}

    /** The value to use meaning "no term" (e.g. if the document ends) */
    public static final int NO_TERM = -1;

    /**
     * Safe maximum size for a Java array.
     *
     * This is JVM-dependent, but the consensus seems to be that
     * this is a safe limit. See e.g.
     * https://stackoverflow.com/questions/3038392/do-java-arrays-have-a-maximum-size
     */
    public static final int JAVA_MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;
    /**
     * Safe maximum size for a Java HashMap.
     *
     * This is JVM dependent, but the consensus seems to be that
     * this is a safe limit. See e.g.
     * https://stackoverflow.com/questions/25609840/java-hashmap-max-size-of-5770/25610054
     */
    public static final int JAVA_MAX_HASHMAP_SIZE = Integer.MAX_VALUE / 4;

    /**
     * Safe maximum size for a Java Set.
     */
    public static final long JAVA_MAX_SET_SIZE = JAVA_MAX_HASHMAP_SIZE;

    /** Key in Solr response that contains the BlackLab response
        (also used by the proxy to retrieve the BlackLab response from the Solr response) */
    public static final String SOLR_BLACKLAB_SECTION_NAME = "blacklab";

    /** The maximum length for a token Lucene will accept */
    public static final int MAX_LUCENE_VALUE_LENGTH = 32766;
}
