package nl.inl.blacklab.contentstore;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.apache.lucene.document.Document;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.util.TextContent;
import nl.inl.util.VersionFile;

/**
 * Store string content by integer id. Quickly retrieve (parts of) the string
 * content.
 */
public abstract class ContentStoreExternal implements ContentStore {

    static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    public static ContentStoreExternal open(File indexXmlDir, boolean indexMode, boolean create) throws ErrorOpeningIndex {
        String type;
        if (create)
            type = "fixedblock";
        else {
            VersionFile vf = ContentStoreDirAbstract.getStoreTypeVersion(indexXmlDir);
            type = vf.getType();
        }
        if (type.equals("fixedblock")) {
            if (indexMode)
                return new ContentStoreFixedBlockWriter(indexXmlDir, create);
            if (create)
                throw new UnsupportedOperationException("create == true, but not in index mode");
            return new ContentStoreFixedBlockReader(indexXmlDir);
        } else {
            throw new UnsupportedOperationException("Content store of type '" + type + "' is unknown (or no longer supported). Please re-index your data.");
        }
    }

    /**
     * Store a document.
     *
     * It is possible to first call storePart() several times, as long as you finish
     * with a call to store. The parameter may be the empty string if you wish.
     *
     * @param content (part of) the content of the document to store
     * @return the content store id assigned to the document
     */
    public abstract int store(TextContent content);


    /**
     * Store a document.
     *
     * It is possible to first call storePart() several times, as long as you finish
     * with a call to store. The parameter may be the empty string if you wish.
     *
     * @param content (part of) the content of the document to store
     * @return the content store id assigned to the document
     */
    public int store(String content) {
        return store(TextContent.from(content));
    }
    
    
    /**
     * Store part of a large document.
     *
     * You can call this several times, but it must end with a call to store() or
     * the document isn't properly stored.
     *
     * @param content part of the content of the document to store
     */
    public abstract void storePart(TextContent content);

    /**
     * Delete a document from the content store.
     * 
     * @param id content store id of the document to delete
     */
    public abstract void delete(int id);

    /**
     * Clear the entire content store.
     */
    public abstract void clear() throws IOException;

    /**
     * Returns the set of doc ids in the store. Note that the IDs of deleted
     * document are still returned by this method. Use isDeleted() to check.
     * 
     * @return the set of doc ids
     */
    public abstract Set<Integer> idSet();

    /**
     * Return true iff the entry with this id was deleted.
     * 
     * @param id the entry to check
     * @return true iff deleted
     */
    public abstract boolean isDeleted(int id);

    public abstract void initialize();

    @Override
    public int getContentId(int docId, Document d, String contentIdField) {
        // Classic external index format. Read the content store id field.
        String contentIdStr = d.get(contentIdField);
        if (contentIdStr == null)
            throw new BlackLabRuntimeException("Lucene document has no content id: " + d);
        return Integer.parseInt(contentIdStr);
    }

}
