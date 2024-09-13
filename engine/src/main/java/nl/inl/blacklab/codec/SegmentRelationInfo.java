package nl.inl.blacklab.codec;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.store.IndexInput;

import net.jcip.annotations.NotThreadSafe;
import net.jcip.annotations.ThreadSafe;
import nl.inl.blacklab.codec.TokensCodec.VALUE_PER_TOKEN_PARAMETER;
import nl.inl.blacklab.forwardindex.ForwardIndexAbstract;
import nl.inl.blacklab.forwardindex.RelationInfoSegmentReader;
import nl.inl.blacklab.forwardindex.TermsSegmentReader;

/**
 * Manages read access to forward indexes for a single segment.
 */
@ThreadSafe
public class SegmentRelationInfo implements AutoCloseable {

    /** Tokens index file record consists of:
     * - offset in tokens file (long),
     * - doc length in tokens (int),
     * - tokens codec scheme (byte),
     * - tokens codec parameter (byte)
     */
    private static final long TOKENS_INDEX_RECORD_SIZE = Long.BYTES + Integer.BYTES + Byte.BYTES + Byte.BYTES;

    /** Our fields producer */
    private final BlackLabPostingsReader fieldsProducer;

    /** Contains field names and offsets to term index file, where the terms for the field can be found */
    private final Map<String, RelationInfoField> fieldsByName = new LinkedHashMap<>();


    /** Where relations for each doc can be found in relations file */
    private IndexInput _docsFile;

    /** Where attributes for each relation can be found in attrsets file */
    private IndexInput _relationsFile;

    /** Where name and value of attributes can be found */
    private IndexInput _attrSetsFile;

    /** Where name and value of attributes can be found */
    private IndexInput _attrValuesFile;

    /** Attribute names */
    private List<String> attributeNames = new ArrayList<>();

    public static SegmentRelationInfo openIfPresent(BlackLab40PostingsReader postingsReader) throws IOException {
        try (IndexInput fieldsFile = postingsReader.openIndexFile(BlackLabPostingsFormat.FIELDS_EXT)) {
            return new SegmentRelationInfo(postingsReader, fieldsFile);
        } catch (NoSuchFileException | FileNotFoundException e) {
            // No relation info stored; that's okay
            return null;
        }
    }

    private SegmentRelationInfo(BlackLabPostingsReader postingsReader, IndexInput fieldsFile) throws IOException {
        this.fieldsProducer = postingsReader;

        // Read fields file
        while (fieldsFile.getFilePointer() < (fieldsFile.length() - CodecUtil.footerLength())) {
            RelationInfoField f = new RelationInfoField(fieldsFile);
            this.fieldsByName.put(f.getFieldName(), f);
        }

        // Read attribute names file
        try (IndexInput attrNamesFile = postingsReader.openIndexFile(BlackLabPostingsFormat.RI_ATTR_NAMES_EXT)) {
            while (attrNamesFile.getFilePointer() < (attrNamesFile.length() - CodecUtil.footerLength())) {
                attributeNames.add(attrNamesFile.readString());
            }
        }

        _docsFile = postingsReader.openIndexFile(BlackLabPostingsFormat.RI_DOCS_EXT);
        _relationsFile = postingsReader.openIndexFile(BlackLabPostingsFormat.RI_RELATIONS_EXT);
        _attrSetsFile = postingsReader.openIndexFile(BlackLabPostingsFormat.RI_ATTR_SETS_EXT);
        _attrValuesFile = postingsReader.openIndexFile(BlackLabPostingsFormat.RI_ATTR_VALUES_EXT);
    }

    private synchronized IndexInput getCloneOfDocsFile() {
        // synchronized because clone() is not thread-safe
        return _docsFile.clone();
    }

    private synchronized IndexInput getCloneOfRelationsFile() {
        // synchronized because clone() is not thread-safe
        return _relationsFile.clone();
    }

    private synchronized IndexInput getCloneOfAttrSetsFile() {
        // synchronized because clone() is not thread-safe
        return _attrSetsFile.clone();
    }

    private synchronized IndexInput getCloneOfAttrValuesFile() {
        // synchronized because clone() is not thread-safe
        return _attrValuesFile.clone();
    }

    @Override
    public void close() {
        try {
            _docsFile.close();
            _relationsFile.close();
            _attrSetsFile.close();
            _attrValuesFile.clone();
            _docsFile = _relationsFile = _attrSetsFile = _attrValuesFile = null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** 
     * Get a new ForwardIndexSegmentReader on this segment. 
     * Though the reader is not Threadsafe, a new instance is returned every time, 
     * So this function can be used from multiple threads. 
     */
    public RelationInfoSegmentReader reader() {
        return new Reader();
    }

    /**
     * A forward index reader for a single segment.
     *
     * This can be used by a single thread to read from a forward index segment.
     * Not thread-safe because it contains state (file pointers, doc offset/length).
     */
    @NotThreadSafe
    public class Reader implements RelationInfoSegmentReader {

        private IndexInput _docs;

        private IndexInput _relations;

        private IndexInput _attrSets;

        private IndexInput _attrValues;

        private IndexInput docs() {
            if (_docs == null)
                _docs = getCloneOfDocsFile();
            return _docs;
        }

        private IndexInput relations() {
            if (_relations == null)
                _relations = getCloneOfRelationsFile();
            return _relations;
        }

        private IndexInput attrSets() {
            if (_attrSets == null)
                _attrSets = getCloneOfAttrSetsFile();
            return _attrSets;
        }

        private IndexInput attrValues() {
            if (_attrValues == null)
                _attrValues = getCloneOfAttrValuesFile();
            return _attrValues;
        }

        /** Retrieve parts of a document from a forward index.
         *
         * @param luceneField lucene field to retrieve snippet from
         * @param docId segment-local docId of document to retrieve snippet from
         * @param relationId relation id
         * @return attributes
         */
        Map<String, String> getAttributes(String luceneField, int docId, int relationId) {
            RelationInfoField f = fieldsByName.get(luceneField);
            long docsOffset = f.getDocsOffset();
            try {
                 docs().seek(docsOffset + docId * (Long.BYTES + Integer.BYTES));
                long relationsOffset = _docs.readLong();
                relations().seek(relationsOffset + relationId);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private int[] retrievePart(int start, int end) {
            if (start == -1)
                start = 0;
            if (end == -1 || end > docLength) // Can happen while making KWICs because we don't know the doc length until here
                end = docLength;
            ForwardIndexAbstract.validateSnippetParameters(docLength, start, end);

            // Read the snippet from the tokens file
            try {
                int[] snippet = new int[end - start];
                switch (tokensCodec) {
                case VALUE_PER_TOKEN:
                    switch(VALUE_PER_TOKEN_PARAMETER.fromCode(tokensCodecParameter)) {
                        case INT: 
                            _tokens.seek(docTokensOffset + (long) start * Integer.BYTES);
                            for (int j = 0; j < snippet.length; j++) {
                                snippet[j] = _tokens.readInt();
                            }
                            break;
                        case THREE_BYTES:
                            _tokens.seek(docTokensOffset + (long) start * 3);
                            for (int j = 0; j < snippet.length; j++) {
                                snippet[j] = ThreeByteInt.read( () -> _tokens.readByte() );

                            }
                            break;
                        case SHORT: 
                            _tokens.seek(docTokensOffset + (long) start * Short.BYTES);
                            for (int j = 0; j < snippet.length; j++) {
                                snippet[j] = _tokens.readShort();
                            }
                            break;
                        case BYTE: 
                            // Simplest encoding, just one 4-byte int per token
                            _tokens.seek(docTokensOffset + (long) start * Byte.BYTES);
                            for (int j = 0; j < snippet.length; j++) {
                                snippet[j] = _tokens.readByte();
                            }
                            break;
                        default: throw new NotImplementedException("Handling for tokens codec " + tokensCodec + " with parameter " + tokensCodecParameter
                                + " not implemented.");
                    }
                    break;
                case ALL_TOKENS_THE_SAME:
                    // All tokens have the same value, so we only have one value stored
                    _tokens.seek(docTokensOffset);
                    int value = _tokens.readInt();
                    Arrays.fill(snippet, value);
                    break;
                default:
                    throw new UnsupportedOperationException("Cannot read tokens codec: " + tokensCodec);
                }
                return snippet;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void getDocOffsetAndLength(String luceneField, int docId)  {
            try {
                tokensIndex(); // ensure input available
                long fieldTokensIndexOffset = fieldsByName.get(luceneField).getTokensIndexOffset();
                _tokensIndex.seek(fieldTokensIndexOffset + (long) docId * TOKENS_INDEX_RECORD_SIZE);
                docTokensOffset = _tokensIndex.readLong();
                docLength = _tokensIndex.readInt();
                tokensCodec = TokensCodec.fromCode(_tokensIndex.readByte());
                tokensCodecParameter = _tokensIndex.readByte();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        /** Get length of document in tokens from the forward index.
         *
         * This includes the "extra closing token" at the end, so subtract one for the real length.
         *
         * @param luceneField lucene field to read forward index from
         * @param docId segment-local docId of document to get length for
         * @return doc length
         */
        @Override
        public long docLength(String luceneField, int docId) {
            getDocOffsetAndLength(luceneField, docId);
            return docLength;
        }

        @Override
        public TermsSegmentReader terms(String luceneField) {
            try {
                return fieldsProducer.terms(luceneField);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
