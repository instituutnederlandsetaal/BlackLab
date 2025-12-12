package nl.inl.blacklab.plugins;

import nl.inl.blacklab.exceptions.InvalidConfiguration;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.index.IndexSource;

/** A source for documents to index, such as the file system,
 * a database, or a web service. */
public abstract class IndexSourceType extends Plugin {

    private static final String PROTOCOL_SEPARATOR = "//:"; // avoid matching random ':' or windows pathnames.

    public static IndexSourceType forScheme(String id) {
        if (id.isEmpty())
            id = "file"; // default
        try {
            return PluginManager.type(IndexSourceType.class).get(id);
        } catch (PluginException e) {
            throw new InvalidConfiguration("Error finding index source type: " + id, e);
        }
    }

    public static IndexSource fromUri(String uri) {
        int index = uri.indexOf(PROTOCOL_SEPARATOR);
        String scheme = index >= 0 ? uri.substring(0, index) : "";
        String path = index >= 0 ? uri.substring(index + PROTOCOL_SEPARATOR.length()) : uri;
        IndexSourceType indexSourceType = forScheme(scheme);
        if (indexSourceType == null) {
            throw new IllegalArgumentException("Unknown input URI scheme: " + uri);
        }
        // Create an instance of the appropriate IndexSource subclass
        return indexSourceType.get(path);
    }

    /** Get the resources indicated by the path from our source. */
    public abstract IndexSource get(String path);

}
