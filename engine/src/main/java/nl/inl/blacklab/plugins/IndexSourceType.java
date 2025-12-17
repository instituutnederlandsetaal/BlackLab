package nl.inl.blacklab.plugins;

import nl.inl.blacklab.exceptions.InvalidConfiguration;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.index.IndexSource;

/** A source for documents to index, such as the file system,
 * a database, or a web service. */
public abstract class IndexSourceType extends Plugin {

    private static final String PROTOCOL_SEPARATOR = ":"; // avoid matching random ':' or windows pathnames.

    public static IndexSourceType forScheme(String id) {
        if (id.isEmpty())
            id = "file"; // default
        try {
            return PluginManager.type(IndexSourceType.class).get(id);
        } catch (PluginException e) {
            throw new InvalidConfiguration("Error finding index source type: " + id, e);
        }
    }

    private static boolean schemeExists(String scheme) {
        return PluginManager.type(IndexSourceType.class).exists(scheme);
    }

    /** Parse an index source URI and return the scheme and path separately.
     *
     * @param uri the URI
     * @return an array with [scheme, path]
     */
    public static String[] parseUri(String uri) {
        int index = uri.indexOf(PROTOCOL_SEPARATOR);
        String scheme = index >= 0 ? uri.substring(0, index) : "";
        if (!schemeExists(scheme))
            return new String[] { "", uri }; // no scheme, path might contain a colon (e.g. Windows path)
        String path = index >= 0 ? uri.substring(index + PROTOCOL_SEPARATOR.length()) : uri;
        return new String[] { scheme, path };
    }

    public static IndexSource fromUri(String uri) {
        String[] parts = parseUri(uri);
        String scheme = parts[0];
        String path = parts[1];
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
