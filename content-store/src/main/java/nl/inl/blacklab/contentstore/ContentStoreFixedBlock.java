package nl.inl.blacklab.contentstore;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.util.Set;

import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.util.CollUtil;
import nl.inl.util.SimpleResourcePool;

public abstract class ContentStoreFixedBlock extends ContentStoreDirAbstract {

    /**
     * The type of content store. Written to version file and detected when opening.
     */
    protected static final String CONTENT_STORE_TYPE_NAME = "fixedblock";

    /**
     * Version of this type of content store. Written to version file and detected
     * when opening.
     */
    protected static final String CURRENT_VERSION = "1";

    /** Name of the table of contents file */
    protected static final String TOC_FILE_NAME = "toc.dat";

    /** Name of the file containing all the original file contents (zipped) */
    protected static final String CONTENTS_FILE_NAME = "file-contents.dat";

    /**
     * Block size for the contents file.
     *
     * Contributing factors for choosing block size:
     * - larger blocks improve compression ratio
     * - larger blocks decrease number of blocks you have to read
     * - smaller blocks decrease the decompression time
     * - smaller blocks increase the chance that we only have to read one disk block for a single concordance
     * (disk blocks are generally 2 or 4K)
     * - consider OS I/O caching and memory mapping. Then it becomes the difference between reading a few bytes from
     * memory and reading a few kilobytes and decompressing them. Right now, making concordances is often CPU-bound
     * (because of decompression?)
     */
    protected static final int BLOCK_SIZE_BYTES = 4096;

    /**
     * How small a block can get without triggering a retry with more input
     * characters
     */
    protected static final int MINIMUM_ACCEPTABLE_BLOCK_SIZE = BLOCK_SIZE_BYTES * 9 / 10;

    /** The expected average compression factor */
    protected static final float AVERAGE_COMPRESSION_FACTOR = 4;

    /** A conservative estimate to avoid our estimates going over the block size */
    protected static final float CONSERVATIVE_COMPRESSION_FACTOR = AVERAGE_COMPRESSION_FACTOR * 7 / 8;

    /** How many characters we will usually be able to fit within a BLOCK_SIZE */
    protected static final int TYPICAL_BLOCK_SIZE_CHARACTERS = (int) (BLOCK_SIZE_BYTES * CONSERVATIVE_COMPRESSION_FACTOR);

    /** The expected maximum compression factor */
    protected static final float MAX_COMPRESSION_FACTOR = 20;

    /**
     * Maximum byte size of unencoded block (we make the zip buffer one larger to
     * detect when buffer space was insufficient)
     */
    protected static final int MAX_BLOCK_SIZE_BYTES = (int) (BLOCK_SIZE_BYTES * MAX_COMPRESSION_FACTOR);

    /** Size of the (de)compressor and zipbuf pools */
    protected static final int POOL_SIZE = 10;

    /** Table of contents entry */
    static class TocEntry {

        /** content store id for this document */
        final int id;

        /** length of the encoded string in bytes */
        final int entryLengthBytes;

        /** length of the decoded string in characters */
        final int entryLengthCharacters;

        /** blocks this document is stored in */
        final int[] blockIndices;

        /** first character stored in each block */
        final int[] blockCharOffsets;

        /** was this entry deleted? (can be removed in next compacting run) */
        boolean deleted;

        TocEntry(int id, int length, int charLength, boolean deleted, int[] blockIndices,
                int[] blockCharOffsets) {
            super();
            this.id = id;
            entryLengthBytes = length;
            entryLengthCharacters = charLength;
            this.deleted = deleted;
            this.blockIndices = blockIndices;
            this.blockCharOffsets = blockCharOffsets;
        }

        /**
         * Store TOC entry in the TOC file
         *
         * @param buf where to serialize to
         */
        public void serialize(ByteBuffer buf) {
            buf.putInt(id);
            buf.putInt(entryLengthBytes);
            buf.putInt(deleted ? -1 : entryLengthCharacters);
            buf.putInt(blockIndices.length);
            IntBuffer ib = buf.asIntBuffer();
            ib.put(blockIndices);
            ib.put(blockCharOffsets);
            ((Buffer)buf).position(buf.position() + blockIndices.length * Integer.BYTES * 2);
        }

        /**
         * Read TOC entry from the TOC file
         *
         * @param buf the buffer to read from
         * @return new TocEntry
         */
        public static TocEntry deserialize(ByteBuffer buf) {
            int id = buf.getInt();
            int length = buf.getInt();
            int charLength = buf.getInt();
            boolean deleted = charLength < 0;
            int nBlocks = buf.getInt();
            int[] blockIndices = new int[nBlocks];
            int[] blockCharOffsets = new int[nBlocks];
            IntBuffer ib = buf.asIntBuffer();
            ib.get(blockIndices);
            ib.get(blockCharOffsets);
            ((Buffer)buf).position(buf.position() + blockIndices.length * Integer.BYTES * 2);
            return new TocEntry(id, length, charLength, deleted, blockIndices, blockCharOffsets);
        }

        /**
         * Get the offset of the first byte of the specified block.
         *
         * @param blockNumber which block?
         * @return byte offset of the first byte in the file
         */
        public int getBlockNumber(int blockNumber) {
            return blockIndices[blockNumber];
        }

        /**
         * Size of this entry serialized
         *
         * @return the size in bytes
         */
        public int sizeBytes() {
            return 4 * Integer.BYTES + blockIndices.length * Integer.BYTES * 2;
        }
    }

    /**
     * The TOC entries
     */
    protected final MutableIntObjectMap<TocEntry> toc;

    /**
     * The table of contents (TOC) file
     */
    protected final File tocFile;

    /**
     * Memory mapping of the TOC file
     */
    protected ByteBuffer tocFileBuffer;

    /**
     * The TOC file channel.
     */
    protected FileChannel tocFileChannel;

    /**
     * The TOC random access file
     */
    protected RandomAccessFile tocRaf;

    /** Next content ID */
    protected int nextId = 1;

    /** The file containing all the original file contents */
    protected final File contentsFile;

    /** Total number of blocks in the contents file */
    protected int totalBlocks;

    /** The sorted list of free blocks in the contents file */
    protected final IntArrayList freeBlocks = new IntArrayList();

    protected final SimpleResourcePool<byte[]> zipbufPool;

    protected boolean initialized = false;

    protected ContentStoreFixedBlock(File dir) {
        super(dir);
        tocFile = new File(dir, TOC_FILE_NAME);
        contentsFile = new File(dir, CONTENTS_FILE_NAME);
        toc = IntObjectMaps.mutable.empty();
        zipbufPool = new SimpleResourcePool<>(POOL_SIZE) {
            @Override
            public byte[] createResource() {
                return new byte[MAX_BLOCK_SIZE_BYTES + 1]; // one larger to detect when buffer space was insufficient
            }
        };
    }

    @Override
    public synchronized final void initialize() {
        if (initialized)
            return;
        performInitialization();
        initialized = true;
    }

    protected abstract void performInitialization();

    @Override
    public void close() {
        if (!initialized)
            initialize();
        if (zipbufPool != null)
            zipbufPool.close();
    }

    protected void closeMappedToc() {
        if (tocFileBuffer == null)
            return; // not mapped
        try {
            tocFileChannel.close();
            tocFileChannel = null;
            tocRaf.close();
            tocRaf = null;

            tocFileBuffer = null;

        } catch (IOException e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    protected abstract void mapToc(boolean writable, boolean growBuffer) throws IOException;

    /**
     * Read the table of contents from the file
     */
    protected synchronized void readToc() {
        toc.clear();
        try {
            mapToc(false, false);
            try {
                ((Buffer)tocFileBuffer).position(0);
                int n = tocFileBuffer.getInt();
                totalBlocks = 0;
                for (int i = 0; i < n; i++) {
                    TocEntry e = TocEntry.deserialize(tocFileBuffer);
                    toc.put(e.id, e);

                    // Keep track of the number of blocks
                    for (int bl : e.blockIndices) {
                        if (bl > totalBlocks - 1)
                            totalBlocks = bl + 1;
                    }

                    // Keep track of what the next ID should be
                    if (e.id + 1 > nextId)
                        nextId = e.id + 1;
                }
            } finally {
                closeMappedToc();
            }

            // Determine occupied blocks
            boolean[] blockOccupied = new boolean[totalBlocks]; // automatically initialized to false
            int numOccupied = 0;
            for (TocEntry e : toc) {
                for (int bl : e.blockIndices) {
                    blockOccupied[bl] = true;
                    numOccupied++;
                }
            }
            // Build the list of free blocks
            freeBlocks.clear();
            freeBlocks.ensureCapacity(totalBlocks - numOccupied);
            for (int i = 0; i < totalBlocks; i++) {
                if (!blockOccupied[i])
                    freeBlocks.add(i);
            }

        } catch (IOException e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    @Override
    public Set<Integer> idSet() {
        if (!initialized)
            initialize();
        return CollUtil.toJavaSet(toc.keySet());
    }

    @Override
    public boolean isDeleted(int id) {
        if (!initialized)
            initialize();
        return toc.get(id).deleted;
    }

    @Override
    public int docLength(int id) {
        if (!initialized)
            initialize();
        return toc.get(id).entryLengthCharacters;
    }

    protected void setStoreType() {
        setStoreType(CONTENT_STORE_TYPE_NAME, CURRENT_VERSION);
    }


}
