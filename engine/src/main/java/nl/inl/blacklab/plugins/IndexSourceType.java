package nl.inl.blacklab.plugins;

import nl.inl.blacklab.exceptions.InvalidConfiguration;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.index.IndexSource;
import nl.inl.blacklab.plugins.param.PluginParams;

/** A source for documents to index, such as the file system,
 * a database, or a web service. */
public abstract class IndexSourceType extends Plugin {

    /** Separator between the scheme and the path in a URI */
    private static final String URI_PROTOCOL_SEPARATOR = ":";

    /** Optional double slash after the colon in a URI */
    private static final String URI_DOUBLE_SLASH = "//";

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
        int schemeEndIndex = uri.indexOf(URI_PROTOCOL_SEPARATOR);
        String scheme = schemeEndIndex >= 0 ? uri.substring(0, schemeEndIndex) : "";
        if (!schemeExists(scheme))
            return new String[] { "", uri }; // no scheme, path might contain a colon (e.g. Windows path)
        int pathStartIndex = schemeEndIndex >= 0 ? schemeEndIndex + URI_PROTOCOL_SEPARATOR.length() : 0;
        if (schemeEndIndex >= 0 && uri.startsWith(URI_DOUBLE_SLASH, pathStartIndex))
            pathStartIndex += URI_DOUBLE_SLASH.length();
        String path = uri.substring(pathStartIndex);
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
        return indexSourceType.get(path, PluginParams.NONE);
    }

    /** Get the resources indicated by the path from our source. */
    public abstract IndexSource get(String path, PluginParams params);

}
