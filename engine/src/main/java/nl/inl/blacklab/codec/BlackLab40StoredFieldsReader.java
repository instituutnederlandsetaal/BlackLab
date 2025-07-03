package nl.inl.blacklab.codec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.codecs.StoredFieldsReader;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.index.StoredFieldVisitor;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.Accountables;

import nl.inl.blacklab.Constants;
import nl.inl.blacklab.contentstore.ContentStoreSegmentReader;
import nl.inl.blacklab.search.BlackLabIndexIntegrated;

/**
 * Provides random access to values stored as a content store.
 * Delegates non-content-store reads to the default implementation.
 */
public class BlackLab40StoredFieldsReader extends BlackLabStoredFieldsReader {

    /** How large we allow the block decoding buffer to become before throwing an error. */
    private static final int MAX_DECODE_BUFFER_LENGTH = 100_000;

    /** Size in bytes of a record in the docindex file */
    private static final int DOCINDEX_RECORD_SIZE = Integer.BYTES + Byte.BYTES;

    /** Size in bytes of a record in the valueindex file */
    private static final int VALUEINDEX_RECORD_SIZE = Byte.BYTES + Integer.BYTES + Byte.BYTES + 2 * Long.BYTES;

    /** Size in bytes of a record in the blockindex file */
    private static final int BLOCKINDEX_RECORD_SIZE = Integer.BYTES;

    /** How many bytes a UTF-8 codepoint can take at most, for reserving buffer space */
    private static final int UTF8_MAX_BYTES_PER_CHAR = 4;

    /** How much space to reserve in the buffer for decoding overhead*/
    private static final int ESTIMATED_DECODE_OVERHEAD = 1024;

    /** Our segment directory */
    private final Directory directory;

    /** Info about our segment */
    private final SegmentInfo segmentInfo;

    /** Lucene I/O context (?) */
    private final IOContext ioContext;

    /** Information about the fields in this segment */
    private final FieldInfos fieldInfos;

    /** Lucene stored fields reader we delegate to for non-content-store fields */
    private final StoredFieldsReader delegate;

    /** Fields with a content store and their field index. */
    private final Map<String, Integer> contentStoreFieldIndexes = new HashMap<>();

    /** Offset for each doc in the valueindex file, and number of fields stored */
    private final IndexInput _docIndexFile;

    /** Information about field, value length, codec, and offsets in the block* files */
    private final IndexInput _valueIndexFile;

    /** Offsets in the blocks file (1 or more for each value stored) */
    private final IndexInput _blockIndexFile;

    /** Encoded data blocks */
    private final IndexInput _blocksFile;

    /** Offset in docIndex file after header, so we can calculate doc offsets. */
    private final long docIndexFileOffset;

    /** How many characters from the document do we encode into a data block? */
    private final int blockSizeChars;

    /** Name of the StoredFieldsFormat we delegate to.
     *  We check the index files to make sure this matches. */
    private String delegateFormatName;

    public BlackLab40StoredFieldsReader(Directory directory, SegmentInfo segmentInfo, IOContext ioContext, FieldInfos fieldInfos,
            StoredFieldsReader delegate, String delegateFormatName)
            throws IOException {
        this.directory = directory;
        this.segmentInfo = segmentInfo;
        this.ioContext = ioContext;
        this.fieldInfos = fieldInfos;
        this.delegate = delegate;
        this.delegateFormatName = delegateFormatName; // check that this matches what was written

        IndexInput fieldsFile = openInput(BlackLabStoredFieldsFormat.FIELDS_EXT, directory, segmentInfo, ioContext);
        blockSizeChars = fieldsFile.readInt();
        while (fieldsFile.getFilePointer() < (fieldsFile.length() - CodecUtil.footerLength())) {
            String fieldName = fieldsFile.readString();
            int fieldIndex = contentStoreFieldIndexes.size();
            contentStoreFieldIndexes.put(fieldName, fieldIndex);
        }
        fieldsFile.close();
        _docIndexFile = openInput(BlackLabStoredFieldsFormat.DOCINDEX_EXT, directory, segmentInfo, ioContext);
        docIndexFileOffset = _docIndexFile.getFilePointer(); // remember offset after header so we can calculate doc offsets.
        _valueIndexFile = openInput(BlackLabStoredFieldsFormat.VALUEINDEX_EXT, directory, segmentInfo, ioContext);
        _blockIndexFile = openInput(BlackLabStoredFieldsFormat.BLOCKINDEX_EXT, directory, segmentInfo, ioContext);
        _blocksFile = openInput(BlackLabStoredFieldsFormat.BLOCKS_EXT, directory, segmentInfo, ioContext);
    }

    /** Lucene 8 uses big-endian, Lucene 9 little-endian */
    public IndexInput openInputCorrectEndian(Directory directory, String fileName, IOContext ioContext) throws IOException {
        return directory.openInput(fileName, ioContext);
    }

    /**
     * Open a custom file for reading and check the header.
     *
     * Also detect the delegate format name, or check if it matches the one we already detected.
     *
     * @param extension   extension of the file to open (will automatically be prefixed with "blfi.")
     * @param directory   segment directory
     * @param segmentInfo segment info
     * @param ioContext   I/O context
     * @return handle to the opened segment file
     */
    private IndexInput openInput(String extension, Directory directory, SegmentInfo segmentInfo, IOContext ioContext) throws IOException {
        String segmentSuffix = "";
        String fileName = IndexFileNames.segmentFileName(segmentInfo.name, segmentSuffix, extension);
        IndexInput input = openInputCorrectEndian(directory, fileName, ioContext);
        try {
            // Check index header
            String codecName = BlackLab40StoredFieldsFormat.NAME + "_" + extension;
            CodecUtil.checkIndexHeader(input, codecName, BlackLab40StoredFieldsFormat.VERSION_START,
                    BlackLab40StoredFieldsFormat.VERSION_CURRENT, segmentInfo.getId(), segmentSuffix);

            // Set or check delegate format name
            String delegateFN = input.readString();
            if (delegateFormatName == null)
                delegateFormatName = delegateFN;
            if (!delegateFormatName.equals(delegateFN))
                throw new IOException("Segment file " + fileName + " contains wrong delegate format name: " +
                        delegateFN + " (expected " + delegateFormatName + ")");
            return input;
        } catch (Exception e) {
            input.close();
            throw e;
        }
    }

    @Override
    public void visitDocument(int docId, StoredFieldVisitor storedFieldVisitor) throws IOException {
        // Visit each regular stored field.
        delegate.visitDocument(docId, storedFieldVisitor);

        // Visit each content store field.
        for (FieldInfo fieldInfo: fieldInfos) {
            switch (storedFieldVisitor.needsField(fieldInfo)) {
            case YES:
                // Visit this field.
                if (BlackLabIndexIntegrated.isContentStoreField(fieldInfo)) {
                    visitContentStoreDocument(docId, fieldInfo, storedFieldVisitor);
                }
                break;
            case NO:
                // No, we don't want to visit this field.
                break;
            case STOP:
                // We should stop visiting fields altogether.
                return;
            }
        }
    }

    /**
     * Retrieve contents from content store and pass them to visitor.
     *
     * This is used when retrieving the entire field value from Lucene, and when segments are merged.
     *
     * @param docId              document id
     * @param storedFieldVisitor visitor that needs the contents
     */
    private void visitContentStoreDocument(int docId, FieldInfo fieldInfo, StoredFieldVisitor storedFieldVisitor)
            throws IOException {
        byte[] contents = contentStore().getBytes(docId, fieldInfo.name);
        if (contents != null)
            storedFieldVisitor.stringField(fieldInfo, contents);
    }

    @Override
    public StoredFieldsReader clone() {
        try {
            return new BlackLab40StoredFieldsReader(directory, segmentInfo, ioContext, fieldInfos,
                    delegate.clone(), delegateFormatName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void checkIntegrity() throws IOException {
        delegate.checkIntegrity();

        // When is this called? Should we check our own files' checksum here as well?
    }

    @Override
    public void close() throws IOException {
        // Close our files
        _docIndexFile.close();
        _valueIndexFile.close();
        _blockIndexFile.close();
        _blocksFile.close();

        // Let the delegate close its files.
        delegate.close();
    }

    @Override
    public long ramBytesUsed() {
        return delegate.ramBytesUsed() /* +
                RamUsageEstimator.sizeOfObject(fieldsFile) +
                RamUsageEstimator.sizeOfObject(docIndexFile) +
                RamUsageEstimator.sizeOfObject(valueIndexFile) +
                RamUsageEstimator.sizeOfObject(blockIndexFile) +
                RamUsageEstimator.sizeOfObject(blocksFile) +
                Integer.BYTES * 2 + // blockSizeChars, numberOfFieldsWritten
                RamUsageEstimator.sizeOfMap(fields)
                */ ;
    }

    @Override
    public Collection<Accountable> getChildResources() {
        return List.of(Accountables.namedAccountable("delegate", delegate));
    }

    @Override
    public StoredFieldsReader getMergeInstance() {

        // For now we don't have a merging-optimized version of this class,
        // but maybe in the future.

        StoredFieldsReader mergeInstance = delegate.getMergeInstance();
        if (mergeInstance != delegate) {
            // The delegate has a specific merge instance (i.e. didn't return itself).
            // Create a new instance with the new delegate and return that.
            try {
                return new BlackLab40StoredFieldsReader(directory, segmentInfo, ioContext, fieldInfos,
                        mergeInstance, delegateFormatName);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return this;
    }

    @Override
    public String toString() {
        return "BlackLab40StoredFieldsReader(" + delegate.toString() + ")";
    }

    /**
     * Create a content store reader for this segment.
     *
     * The returned reader is not threadsafe and shouldn't be stored.
     * A single thread may use it for reading from this segment. It
     * can then be discarded.
     *
     * @return content store segment reader
     */
    @Override
    public synchronized ContentStoreSegmentReader contentStore() {
        // NOTE: this method is synchronized because IndexInput.clone() is not thread-safe!
        //       so if multiple threads could call this method simultaneously, disaster could strike.
        return new ContentStoreSegmentReader() {

            // Buffer for decoding blocks. Automatically reallocated if needed.
            byte[] decodedValue;

            // Clones of the various file handles, so we can reposition them without
            // causing problems. Cloned IndexInputs don't need to be closed.
            private final IndexInput docIndexFile = _docIndexFile.clone();
            private final IndexInput valueIndexFile = _valueIndexFile.clone();
            private final IndexInput blockIndexFile = _blockIndexFile.clone();
            private final IndexInput blocksFile = _blocksFile.clone();

            /**
             * Get the field value as bytes.
             *
             * @param docId     document id
             * @param luceneField field to get
             * @return field value as bytes, or null if no value
             */
            @Override
            public byte[] getBytes(int docId, String luceneField) {
                try {
                    // Find the value length in characters, and position the valueIndex file pointer
                    // to read the rest of the information we need: where to find the block indexes
                    // and where the blocks start.
                    final int valueLengthChar = findValueLengthChar(docId, luceneField);
                    if (valueLengthChar == 0)
                        return null; // no value stored for this document
                    ContentStoreBlockCodec blockCodec = ContentStoreBlockCodec.fromCode(valueIndexFile.readByte());
                    final long blockIndexOffset = valueIndexFile.readLong();
                    final long blocksOffset = valueIndexFile.readLong();

                    // Determine what blocks we'll need
                    final int firstBlockNeeded = 0;
                    final int lastBlockNeeded = valueLengthChar / blockSizeChars; // implicitly does a floor()
                    // add one block for spillover discarded by the floor().
                    // NOTE: don't add a spillover block if the document fits exactly in the block size.
                    final int numBlocksNeeded = lastBlockNeeded - firstBlockNeeded + ((valueLengthChar % blockSizeChars) == 0 ? 0 : 1);

                    // Determine where our first block starts, and position blockindex file
                    // to start reading subsequent after-block positions
                    int blockStartOffset = findBlockStartOffset(blockIndexOffset, blocksOffset, firstBlockNeeded);

                    // Try to make sure we have a large enough buffer available
                    long decodeBufferLengthLong = (long) valueLengthChar * UTF8_MAX_BYTES_PER_CHAR + ESTIMATED_DECODE_OVERHEAD;
                    if (decodeBufferLengthLong > Constants.JAVA_MAX_ARRAY_SIZE)
                        decodeBufferLengthLong = Constants.JAVA_MAX_ARRAY_SIZE;
                    final int decodeBufferLength = (int) decodeBufferLengthLong;
                    if (decodedValue == null || decodedValue.length < decodeBufferLength)
                        decodedValue = new byte[decodeBufferLength];

                    int decodedOffset = 0; // write position in the decodedValue buffer
                    int numBlocksRead = 0;
                    try (ContentStoreBlockCodec.Decoder decoder = blockCodec.getDecoder()) {
                        while (numBlocksRead < numBlocksNeeded) {

                            // Read a block and decompress it.
                            final int blockEndOffset = blockIndexFile.readInt();
                            final int blockSizeBytes = blockEndOffset - blockStartOffset;

                            int decodedSize = -1;
                            while (decodedSize < 0) {
                                decodedSize = readAndDecodeBlock(blockSizeBytes, decoder, decodedValue, decodedOffset);
                                if (decodedSize < 0) {
                                    // Not enough buffer space. Reallocate and try again (up to a point).
                                    final int availableSpace = decodedValue.length - decodedOffset;
                                    if (availableSpace > MAX_DECODE_BUFFER_LENGTH)
                                        throw new IOException("Insufficient buffer space for decoding block, even at max (" + MAX_DECODE_BUFFER_LENGTH + ")");
                                    // Double the available space for the remaining blocks (probably always just 1 block)
                                    final int blocksLeftToRead = numBlocksNeeded - numBlocksRead;
                                    final byte[] newBuffer = new byte[decodedOffset + blocksLeftToRead * availableSpace * 2];
                                    System.arraycopy(decodedValue, 0, newBuffer, 0, decodedOffset);
                                    decodedValue = newBuffer;
                                }
                            }
                            decodedOffset += decodedSize;

                            // Update variables to read the next block
                            blockStartOffset = blockEndOffset;
                            numBlocksRead++;
                        }
                    }
                    // Copy result to a new array of the right size
                    final byte[] result = new byte[decodedOffset];
                    System.arraycopy(decodedValue, 0, result, 0, result.length);
                    return result;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            private int findBlockStartOffset(long blockIndexOffset, long blocksOffset, int blockNumber)
                    throws IOException {
                int blockStartOffset;
                if (blockNumber == 0) {
                    // Start of the first block is always 0. We don't store that.
                    blockStartOffset = 0;
                    blockIndexFile.seek(blockIndexOffset);
                    blocksFile.seek(blocksOffset);
                } else {
                    // Start of block n is end of block n-1; read it from the block index file.
                    blockIndexFile.seek(blockIndexOffset + (long) (blockNumber - 1) * BLOCKINDEX_RECORD_SIZE);
                    blockStartOffset = blockIndexFile.readInt();
                    blocksFile.seek(blocksOffset + blockStartOffset);
                }
                return blockStartOffset;
            }

            /**
             * Get the entire field value.
             *
             * @param docId document id
             * @param luceneField field to get
             * @return field value
             */
            @Override
            public String getValue(int docId, String luceneField) {
                return getValueSubstring(docId, luceneField, 0, -1);
            }

            /**
             * Get part of the field value.
             *
             * If startChar or endChar are larger than <code>value.length()</code>, they will be clamped to that value.
             *
             * @param docId document id
             * @param luceneField field to get
             * @param startChar first character to get. Must be zero or greater.
             * @param endChar character after the last character to get, or -1 for <code>value.length()</code>.
             * @return requested part
             */
            @Override
            public String getValueSubstring(int docId, String luceneField, int startChar, int endChar) {
                if (startChar < 0)
                    throw new IllegalArgumentException("Illegal startChar value, must be >= 0: " + startChar);
                if (endChar < -1)
                    throw new IllegalArgumentException("Illegal endChar value, must be >= -1: " + endChar);
                if (endChar != -1 && startChar > endChar)
                    throw new IllegalArgumentException("Illegal startChar/endChar values, startChar > endChar: " +
                            startChar + "-" + endChar);

                try {
                    // Find the value length in characters, and position the valueIndex file pointer
                    // to read the rest of the information we need: where to find the block indexes
                    // and where the blocks start.
                    int valueLengthChar = findValueLengthChar(docId, luceneField);
                    if (valueLengthChar == 0)
                        return ""; // no value stored for this document
                    if (startChar > valueLengthChar)
                        startChar = valueLengthChar;
                    if (endChar == -1 || endChar > valueLengthChar)
                        endChar = valueLengthChar;
                    ContentStoreBlockCodec blockCodec = ContentStoreBlockCodec.fromCode(valueIndexFile.readByte());
                    long blockIndexOffset = valueIndexFile.readLong();
                    long blocksOffset = valueIndexFile.readLong();

                    // Determine what blocks we'll need
                    int firstBlockNeeded = startChar / blockSizeChars;
                    int lastBlockNeeded = endChar / blockSizeChars;
                    int numBlocksNeeded = lastBlockNeeded - firstBlockNeeded + 1;

                    // Determine where our first block starts, and position blockindex file
                    // to start reading subsequent after-block positions
                    int blockStartOffset = findBlockStartOffset(blockIndexOffset, blocksOffset, firstBlockNeeded);

                    int currentBlockCharOffset = firstBlockNeeded * blockSizeChars;
                    int blocksRead = 0;
                    StringBuilder result = new StringBuilder();
                    byte[] decodedBlock = new byte[blockSizeChars * UTF8_MAX_BYTES_PER_CHAR + ESTIMATED_DECODE_OVERHEAD];
                    try (ContentStoreBlockCodec.Decoder decoder = blockCodec.getDecoder()) {
                        while (blocksRead < numBlocksNeeded) {

                            // Read a block and decompress it.
                            int blockEndOffset = blockIndexFile.readInt();
                            int blockSizeBytes = blockEndOffset - blockStartOffset;
                            int decodedSize = -1;
                            while (decodedSize < 0) {
                                decodedSize = readAndDecodeBlock(blockSizeBytes, decoder, decodedBlock, 0);
                                if (decodedSize < 0) {
                                    if (decodedBlock.length > MAX_DECODE_BUFFER_LENGTH)
                                        throw new IOException("Insufficient buffer space for decoding block, even at max (" + MAX_DECODE_BUFFER_LENGTH + ")");
                                    decodedBlock = new byte[decodedBlock.length * 2];
                                }
                            }
                            String blockDecompressed = new String(decodedBlock, 0, decodedSize, StandardCharsets.UTF_8);

                            // Append the content we need to the result.
                            if (blocksRead == 0) {
                                // First block. Only take the part we need.
                                if (numBlocksNeeded == 1) {
                                    // This is the only block we need. Take the requested part from the middle.
                                    result.append(blockDecompressed, startChar - currentBlockCharOffset,
                                            endChar - currentBlockCharOffset);
                                } else {
                                    // We'll need more blocks. Take part at the end.
                                    result.append(blockDecompressed, startChar - currentBlockCharOffset,
                                            blockSizeChars);
                                }
                            } else if (blocksRead == numBlocksNeeded - 1) {
                                // Last block. Take the part we need from the beginning.
                                result.append(blockDecompressed, 0, endChar - currentBlockCharOffset);
                            } else {
                                // Middle block. Append the whole thing.
                                result.append(blockDecompressed);
                            }

                            // Update variables to read the next block
                            blockStartOffset = blockEndOffset;
                            currentBlockCharOffset += blockSizeChars;
                            blocksRead++;
                        }
                    }
                    return result.toString();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            int readAndDecodeBlock(int blockSizeBytes, ContentStoreBlockCodec.Decoder decoder, byte[] buffer, int offset)
                    throws IOException {
                // Read block (file is already positioned)
                byte[] block = new byte[blockSizeBytes];
                blocksFile.readBytes(block, 0, blockSizeBytes);

                // Decode block into buffer
                int maxLength = buffer.length - offset;
                return decoder.decode(block, 0, blockSizeBytes, buffer, offset, maxLength);
            }

            /**
             * Finds the length in characters of a stored value.
             *
             * Also positions the valueIndexFile pointer to just after the doc length,
             * from which we can continue reading information about the value (such as where
             * to find the actual value itself).
             *
             * @param docId document id
             * @param luceneField field to get length for
             * @return length of the value in characters
             */
            private int findValueLengthChar(int docId, String luceneField) throws IOException {
                // What's the id of the field that is references in the value index file?
                int fieldId = contentStoreFieldIndexes.get(luceneField);

                // Find the document
                docIndexFile.seek(docIndexFileOffset + (long) docId * DOCINDEX_RECORD_SIZE);
                int valueIndexOffset = docIndexFile.readInt();
                byte numberOfContentStoreFields = docIndexFile.readByte();

                // Find the correct field in this document
                int i = 0;
                while (i < numberOfContentStoreFields) {
                    valueIndexFile.seek(valueIndexOffset + (long) i * VALUEINDEX_RECORD_SIZE);
                    byte thisFieldId = valueIndexFile.readByte();
                    if (thisFieldId == fieldId)
                        break;
                    i++;
                }
                if (i == numberOfContentStoreFields)
                    return 0;

                // Read document length, where to find the block indexes and where the blocks start.
                return valueIndexFile.readInt();
            }

            /**
             * Get the length of a value in the content store.
             *
             * @param docId document id
             * @param luceneField field to get length for
             * @return length of the value in characters
             */
            @Override
            public int valueLength(int docId, String luceneField) {
                try {
                    return findValueLengthChar(docId, luceneField);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            /**
             * Get several parts of the field value.
             *
             * @param docId document id
             * @param luceneField field to get
             * @param start positions of the first character to get. Must all be zero or greater.
             * @param end positions of the character after the last character to get, or -1 for <code>value.length()</code>.
             * @return requested parts
             */
            @Override
            public String[] getValueSubstrings(int docId, String luceneField, int[] start, int[] end) {
                if (start.length != end.length)
                    throw new IllegalArgumentException("Different numbers of starts and ends provided: " + start.length + ", " + end.length);
                // OPT: we could optimize this to avoid reading blocks twice!
                //   easiest is to determine the lowest start and highest end, read the entire document part,
                //   then cut the snippets from that. More efficient is to figure out exactly which blocks we
                //   need, retrieve those, then cut the snippets.
                String[] results = new String[start.length];
                for (int i = 0; i < start.length; i++) {
                    results[i] = getValueSubstring(docId, luceneField, start[i], end[i]);
                }
                return results;
            }
        };
    }
}
