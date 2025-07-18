package nl.inl.blacklab.codec;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.backward_codecs.store.EndiannessReverserUtil;
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.codecs.StoredFieldsWriter;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.index.MergeState;
import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.Accountables;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.RamUsageEstimator;

import nl.inl.blacklab.search.BlackLabIndexIntegrated;

public abstract class BlackLabStoredFieldsWriter extends StoredFieldsWriter {

    /** How large we allow the block encoding buffer to become before throwing an error. */
    private static final int MAX_ENCODE_BUFFER_LENGTH = 100_000;

    /** What fields are stored in the content store? */
    protected final IndexOutput fieldsFile;

    /** Offset for each doc in the valueindex file, and number of fields stored */
    protected final IndexOutput docIndexFile;

    /** Information about field, value length, codec, and offsets in the block* files */
    protected final IndexOutput valueIndexFile;

    /** Offsets in the blocks file (1 or more for each value stored) */
    protected final IndexOutput blockIndexFile;

    /** Encoded data blocks */
    protected final IndexOutput blocksFile;

    /** Lucene's default stored fields writer, for regular stored fields. */
    protected final StoredFieldsWriter delegate;

    /** Class name for the delegate StoredFieldsFormat, which we will write to our file headers,
     * so we can ensure we're using the right delegate format when reading.
     */
    protected final String delegateFormatName;

    /** How many characters per compressed block. */
    protected final int blockSizeChars = BlackLabStoredFieldsFormat.DEFAULT_BLOCK_SIZE_CHARS;

    /** Fields with a content store and their field index. */
    private final Map<String, Integer> contentStoreFieldIndexes = new HashMap<>();

    /** How we (de)compress our blocks. */
    private final ContentStoreBlockCodec blockCodec = ContentStoreBlockCodecZlib.INSTANCE;

    String storedFieldsFormatName;

    int minVersion;

    int currentVersion;

    /** How many CS fields were written for the current document? */
    private byte numberOfFieldsWritten;

    /** Whether to reverse the endianness of the content store files. */
    private final boolean reverseEndian;

    public BlackLabStoredFieldsWriter(String storedFieldsFormatName, int minVersion, int currentVersion,
            Directory directory, SegmentInfo segmentInfo, IOContext ioContext, StoredFieldsWriter delegate,
            String delegateFormatName, boolean reverseEndian) throws IOException {
        this.storedFieldsFormatName = storedFieldsFormatName;
        this.minVersion = minVersion;
        this.currentVersion = currentVersion;
        this.delegate = delegate;
        if (delegateFormatName == null)
            throw new IllegalStateException("Opening the segment FI should have set the delegate format name");
        this.delegateFormatName = delegateFormatName;
        this.reverseEndian = reverseEndian;
        fieldsFile = createOutput(BlackLabStoredFieldsFormat.FIELDS_EXT, directory, segmentInfo, ioContext);
        docIndexFile = createOutput(BlackLabStoredFieldsFormat.DOCINDEX_EXT, directory, segmentInfo, ioContext);
        valueIndexFile = createOutput(BlackLabStoredFieldsFormat.VALUEINDEX_EXT, directory, segmentInfo, ioContext);
        blockIndexFile = createOutput(BlackLabStoredFieldsFormat.BLOCKINDEX_EXT, directory, segmentInfo, ioContext);
        blocksFile = createOutput(BlackLabStoredFieldsFormat.BLOCKS_EXT, directory, segmentInfo, ioContext);

        // NOTE: we can make this configurable later (to optimize for specific usage scenarios),
        // but for now we'll just use the default value.
        fieldsFile.writeInt(blockSizeChars);
    }

    protected IndexOutput createOutput(String ext, Directory directory, SegmentInfo segmentInfo, IOContext ioContext)
            throws IOException {
        final IndexOutput indexOutput;
        String codecName = storedFieldsFormatName + "_" + ext;
        String segmentSuffix = "";
        String fileName = IndexFileNames.segmentFileName(segmentInfo.name, segmentSuffix, ext);

        if (reverseEndian) {
            indexOutput = EndiannessReverserUtil.createOutput(directory, fileName, ioContext); // flip for Lucene 9
        } else {
            indexOutput = directory.createOutput(fileName, ioContext);
        }

        CodecUtil.writeIndexHeader(indexOutput, codecName, currentVersion,
                segmentInfo.getId(), segmentSuffix);
        assert CodecUtil.indexHeaderLength(codecName, segmentSuffix) == indexOutput.getFilePointer();
        indexOutput.writeString(delegateFormatName);
        return indexOutput;
    }

    @Override
    public void startDocument() throws IOException {
        docIndexFile.writeInt((int)valueIndexFile.getFilePointer());
        numberOfFieldsWritten = 0; // we'll record this at the end of the doc

        delegate.startDocument();
    }

    /**
     * Write a single stored field or content store value.
     *
     * Will either delegate to the default stored field writer,
     * or will store the value in the content store.
     *
     * @param fieldInfo field to write
     * @param v value to write
     */
    @Override
    public void writeField(FieldInfo fieldInfo, float v) throws IOException {
        assert !BlackLabIndexIntegrated.isContentStoreField(fieldInfo);
        delegate.writeField(fieldInfo, v);
    }

    @Override
    public void writeField(FieldInfo fieldInfo, int v) throws IOException {
        assert !BlackLabIndexIntegrated.isContentStoreField(fieldInfo);
        delegate.writeField(fieldInfo, v);
    }

    @Override
    public void writeField(FieldInfo fieldInfo, long v) throws IOException {
        assert !BlackLabIndexIntegrated.isContentStoreField(fieldInfo);
        delegate.writeField(fieldInfo, v);
    }

    @Override
    public void writeField(FieldInfo fieldInfo, double v) throws IOException {
        assert !BlackLabIndexIntegrated.isContentStoreField(fieldInfo);
        delegate.writeField(fieldInfo, v);
    }

    @Override
    public void writeField(FieldInfo fieldInfo, String v) throws IOException {
        if (BlackLabIndexIntegrated.isContentStoreField(fieldInfo)) {
            // This is a content store field.
            writeContentStoreField(fieldInfo, v);
        } else {
            // This is a regular stored field. Delegate.
            delegate.writeField(fieldInfo, v);
        }
    }

    @Override
    public void writeField(FieldInfo fieldInfo, BytesRef v) throws IOException {
        if (BlackLabIndexIntegrated.isContentStoreField(fieldInfo)) {
            // This is a content store field.
            writeContentStoreField(fieldInfo, v.utf8ToString());
        } else {
            // This is a regular stored field. Delegate.
            delegate.writeField(fieldInfo, v);
        }
    }

    /**
     * Write a content store field to the content store files.
     *
     * @param fieldInfo field to write
     * @param value string value for the field
     */
    private void writeContentStoreField(FieldInfo fieldInfo, String value) throws IOException {
        // Write some info about this value
        valueIndexFile.writeByte(getFieldIndex(fieldInfo)); // which field is this?
        int lengthChars = value.length();
        valueIndexFile.writeInt(lengthChars);
        valueIndexFile.writeByte(blockCodec.getCode());
        valueIndexFile.writeLong(blockIndexFile.getFilePointer());
        long baseOffset = blocksFile.getFilePointer();
        valueIndexFile.writeLong(baseOffset);

        // Write blocks and block offsets
        int numberOfBlocks = (lengthChars + blockSizeChars - 1) / blockSizeChars; // ceil(lengthInChars/blockSizeChars)
        try (ContentStoreBlockCodec.Encoder encoder = blockCodec.getEncoder()) {
            byte[] buffer = new byte[blockSizeChars * 3]; // hopefully be enough space, or we'll grow it
            for (int i = 0; i < numberOfBlocks; i++) {

                int blockOffset = i * blockSizeChars;
                int blockLength = Math.min(blockSizeChars, value.length() - blockOffset);
                //String block = value.substring(blockOffset, blockOffset + blockLength);

                // Compress block and write to values file
                int bytesWritten = -1;
                while (bytesWritten < 0) {
                    bytesWritten = encoder.encode(value, blockOffset, blockLength, buffer, 0, buffer.length);
                    if (bytesWritten < 0) {
                        if (buffer.length > MAX_ENCODE_BUFFER_LENGTH)
                            throw new IOException("Insufficient buffer space for encoding block, even at max (" + MAX_ENCODE_BUFFER_LENGTH
                                    + ")");
                        buffer = new byte[buffer.length * 2];
                    }
                }

                blocksFile.writeBytes(buffer, 0, bytesWritten);

                // Write offset after block to index file
                int offset = (int) (blocksFile.getFilePointer() - baseOffset);
                blockIndexFile.writeInt(offset);
            }
        }

        // Keep track of the number of values written for this doc, so we can record that later.
        numberOfFieldsWritten++;
        if (numberOfFieldsWritten == 127) {
            throw new IllegalStateException("Too many content store fields for document (>=127)");
        }
    }

    /**
     * Get the index (in the fields file) of a field we're writing.
     *
     * If the field did not have an index yet, write it to the fields file and
     * assign the index.
     *
     * @param fieldInfo field to get index for
     * @return index for the field
     */
    private byte getFieldIndex(FieldInfo fieldInfo) throws IOException {
        String name = fieldInfo.name;
        Integer id = contentStoreFieldIndexes.get(name);
        if (id == null) {
            id = contentStoreFieldIndexes.size();
            if (id == 128)
                throw new IllegalStateException("Too many content store fields (>127)");
            contentStoreFieldIndexes.put(name, id);
            fieldsFile.writeString(name);
        }
        return (byte)(int)id;
    }

    @Override
    public void finish(int i) throws IOException {
        delegate.finish(i);
    }

    @Override
    public void close() throws IOException {
        // Close our files

        CodecUtil.writeFooter(fieldsFile);
        CodecUtil.writeFooter(docIndexFile);
        CodecUtil.writeFooter(valueIndexFile);
        CodecUtil.writeFooter(blockIndexFile);
        CodecUtil.writeFooter(blocksFile);

        fieldsFile.close();
        docIndexFile.close();
        valueIndexFile.close();
        blockIndexFile.close();
        blocksFile.close();

        // Let the delegate close its files
        delegate.close();
    }

    @Override
    public long ramBytesUsed() {
        return delegate.ramBytesUsed() +
                RamUsageEstimator.sizeOfObject(fieldsFile) +
                RamUsageEstimator.sizeOfObject(docIndexFile) +
                RamUsageEstimator.sizeOfObject(valueIndexFile) +
                RamUsageEstimator.sizeOfObject(blockIndexFile) +
                RamUsageEstimator.sizeOfObject(blocksFile) +
                Integer.BYTES * 2 + // blockSizeChars, numberOfFieldsWritten
                RamUsageEstimator.sizeOfMap(contentStoreFieldIndexes);
    }

    @Override
    public void finishDocument() throws IOException {
        // Record how many fields were written for this doc (could be 0)
        docIndexFile.writeByte(numberOfFieldsWritten);

        delegate.finishDocument();
    }

    /**
     * Merge multiple segments' stored fields files.
     *
     * Identical to StoredFieldsWriter.merge() except we instantiate OurMergeVisitor,
     * which recognizes content store fields and merges them appropriately.
     *
     * @param mergeState merge state
     * @return number of docs
     */
    @Override
    public int merge(MergeState mergeState) throws IOException {
        // NOTE: the default implementation just reads all the fields for each document
        //   and writes them again. This works but is relatively slow.
        //   More efficient is to copy most information (esp. compressed blocks)
        //   directly from the readers to the new contentstore files.
        // Problem is that we cannot instantiate MergeState, which we would need to do
        // (we would split MergeState into one for us and one for the delegate,
        //  passing all the regular stored fields to the delegate and handling the other
        //  fields ourselves).
        return super.merge(mergeState);

        // Don't call the delegate here because the default implementation processes all the fields,
        // both the regular stored fields and the content store fields. So the regular stored fields
        // will be written to the new segment in the normal way.
        //return delegate.merge(mergeState);
    }

    @Override
    public Collection<Accountable> getChildResources() {
        return List.of(Accountables.namedAccountable("delegate", delegate));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" + delegate.getClass().getName() + ")";
    }
}
