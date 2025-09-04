package nl.inl.blacklab.codec;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.store.IndexInput;

import net.jcip.annotations.NotThreadSafe;
import net.jcip.annotations.ThreadSafe;
import nl.inl.blacklab.codec.tokens.TokensCodec;
import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.forwardindex.FieldForwardIndex;
import nl.inl.blacklab.forwardindex.ForwardIndexImpl;
import nl.inl.blacklab.forwardindex.ForwardIndexSegmentReader;
import nl.inl.blacklab.forwardindex.Terms;

/**
 * Manages read access to forward indexes for a single segment.
 */
@ThreadSafe
public class ForwardIndex implements AutoCloseable {

    /** Tokens index file record consists of:
     * - offset in tokens file (long),
     * - doc length in tokens (int), 
     * - tokens codec scheme (byte),
     * - tokens codec parameter (byte)
     */
    private static final long TOKENS_INDEX_RECORD_SIZE = (long)Long.BYTES + Integer.BYTES + Byte.BYTES + Byte.BYTES;

    /** Our fields producer */
    private final BlackLabPostingsReader fieldsProducer;

    /** Contains field names and offsets to term index file, where the terms for the field can be found */
    private final Map<String, ForwardIndexField> fieldsByName = new LinkedHashMap<>();


    /** Contains indexes into the tokens file for all field and documents */
    private IndexInput _tokensIndexFile;

    /** Contains the tokens for all fields and documents */
    private IndexInput _tokensFile;

    public ForwardIndex(BlackLabPostingsReader postingsReader) throws IOException {
        this.fieldsProducer = postingsReader;

        try (IndexInput fieldsFile = postingsReader.openIndexFile(BlackLabPostingsFormat.FIELDS_EXT)) {
            long size = fieldsFile.length();
            while (fieldsFile.getFilePointer() < (size - CodecUtil.footerLength())) {
                ForwardIndexField f = new ForwardIndexField(fieldsFile);
                this.fieldsByName.put(f.getFieldName(), f);
            }
        }

        _tokensIndexFile = postingsReader.openIndexFile(BlackLabPostingsFormat.TOKENS_INDEX_EXT);
        _tokensFile = postingsReader.openIndexFile(BlackLabPostingsFormat.TOKENS_EXT);
    }

    @Override
    public void close() {
        try {
            _tokensFile.close();
            _tokensIndexFile.close();
            _tokensIndexFile = _tokensFile = null;
        } catch (IOException e) {
            throw new InvalidIndex(e);
        }
    }

    /**
     * Get a new FieldForwardIndex on this segment.
     * Though the reader is not Threadsafe, a new instance is returned every time,
     * So this function can be used from multiple threads.
     */
    public AnnotationForwardIndex forField(String luceneField) {
        Reader reader;
        // Synchronized because it clones our IndexInputs
        synchronized (this) {
            reader = new Reader();
        }
        return new FieldForwardIndex(reader, fieldsByName.get(luceneField));
    }

    public ForwardIndexField getForwardIndexField(String luceneField) {
        return fieldsByName.get(luceneField);
    }

    /**
     * A forward index reader for a single segment.
     *
     * This can be used by a single thread to read from a forward index segment.
     * Not thread-safe because it contains state (file pointers, doc offset/length).
     */
    @NotThreadSafe
    public class Reader implements ForwardIndexSegmentReader {

        private final IndexInput _tokensIndex;

        private final IndexInput _tokens;

        // Used by retrievePart(s)
        private long docTokensOffset;

        // Used by retrievePart(s)
        private int docLength;

        // Used by retrievePart(s)
        private TokensCodec tokensCodec;

        // to be decoded by the appropriate tokensCodec
        private byte tokensCodecParameter;

        Reader() {
            _tokensIndex = _tokensIndexFile.clone();
            _tokens = _tokensFile.clone();
        }

        /** Retrieve parts of a document from the forward index. */
        @Override
        public int[][] retrieveParts(ForwardIndexField field, int docId, int[] starts, int[] ends) {
            int n = starts.length;
            if (n != ends.length)
                throw new IllegalArgumentException("start and end must be of equal length");

            getDocOffsetAndLength(field, docId);

            // We don't exclude the closing token here because we didn't do that with the external index format either.
            // And you might want to fetch the extra closing token.
            //docLength -= BlackLabIndexAbstract.IGNORE_EXTRA_CLOSING_TOKEN;
            return retrieveParts(starts, ends);
        }

        /** Retrieve parts of a document from the forward index. */
        @Override
        public int[] retrievePart(ForwardIndexField field, int docId, int start, int end) {
            // ensure both inputs available
            getDocOffsetAndLength(field, docId);
            // We don't exclude the closing token here because we didn't do that with the external index format either.
            // And you might want to fetch the extra closing token.
            //docLength -= BlackLabIndexAbstract.IGNORE_EXTRA_CLOSING_TOKEN;
            return retrieveParts(field, docId, new int[] { start }, new int[] { end })[0];
        }

        private int[][] retrieveParts(int[] starts, int[] ends) {
            for (int i = 0; i < starts.length; i++) {
                if (starts[i] == -1)
                    starts[i] = 0;
                if (ends[i] == -1 || ends[i] > docLength) // Can happen while making KWICs because we don't know the doc length until here
                    ends[i] = docLength;
                ForwardIndexImpl.validateSnippetParameters(docLength, starts[i], ends[i]);
            }

            // Read the snippets from the tokens file
            try {
                return tokensCodec.readSnippets(_tokens, docTokensOffset, starts, ends);
            } catch (IOException e) {
                throw new InvalidIndex(e);
            }
        }

        private void getDocOffsetAndLength(ForwardIndexField field, int docId)  {
            try {
                long fieldTokensIndexOffset = field.getTokensIndexOffset();
                _tokensIndex.seek(fieldTokensIndexOffset + (long) docId * TOKENS_INDEX_RECORD_SIZE);
                docTokensOffset = _tokensIndex.readLong();
                docLength = _tokensIndex.readInt();
                tokensCodec = TokensCodec.fromHeader(_tokensIndex);
            } catch (IOException e) {
                throw new InvalidIndex(e);
            }
        }

        /** Get length of document in tokens from the forward index.
         *
         * This includes the "extra closing token" at the end, so subtract one for the real length.
         *
         * @param field lucene field to read forward index from
         * @param docId segment-local docId of document to get length for
         * @return doc length
         */
        @Override
        public long docLength(ForwardIndexField field, int docId) {
            getDocOffsetAndLength(field, docId);
            return docLength;
        }

        @Override
        public synchronized Terms terms(ForwardIndexField field) {
            return field.getTerms(fieldsProducer).reader();
        }
    }
}
