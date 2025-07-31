package nl.inl.blacklab.codec;

import java.io.IOException;

import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.util.BytesRef;

/**
 * Interface for hooking into the process of writing postings to the forward index.
 *
 * Used to write the forward index and relation info.
 */
interface PWPlugin extends AutoCloseable {

    /** Start processing a new Lucene field */
    boolean startField(FieldInfo fieldInfo);

    /** Start processing a new term in the current field */
    void startTerm(BytesRef term) throws IOException;

    /** Start a new document for the current term */
    void startDocument(int docId, int nOccurrences);

    /** Process an occurrence of the current term in the current document */
    void termOccurrence(int position, BytesRef payload) throws IOException;

    /** We're done with the current document (for the current term) */
    void endDocument() throws IOException;

    /** We're done with the current term */
    void endTerm();

    /** We're done with this Lucene field */
    void endField() throws IOException;

    /** Finish this segment, i.e. converting any temporary files to permanent ones */
    void finish() throws IOException;

    /** Close this plugin instance, releasing its resources */
    @Override
    void close() throws IOException;
}
