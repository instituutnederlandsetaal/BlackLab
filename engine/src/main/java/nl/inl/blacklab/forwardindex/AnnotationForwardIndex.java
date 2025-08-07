package nl.inl.blacklab.forwardindex;

import java.util.List;

import org.apache.lucene.index.LeafReaderContext;

import net.jcip.annotations.ThreadSafe;
import nl.inl.blacklab.search.indexmetadata.Annotation;

/**
 * A forward index for a single annotation on a field.
 */
@ThreadSafe
public interface AnnotationForwardIndex {

    /**
     * Initialize this forward index (to be run in the background).
     */
    void initialize();

    /**
     * Retrieve one or more parts from the specified content, in the form of token
     * ids.
     *
     * This is more efficient than retrieving the whole content, or retrieving parts
     * in separate calls, because the file is only opened once and random access is
     * used to read only the required parts.
     *
     * NOTE: if offset and length are both -1, retrieves the whole content. This is
     * used by the retrieve(id) method.
     *
     * NOTE2: Mapped file IO on Windows has some issues that sometimes cause an
     * OutOfMemoryError on the FileChannel.map() call (which makes no sense, because
     * memory mapping only uses address space, it doesn't try to read the whole
     * file). Possibly this could be solved by using 64-bit Java, but we haven't
     * tried. For now we just disable memory mapping on Windows.
     *
     * @param docId Lucene document id
     * @param start the starting points of the parts to retrieve (in words) (-1 for
     *         start of document)
     * @param end the end points (i.e. first token beyond) of the parts to retrieve
     *         (in words) (-1 for end of document)
     * @return the parts
     */
    List<int[]> retrievePartsInt(int docId, int[] start, int[] end);

    List<int[]> retrievePartsIntSegment(LeafReaderContext lrc, int docId, int[] start, int[] end);

    /**
     * Convenience method for retrieving 1 part of a document.
     *
     * If you're retrieving multiple parts from the same document, it's always
     * faster to use retrievePartsInt() to batch them.
     *
     * @param docId Lucene document id
     * @param start start of the part to retrieve (in words) (-1 for start of document)
     * @param end   end of the part to retrieve (in words) (-1 for end of document)
     * @return the part
     */
    default int[] retrievePart(int docId, int start, int end) {
        return retrievePartsInt(docId, new int[] { start }, new int[] { end }).get(0);
    }

    /**
     * Retrieve token ids for the entire document.
     * @param docId forward index id
     * @return token ids for the entire document.
     */
    default int[] getDocument(int docId) {
        return retrievePart(docId, -1, -1);
    }

    /**
     * Get the Terms object in order to translate ids to token strings
     *
     * @return the Terms object
     */
    Terms terms();

    /**
     * Gets the length (in tokens) of a document.
     *
     * NOTE: this INCLUDES the extra closing token at the end of the document!
     *
     * @param docId Lucene document id
     * @return length of the document
     */
    int docLength(int docId);

    /**
     * The annotation for which this is the forward index
     *
     * @return annotation
     */
    Annotation annotation();

    boolean canDoNfaMatching();

    @Override
    String toString();

    Collators collators();

    ForwardIndex getParent();
}
