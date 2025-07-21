package nl.inl.blacklab.contentstore;

import java.util.HashMap;
import java.util.Map;

import nl.inl.blacklab.search.ContentAccessor;
import nl.inl.blacklab.search.indexmetadata.Field;

public class ContentStoresManager {
    /**
     * ContentAccessors tell us how to get a field's content:
     * <ol>
     * <li>if there is no contentaccessor: get it from the Lucene index (stored
     * field)</li>
     * <li>from an external source (file, database) if it's not (because the content
     * is very large and/or we want faster random access to the content than a
     * stored field can provide)</li>
     * </ol>
     *
     * Indexed by annotated field name.
     */
    private final Map<Field, ContentAccessor> contentAccessors = new HashMap<>();

    public void close() {
        // Close the content accessor(s)
        // (the ContentStore, and possibly other content accessors
        // (although that feature is not used right now))
        for (ContentAccessor ca : contentAccessors.values()) {
            ca.close();
        }

    }

    public void put(Field field, ContentStore store) {
        contentAccessors.put(field, new ContentAccessor(field, store));
    }

    public ContentAccessor contentAccessor(Field field) {
        return contentAccessors.get(field);
    }


}
