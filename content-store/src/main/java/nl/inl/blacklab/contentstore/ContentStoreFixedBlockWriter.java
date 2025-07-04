package nl.inl.blacklab.contentstore;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.Arrays;
import java.util.zip.Deflater;

import org.eclipse.collections.api.iterator.IntIterator;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

import net.jcip.annotations.NotThreadSafe;
import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.util.SimpleResourcePool;
import nl.inl.util.TextContent;

/**
 * Store string content by id in a compound file and a TOC file. Quickly
 * retrieve (parts of) the string content.
 *
 * Stores files in a file containing fixed-length (4K) blocks of zipped UTF-8. A
 * file allocation table keeps track of each file's blocks as well as the
 * character offset associated with each block so we can quickly access the
 * data. Free blocks will be re-used to save space.
 *
 * Thread-safety: not thread-safe in index mode, but thread-safe while searching
 */
@NotThreadSafe // in index mode
public class ContentStoreFixedBlockWriter extends ContentStoreFixedBlock {

    /** Name of the version file */
    private static final String VERSION_FILE_NAME = "version.dat";


    /** How many available characters will trigger a block write. */
    private static final int WRITE_BLOCK_WHEN_CHARACTERS_AVAILABLE = (int) (BLOCK_SIZE_BYTES * MAX_COMPRESSION_FACTOR);

    /**
     * How much to reserve at the end of mapped file for writing
     */
    private int writeMapReserve = 1_000_000; // 1M

    /**
     * Set the size of the write reserve (the amount of space allocated at the end
     * of the file). Larger reserves means less re-mapping.
     *
     * The default is 1M bytes.
     *
     * @param writeMapReserve size of the reserve in bytes.
     */
    public void setWriteMapReserve(int writeMapReserve) {
        this.writeMapReserve = writeMapReserve;
    }

    /** Handle into the contents file */
    RandomAccessFile rafContentsFile;

    /** Channel into the contents file */
    FileChannel fchContentsFile;

    /**
     * Keeps track of how many chars were in the blocks we've already written. Used
     * by store() to calculate the total content length in chars.
     */
    private int charsFromEntryWritten = 0;

    /**
     * If we're writing content in chunks, this keeps track of how many bytes were
     * already written. Used by store() to calculate the total content length in
     * bytes.
     */
    private int bytesWritten = 0;

    /**
     * Keeps track of the block ids we've stored parts the current file in so far
     */
    private final MutableIntList blockIndicesWhileStoring;

    /** Keeps track of the char offsets of the blocks of the current file so far */
    private final MutableIntList blockCharOffsetsWhileStoring;

    /** If true, the toc file should be updated dat the end */
    private boolean tocModified = false;

    /** Contents still waiting to be written to the contents file in blocks */
    StringBuilder unwrittenContents = new StringBuilder(BLOCK_SIZE_BYTES * 10);

    /** Index of the first unwritten character in unwrittenContents */
    protected int unwrittenIndex = 0;

    /**  unwritten content buffer is not flushed immediately after writing, as that is very slow in some situations (large documents in particular) */
    protected static final int MAX_UNWRITTEN_INDEX = BLOCK_SIZE_BYTES * 8096;

    /** Used to pad blocks that are less than BLOCK_SIZE long */
    private final byte[] blockPadding = new byte[BLOCK_SIZE_BYTES];

    SimpleResourcePool<Deflater> compresserPool;

    /**
     * @param dir content store dir
     * @param create if true, create a new content store
     */
    public ContentStoreFixedBlockWriter(File dir, boolean create) throws ErrorOpeningIndex {
        super(dir);
        if (!dir.exists() && !dir.mkdir())
            throw new ErrorOpeningIndex("Could not create dir: " + dir);
        if (create && tocFile.exists()) {
            // Delete the ContentStore files
            if (!tocFile.delete())
                throw new ErrorOpeningIndex("Could not delete TOC file: " + tocFile);
            File versionFile = new File(dir, VERSION_FILE_NAME);
            if (versionFile.exists() && !versionFile.delete())
                throw new ErrorOpeningIndex("Could not delete version file: " + tocFile);
            if (contentsFile.exists() && !contentsFile.delete())
                throw new ErrorOpeningIndex("Could not delete contents file: " + contentsFile);

            // Also delete old content store format files if present
            File[] dataFiles = dir.listFiles((dir1, name) -> name.matches("data\\d+.dat"));
            if (dataFiles == null)
                throw new ErrorOpeningIndex("Error finding old data files in content store dir: " + dir);
            for (File f : dataFiles) {
                if (!f.delete())
                    throw new ErrorOpeningIndex("Could not delete data file: " + f);
            }
        }
        if (tocFile.exists())
            readToc();
        tocModified = false;
        if (create) {
            try {
                clear();
            } catch (IOException e) {
                throw new ErrorOpeningIndex("Could not clear content store", e);
            }
            if (tocFile.exists() && !tocFile.delete())
                throw new ErrorOpeningIndex("Could not delete file: " + tocFile);
            setStoreType();
        }
        blockIndicesWhileStoring = new IntArrayList();
        blockCharOffsetsWhileStoring = new IntArrayList();

        compresserPool = new SimpleResourcePool<>(POOL_SIZE) {
            @Override
            public Deflater createResource() {
                return new Deflater();
            }

            @Override
            public void destroyResource(Deflater resource) {
                resource.end();
            }
        };
    }

    /**
     * Delete all content in the document store
     */
    @Override
    public void clear() throws IOException {
        closeContentsFile();

        // delete contents file and empty TOC
        if (contentsFile.exists() && !contentsFile.delete())
            throw new IOException("Could not delete file: " + contentsFile);
        toc.clear();
        freeBlocks.clear();
        tocModified = true;
        nextId = 1;
    }

    @Override
    protected void mapToc(boolean writeable, boolean growBuffer) throws IOException {
        tocRaf = new RandomAccessFile(tocFile, writeable ? "rw" : "r");
        long fl = tocFile.length();
        if (!writeable && growBuffer) {
            throw new UnsupportedOperationException("Can not grow toc buffer in read mode");
        }
        if (growBuffer || fl == 0) {
            fl += writeMapReserve;
        } // leave 1M room at the end
        tocFileChannel = tocRaf.getChannel();
        tocFileBuffer = tocFileChannel.map(writeable ? MapMode.READ_WRITE : MapMode.READ_ONLY, 0, fl);
    }

    private void writeToc() {
        try {
            mapToc(true, false);
            tocFileBuffer.putInt(toc.size());
            try {
                for (TocEntry e : toc.values()) {
                    if (tocFileBuffer.remaining() < e.sizeBytes()) {
                        // Close and re-open with extra writing room
                        int p = tocFileBuffer.position();
                        closeMappedToc();
                        mapToc(true, true);
                        tocFileBuffer.position(p);
                    }
                    e.serialize(tocFileBuffer);
                }
            } finally {
                closeMappedToc();
            }
        } catch (IOException e) {
            throw BlackLabException.wrapRuntime(e);
        }
        tocModified = false;
    }

    /**
     * Close the content store. Writes the table of contents (if modified)
     */
    @Override
    public void close() {
        compresserPool.close();
        closeContentsFile();
        if (tocModified) {
            writeToc();
        }
        closeMappedToc();
        super.close();
    }

    /**
     * Encode and write the block we've compiled so far and reset for next block
     *
     * @param writeLastBlock if true, we'll write the last block too even if it's
     *            not full
     */
    private void writeBlocks(boolean writeLastBlock) {
        ensureContentsFileOpen();

        // Do we have a block to write?
        while (writeLastBlock && getUnwrittenCharCount() > 0
                || getUnwrittenCharCount() >= WRITE_BLOCK_WHEN_CHARACTERS_AVAILABLE) {
            int offsetBefore = unwrittenIndex;
            byte[] encoded = encodeBlock(); // encode a number of characters to produce a 4K block
            int offsetAfter = unwrittenIndex;
            int charLen = offsetAfter - offsetBefore;
            int blockIndex = writeToFreeBlock(encoded);
            blockIndicesWhileStoring.add(blockIndex);
            blockCharOffsetsWhileStoring.add(charsFromEntryWritten);
            charsFromEntryWritten += charLen;
            bytesWritten += encoded.length;

            if (unwrittenIndex > MAX_UNWRITTEN_INDEX) {
                this.unwrittenContents.delete(0, unwrittenIndex);
                unwrittenIndex = 0;
            }
        }

        // Free memory if unwrittenContents gets too large
        if (getUnwrittenCharCount() == 0 && unwrittenContents.capacity() >= MAX_UNWRITTEN_INDEX) {
            this.unwrittenContents = new StringBuilder(BLOCK_SIZE_BYTES*10);
            unwrittenIndex = 0;
        }
    }

    private int getUnwrittenCharCount() {
        return this.unwrittenContents.length() - this.unwrittenIndex;
    }

    /**
     * Writes the block data to a free block and returns the block number.
     *
     * @param encoded the block data
     * @return the block number
     */
    private int writeToFreeBlock(byte[] encoded) {
        int freeBlock;
        if (freeBlocks.size() == 0) {
            // Add a new one at the end
            totalBlocks++;
            freeBlock = totalBlocks - 1;
        } else {
            // Take the first from the list
            freeBlock = freeBlocks.removeAtIndex(0);
        }
        long offset = (long) freeBlock * BLOCK_SIZE_BYTES;

        // Write data to the block
        try {
            fchContentsFile.position(offset);
            ByteBuffer buf = ByteBuffer.wrap(encoded);
            fchContentsFile.write(buf);
            // pad block with garbage
            buf = ByteBuffer.wrap(blockPadding, 0, BLOCK_SIZE_BYTES - encoded.length);
            fchContentsFile.write(buf);
            return freeBlock;
        } catch (IOException e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    /**
     * Store part of a piece of large content. This may be called several times to
     * store chunks of content, but MUST be *finished* by calling the "normal"
     * store() method. You may call store() with the empty string if you wish.
     *
     * @param content the content to store
     */
    @Override
    public synchronized void storePart(TextContent content) {
        if (content.isEmpty())
            return;

        content.appendToStringBuilder(unwrittenContents);
        writeBlocks(false);
    }

    /**
     * Store the given content and assign an id to it.
     *
     * Parts of the document may already have been stored before. This is the final part and will
     * assign and return the document's content id.
     *
     * @param content the content to store
     * @return the id assigned to the content
     */
    @Override
    public synchronized int store(TextContent content) {
        storePart(content);
        return store();
    }

    /** The store routine (after appending to unwrittenContents) */
    private int store() {
        if (getUnwrittenCharCount() > 0) {
            // Write the last (not completely full) block
            writeBlocks(true);
        }

        // Convert lists to arrays of primitives for storing
        int[] blockIndices = new int[blockIndicesWhileStoring.size()];
        int i = 0;
        IntIterator it = blockIndicesWhileStoring.intIterator();
        while (it.hasNext()) {
            blockIndices[i] = it.next();
            i++;
        }
        int[] blockCharOffsets = new int[blockCharOffsetsWhileStoring.size()];
        i = 0;
        it = blockCharOffsetsWhileStoring.intIterator();
        while (it.hasNext()) {
            blockCharOffsets[i] = it.next();
            i++;
        }

        TocEntry e = new TocEntry(nextId, bytesWritten, charsFromEntryWritten, false, blockIndices, blockCharOffsets);
        nextId++;
        toc.put(e.id, e);
        tocModified = true;
        charsFromEntryWritten = 0;
        bytesWritten = 0;
        blockIndicesWhileStoring.clear();
        blockCharOffsetsWhileStoring.clear();
        return e.id;
    }

    private void ensureContentsFileOpen() {
        try {
            if (rafContentsFile == null) {
                File theContentsFile = new File(dir, CONTENTS_FILE_NAME);
                rafContentsFile = new RandomAccessFile(theContentsFile, "rw");
                fchContentsFile = rafContentsFile.getChannel();
            }
        } catch (FileNotFoundException e) {
            throw new InvalidIndex("Contents file not found" + CONTENTS_FILE_NAME, e);
        }
    }

    private void closeContentsFile() {
        try {
            if (rafContentsFile != null) {
                fchContentsFile.close();
                fchContentsFile = null;
                rafContentsFile.close();
                rafContentsFile = null;
            }
        } catch (IOException e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    /**
     * Retrieve content with given id
     *
     * @param id the id
     * @return the string
     */
    @Override
    public String retrieve(int id) {
        throw new UnsupportedOperationException("Not supported in index mode");
    }

    @Override
    public String[] retrieveParts(int contentId, int[] start, int[] end) {
        throw new UnsupportedOperationException("Not supported in index mode");
    }

    @Override
    public synchronized void delete(int id) {
        TocEntry e = toc.get(id);
        e.deleted = true;
        for (int bl : e.blockIndices) {
            freeBlocks.add(bl);
        }
        freeBlocks.sortThis();
        tocModified = true;
    }

    protected byte[] encodeBlock() {

        int length = TYPICAL_BLOCK_SIZE_CHARACTERS;
        int available = getUnwrittenCharCount();
        if (length > available)
            length = available;

        Deflater compresser = compresserPool.acquire();
        byte[] zipbuf = zipbufPool.acquire();
        boolean doMinCheck = true;
        try {
            while (true) {

                // Serialize to bytes
                byte[] encoded;
                while (true) {
                    encoded = unwrittenContents.substring(this.unwrittenIndex, this.unwrittenIndex + length).getBytes(DEFAULT_CHARSET);

                    // Make sure the block fits in our zip buffer
                    if (encoded.length <= MAX_BLOCK_SIZE_BYTES)
                        break;
                    // Doesn't fit; make it a little smaller until it does fit.
                    float shrinkFactor = 1.0f + (1.05f * (encoded.length - MAX_BLOCK_SIZE_BYTES)) / BLOCK_SIZE_BYTES;
                    length = (int) (length / shrinkFactor);
                    doMinCheck = false;
                }

                // Compress
                compresser.reset();
                compresser.setInput(encoded);
                compresser.finish();
                int compressedDataLength = compresser.deflate(zipbuf, 0, zipbuf.length, Deflater.FULL_FLUSH);
                if (compressedDataLength <= 0) {
                    throw new InvalidIndex("Error, deflate returned " + compressedDataLength);
                }
                if (compressedDataLength == zipbuf.length) {
                    throw new InvalidIndex(
                            "Error, deflate returned size of zipbuf, this indicates insufficient space");
                }

                if (compressedDataLength > BLOCK_SIZE_BYTES) {
                    // Compressed block too large.
                    // Shrink the uncompressed data length by 5% more than what we expect to be required.
                    float shrinkFactor = 1.0f + (1.05f * (compressedDataLength - BLOCK_SIZE_BYTES)) / BLOCK_SIZE_BYTES;
                    length = (int) (length / shrinkFactor);
                    if (length <= 0)
                        length = 1;
                    doMinCheck = false; // prevent oscillation between enlarging and shrinking
                } else if (doMinCheck && length < available && compressedDataLength < MINIMUM_ACCEPTABLE_BLOCK_SIZE) {
                    // Compressed block too small.
                    // Grow the uncompressed data length by 5% less than what we expect is possible.
                    float growFactor = 1.0f
                            + (0.95f * (BLOCK_SIZE_BYTES - compressedDataLength)) / compressedDataLength;
                    length = (int) (length * growFactor);
                    if (length > available)
                        length = available;
                } else {
                    // NOTE: do not delete from unwrittenContents here,
                    // call site needs to know how much we advanced in the buffer to calculate how much uncompressed data was used
                    this.unwrittenIndex += length;
                    return Arrays.copyOfRange(zipbuf, 0, compressedDataLength);
                }
            }
        } finally {
            compresserPool.release(compresser);
            zipbufPool.release(zipbuf);
        }
    }

    @Override
    protected void performInitialization() {
        // NOP
    }

}
