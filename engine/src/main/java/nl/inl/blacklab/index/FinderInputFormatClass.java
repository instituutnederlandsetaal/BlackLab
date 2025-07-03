package nl.inl.blacklab.index;

import java.util.HashMap;
import java.util.Map;

import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;

/**
 * Supports creation of several types of DocIndexers implemented directly in
 * code. Additionally will attempt to load classes if passed a fully-qualified
 * ClassName, and implementations by name in .indexers package within BlackLab.
 */
public class FinderInputFormatClass implements FinderInputFormat {

    @Override
    public InputFormat find(String formatIdentifier) {
        // Is it a fully qualified class name?
        Class<? extends DocIndexerLegacy> docIndexerClass = null;
        docIndexerClass = getLegacyDocIndexers().get(formatIdentifier);
        return docIndexerClass == null ? null : DocumentFormats.add(formatIdentifier, docIndexerClass);
    }

    private static Map<String, Class<? extends DocIndexerLegacy>> legacyDocIndexers = null;

    /**
     * Find all legacy DocIndexers and store them in a map.
     * @return a map of format identifiers to DocIndexerLegacy classes
     */
    private static synchronized Map<String, Class<? extends  DocIndexerLegacy>> getLegacyDocIndexers() {
        if (legacyDocIndexers == null) {
            legacyDocIndexers = new HashMap<>();
            Reflections reflections = new Reflections("", new SubTypesScanner(false));
            for (Class<? extends  DocIndexerLegacy> cl: reflections.getSubTypesOf(DocIndexerLegacy.class)) {
                String qualifiedName = cl.getName();
                legacyDocIndexers.put(qualifiedName, cl);
                if (qualifiedName.startsWith("nl.inl.blacklab.indexers."))
                    legacyDocIndexers.put(cl.getSimpleName(), cl);
            }
        }
        return legacyDocIndexers;
    }

}
