package nl.inl.blacklab.index;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;

/** A source for documents to index, such as a selection of files, records from a database, or resources from a web service. */
public abstract class IndexSource {

    private static final Logger logger = LogManager.getLogger(IndexSource.class);

    private static final String PROTOCOL_SEPARATOR = "://";

    private static Map<String, Class<? extends IndexSource>> indexSourceTypes;

    /**
     * Find all legacy DocIndexers and store them in a map.
     *
     * @return a map of format identifiers to DocIndexerLegacy classes
     */
    private static synchronized Map<String, Class<? extends IndexSource>> getIndexSourceTypes() {
        if (indexSourceTypes == null) {
            indexSourceTypes = new HashMap<>();
            Reflections reflections = new Reflections("nl.inl.blacklab", new SubTypesScanner(false));
            for (Class<? extends IndexSource> cl: reflections.getSubTypesOf(IndexSource.class)) {
                try {
                    // Get the URI_SCHEME constant from the class
                    String scheme = (String) cl.getField("URI_SCHEME").get(null);
                    indexSourceTypes.put(scheme, cl);
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    logger.error("Could not get URI_SCHEME constant from class {}, ", cl.getName());
                    logger.error(e);
                }
            }
            indexSourceTypes.put("", IndexSourceFile.class); // default
        }
        return indexSourceTypes;
    }

    public static IndexSource fromUri(String uri) {
        int index = uri.indexOf(PROTOCOL_SEPARATOR);
        String scheme = index >= 0 ? uri.substring(0, index) : "";
        String path = index >= 0 ? uri.substring(index + PROTOCOL_SEPARATOR.length()) : uri;
        Class<? extends IndexSource> indexSourceClass = getIndexSourceTypes().get(scheme);
        if (indexSourceClass == null) {
            throw new IllegalArgumentException("Unknown input URI scheme: " + uri);
        }
        // Create an instance of the appropriate IndexSource subclass
        try {
            return indexSourceClass.getConstructor(String.class).newInstance(path);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Error creating IndexSource for URI: " + uri, e);
        }
    }

    private final String uri;

    public IndexSource(String uri) {
        this.uri = uri;
    }

    public String getUri() {
        return uri;
    }

    /**
     * Get directory associated with this IndexSource; we will search it for format files.
     */
    public Optional<File> getAssociatedDirectory() {
        return Optional.empty();
    }

    @Override
    public String toString() {
        return uri;
    }

    public abstract void index(Indexer indexer);
}
