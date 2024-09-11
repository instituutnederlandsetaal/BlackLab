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
    boolean startField(FieldInfo fieldInfo);

    void endField() throws IOException;

    void startTerm(BytesRef term) throws IOException;

    void endTerm();

    void startDocument(int docId, int nOccurrences);

    void endDocument() throws IOException;

    void termOccurrence(int position, BytesRef payload) throws IOException;

    void finish() throws IOException;

    @Override
    void close() throws IOException;
}
