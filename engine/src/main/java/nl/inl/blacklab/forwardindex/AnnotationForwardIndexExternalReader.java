package nl.inl.blacklab.forwardindex;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.search.indexmetadata.Annotation;

/**
 * Keeps a forward index of documents, to quickly answer the question "what word
 * occurs in doc X at position Y"?
 *
 * This implementation is thread-safe.
 */
class AnnotationForwardIndexExternalReader extends AnnotationForwardIndexExternalAbstract {

    protected static final Logger logger = LogManager.getLogger(AnnotationForwardIndexExternalReader.class);

    /** The unique terms in our index */
    private TermsReader terms = null;

    /** Mapping into the tokens file */
    private List<ByteBuffer> tokensFileChunks = null;

    /** Offsets of the mappings into the token file */
    private List<Long> tokensFileChunkOffsetBytes = null;

    /** Collators to use for terms file */
    private final Collators collators;

    /** Offset of each document */
    private long[] offset;

    /** Length of each document (INCLUDING the extra closing token at the end) */
    private int[] length;

    /** Deleted status of each document */
    private byte[] deleted;

    /** Deleted TOC entries. Always sorted by size. */
    private List<Integer> deletedTocEntries = null;

    AnnotationForwardIndexExternalReader(IndexReader reader, Annotation annotation, File dir, Collators collators) {
        super(reader, annotation, dir, collators);

        if (!dir.exists()) {
            throw new IllegalArgumentException("ForwardIndex doesn't exist: " + dir);
        }

        if (!tocFile.exists())
            throw new IllegalArgumentException("No TOC found, and not in index mode: " + tocFile);
        this.collators = collators; // for reading terms file in initialize()
    }

    /**
     * Memory-map the tokens file for reading.
     */
    @Override
    public synchronized void initialize() {
        if (initialized)
            return;

        readToc();

        terms = TermsExternalUtil.openForReading(collators, termsFile);

        try (RandomAccessFile tokensFp = new RandomAccessFile(tokensFile, "r");
                FileChannel tokensFileChannel = tokensFp.getChannel()) {
            // Map the tokens file in chunks of 2GB each. When retrieving documents, we always
            // read it from just one chunk, not multiple, but because each chunk begins at a
            // document start, documents of up to 2G tokens can be processed. We could get around
            // this limitation by reading from multiple chunks, but this would make the code
            // more complex.
            tokensFileChunks = new ArrayList<>();
            tokensFileChunkOffsetBytes = new ArrayList<>();
            long mappedBytes = 0;
            long tokenFileEndBytes = tokenFileEndPosition * Integer.BYTES;
            while (mappedBytes < tokenFileEndBytes) {
                // Find the last TOC entry start point that's also in the previous mapping
                // (or right the first byte after the previous mapping).

                // Look for the largest entryOffset that's no larger than mappedBytes.
                int mapNextChunkFrom = -1;
                for (int i = 0; i < offset.length; i++) {
                    if (offset[i] <= mappedBytes && (mapNextChunkFrom < 0 || offset[i] > offset[mapNextChunkFrom]))
                        mapNextChunkFrom = i;
                }

                // Uses binary search.
                int min = 0, max = offset.length;
                while (max - min > 1) {
                    int middle = (min + max) / 2;
                    long middleVal = offset[middle] * Integer.BYTES;
                    if (middleVal <= mappedBytes) {
                        min = middle;
                    } else {
                        max = middle;
                    }
                }
                long startOfNextMappingBytes = offset[min] * Integer.BYTES;

                // Map this chunk
                long sizeBytes = tokenFileEndBytes - startOfNextMappingBytes;
                if (sizeBytes > preferredChunkSizeBytes)
                    sizeBytes = preferredChunkSizeBytes;

                ByteBuffer mapping = tokensFileChannel.map(FileChannel.MapMode.READ_ONLY, startOfNextMappingBytes,
                        sizeBytes);
                tokensFileChunks.add(mapping);
                tokensFileChunkOffsetBytes.add(startOfNextMappingBytes);
                mappedBytes = startOfNextMappingBytes + sizeBytes;
            }
        } catch (IOException e1) {
            throw BlackLabException.wrapRuntime(e1);
        }
        initialized = true;
    }

    /**
     * Read the table of contents from the file
     */
    protected void readToc() {
        try (RandomAccessFile raf = new RandomAccessFile(tocFile, "r");
                FileChannel fc = raf.getChannel()) {
            long fileSize = tocFile.length();
            MappedByteBuffer buf = fc.map(MapMode.READ_ONLY, 0, fileSize);
            int n = buf.getInt();
            offset = new long[n];
            length = new int[n];
            deleted = new byte[n];
            LongBuffer lb = buf.asLongBuffer();
            lb.get(offset);
            buf.position(buf.position() + Long.BYTES * n);
            IntBuffer ib = buf.asIntBuffer();
            ib.get(length);
            buf.position(buf.position() + Integer.BYTES * n);
            buf.get(deleted);
            deletedTocEntries = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if (deleted[i] != 0) {
                    deletedTocEntries.add(i);
                }
                long end = offset[i] + length[i];
                if (end > tokenFileEndPosition)
                    tokenFileEndPosition = end;
            }
            sortDeletedTocEntries();
        } catch (IOException e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    protected void sortDeletedTocEntries() {
        deletedTocEntries.sort(Comparator.comparingInt(o -> length[o]));
    }

    @Override
    public List<int[]> retrievePartsInt(int docId, int[] starts, int[] ends) {
        if (!initialized)
            initialize();

        int fiid = docId2fiid(docId);
        if (deleted[fiid] != 0)
            return null;

        int n = starts.length;
        if (n != ends.length)
            throw new IllegalArgumentException("start and end must be of equal length");
        List<int[]> result = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            int start = starts[i];
            if (start == -1)
                start = 0;
            int end = ends[i];
            if (end == -1 || end > length[fiid]) // Can happen while making KWICs because we don't know the doc length until here
                end = length[fiid];
            ForwardIndexAbstract.validateSnippetParameters(length[fiid], start, end);
            if (start == end) {
                result.add(new int[0]);
                continue;
            }

            // The tokens file has has been mapped to memory.
            // Get an int buffer into the file.

            // Figure out which chunk to access.
            ByteBuffer whichChunk = null;
            long chunkOffsetBytes = -1;
            long entryOffsetBytes = offset[fiid] * Integer.BYTES;
            for (int j = 0; j < tokensFileChunkOffsetBytes.size(); j++) {
                long offsetBytes = tokensFileChunkOffsetBytes.get(j);
                ByteBuffer buffer = tokensFileChunks.get(j);
                if (offsetBytes <= entryOffsetBytes + (long) start * Integer.BYTES
                        && offsetBytes + buffer.capacity() >= entryOffsetBytes + (long) end
                                * Integer.BYTES) {
                    // This one!
                    whichChunk = buffer;
                    chunkOffsetBytes = offsetBytes;
                    break;
                }
            }

            if (whichChunk == null) {
                throw new InvalidIndex("Tokens file chunk containing document not found. fiid = " + fiid);
            }
            int snippetLength = end - start;
            int[] snippet = new int[snippetLength];

            // NOTE: we're synchronizing on a local variable (IntelliJ warns about this),
            //   but it references the object we're reading from, so it's correct to
            //   synchronize on it.
            synchronized (whichChunk) {
                ((Buffer) whichChunk).position((int) (offset[fiid] * Integer.BYTES - chunkOffsetBytes));
                // Get an IntBuffer to read the desired content
                IntBuffer ib = whichChunk.asIntBuffer();

                // The file is mem-mapped (search mode).
                // Position us at the correct place in the file.
                if (start > ib.limit()) {
                    logger.debug("  start=" + start + ", ib.limit()=" + ib.limit());
                }
                ib.position(start);
                ib.get(snippet);
            }

            result.add(snippet);
        }

        return result;
    }

    /**
     * @return the number of documents in the forward index
     */
    @Override
    public int numDocs() {
        if (!initialized)
            initialize();
        return offset.length;
    }

    /**
     * Gets the length (in tokens) of a document.
     *
     * NOTE: this INCLUDES the extra closing token at the end.
     *
     * @param docId forward index id of a document
     * @return length of the document
     */
    @Override
    public int docLength(int docId) {
        if (!initialized)
            initialize();
        return length[docId2fiid(docId)];
    }

    /**
     * Get the Terms object in order to translate ids to token strings
     *
     * @return the Terms object
     */
    @Override
    public Terms terms() {
        if (!initialized)
            initialize();
        return terms;
    }
}
