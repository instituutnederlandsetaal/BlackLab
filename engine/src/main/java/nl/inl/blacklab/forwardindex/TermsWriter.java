
package nl.inl.blacklab.forwardindex;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.Buffer;
import java.nio.IntBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.text.CollationKey;
import java.text.Collator;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.collections.api.set.primitive.MutableIntSet;

import net.jcip.annotations.NotThreadSafe;
import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

/**
 * Keeps a first-come-first-serve list of unique terms. Each term gets a unique
 * index number. These numbers are stored in the forward index to conserve space
 * and allow quick lookups of terms occurring in specific positions.
 *
 * This version of the class stores the terms in a more efficient way so it
 * saves and loads faster, and includes the case-insensitive sorting order.
 *
 * This implementation is not thread-safe.
 */
@NotThreadSafe
class TermsWriter implements Terms {

    protected static final Logger logger = LogManager.getLogger(TermsWriter.class);

    /** Maximum size for blocks of term strings. */
    private static final int DEFAULT_MAX_BLOCK_SIZE = TermsExternalUtil.DEFAULT_MAX_FILE_MAP_SIZE;

    /**
     * Number of sort buffers we store in the terms file (case-sensitive/insensitive
     * and inverted buffers for both as well)
     */
    private static final int NUM_SORT_BUFFERS = 4;

    /**
     * Mapping from term to its unique index number. We use a SortedMap because we
     * wish to store the sorted index numbers later (to speed up sorting).
     */
    private final Map<CollationKey, Integer> termIndex;

    /**
     * The maximum block size to use while writing the terms file. Usually around
     * the limit of 2GB, but for testing, we can set this to a lower value.
     */
    private int maxBlockSize = DEFAULT_MAX_BLOCK_SIZE;

    /**
     * Collator to use for string comparisons
     */
    private final Collator collator;

    /**
     * Collator to use for insensitive string comparisons
     */
    private final Collator collatorInsensitive;

    TermsWriter(Collators collators, File termsFile) {
        this.collator = collators.get(MatchSensitivity.SENSITIVE);
        this.collatorInsensitive = collators.get(MatchSensitivity.INSENSITIVE);

        // Index mode: create a SortedMap based on the specified Collator.
        // (used later to get the terms in sort order)
        this.termIndex = new TreeMap<>();

        String[] terms;
        if (termsFile != null && termsFile.exists()) {
            terms = readTermStrings(termsFile);
        } else {
            terms = new String[0];
        }

        // We need to find id for term quickly while indexing
        // Build the case-sensitive term index.
        for (int i = 0; i < terms.length; i++) {
            termIndex.put(collator.getCollationKey(terms[i]), i);
        }
    }

    @Override
    public int indexOf(String term) {
        synchronized (this) {
            // Yes, use the available term index.
            CollationKey key = this.collator.getCollationKey(term);
            Integer index = termIndex.get(key);
            if (index != null)
                return index;
            index = termIndex.size();
            termIndex.put(key, index);
            return index;
        }
    }

    @Override
    public void indexOf(MutableIntSet results, String term, MatchSensitivity sensitivity) {
        throw new UnsupportedOperationException("Not available during indexing");
    }

    @Override
    public boolean termsEqual(int[] termId, MatchSensitivity sensitivity) {
        throw new UnsupportedOperationException("Not available during indexing");
    }

    private synchronized String[] readTermStrings(File termsFile) {
        try {
            try (RandomAccessFile raf = new RandomAccessFile(termsFile, "r")) {
                try (FileChannel fc = raf.getChannel()) {
                    long fileLength = termsFile.length();
                    Pair<IntBuffer, String[]> termsResult = TermsExternalUtil.readTermsFromFileChannel(fc, fileLength);
                    return termsResult.getRight();
                }
            }
        } catch (IOException e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    public synchronized void write(File termsFile) {
        try {
            // Open the terms file
            try (RandomAccessFile raf = new RandomAccessFile(termsFile, "rw")) {
                try (FileChannel fc = raf.getChannel()) {
                    int n = termIndex.size();

                    // Fill the terms[] array
                    String[] terms = new String[n];
                    long termStringsByteSize = 0;
                    for (Map.Entry<CollationKey, Integer> entry : termIndex.entrySet()) {
                        String term = entry.getKey().getSourceString();
                        terms[entry.getValue()] = term;
                        termStringsByteSize += term.getBytes(TERMS_CHARSET).length;
                    }

                    // Calculate the file length and map the file
                    MappedByteBuffer buf;
                    IntBuffer ib;

                    // "block-based" version of terms file that can grow larger than 2 GB.
                    // (this is now the only supported version)

                    long fileMapStart = 0, fileMapLength = TermsExternalUtil.MAX_FILE_MAP_SIZE;
                    buf = fc.map(MapMode.READ_WRITE, fileMapStart, fileMapLength);
                    buf.putInt(n); // Start with the number of terms      //@4
                    ib = buf.asIntBuffer();
                    long fileLength = Integer.BYTES;

                    // Terms file is too large to fit in a single byte array.
                    // Use the new code.
                    int currentTerm = 0;
                    long bytesLeftToWrite = termStringsByteSize;
                    int[] termStringOffsets = new int[n];
                    while (currentTerm < n) {
                        int firstTermInBlock = currentTerm;
                        int blockSize = (int) Math.min(bytesLeftToWrite, maxBlockSize);

                        // Calculate byte offsets for all the terms and fill data array
                        int currentOffset = 0;
                        byte[] termStrings = new byte[blockSize];
                        long blockSizeBytes = (long)2 * Integer.BYTES;
                        while (currentTerm < n) {
                            termStringOffsets[currentTerm] = currentOffset;
                            byte[] termBytes = terms[currentTerm].getBytes(TERMS_CHARSET);
                            long newBlockSizeBytes = blockSizeBytes + Integer.BYTES + termBytes.length; // block grows by 1 offset and this term's bytes
                            if (newBlockSizeBytes > maxBlockSize) {
                                // Block is full. Write it and continue with next block.
                                break;
                            }
                            System.arraycopy(termBytes, 0, termStrings, currentOffset, termBytes.length);
                            currentOffset += termBytes.length;
                            currentTerm++;
                            bytesLeftToWrite -= termBytes.length;
                            blockSizeBytes = newBlockSizeBytes;
                        }

                        int numTermsThisBlock = currentTerm - firstTermInBlock;

                        // Write offset and data arrays to file
                        if (blockSizeBytes < 0) { // DEBUG, SHOULD NEVER HAPPEN
                            logger.error("***** blockSizeBytes < 0 !!!");
                            logger.error("blockSizeBytes = " + blockSizeBytes);
                            logger.error("n = " + n);
                            logger.error("numTermsThisBlock = " + numTermsThisBlock);
                            logger.error("  currentTerm = " + currentTerm);
                            logger.error("  firstTermInBlock = " + firstTermInBlock);
                            logger.error("currentOffset = " + currentOffset);
                        }

                        ib.put(numTermsThisBlock); //@4
                        ib.put(termStringOffsets, firstTermInBlock, numTermsThisBlock); //@4 * numTermsThisBlock
                        ib.put(currentOffset); // include the offset after the last term at position termStringOffsets[n]
                                               // (doubles as the size of the data block to follow) //@4
                        int newPosition = buf.position() + Integer.BYTES * (2 + numTermsThisBlock);
                        ((Buffer)buf).position(newPosition); // advance past offsets array
                        if (fileMapLength - buf.position() < blockSize) {
                            //throw new RuntimeException("Not enough space in file mapping to write term strings!");

                            // Re-map a new part of the file before we write the term strings
                            fileMapStart += buf.position();
                            buf = fc.map(MapMode.READ_WRITE, fileMapStart, fileMapLength);
                        }
                        buf.put(termStrings, 0, currentOffset); //@blockSize (max. maxBlockSize)
                        fileLength += blockSizeBytes;

                        // Re-map a new part of the file before we write the next block.
                        // (and eventually, the sort buffers, see below)
                        fileMapStart += buf.position();
                        buf = fc.map(MapMode.READ_WRITE, fileMapStart, fileMapLength);
                        ib = buf.asIntBuffer();
                    }

                    // Determine total file length (by adding the sort buffer byte length to the
                    // running total) and truncate the file if necessary
                    // (we can do this now, even though we still have to write the sort buffers,
                    // because we know how large the file will eventually be)
                    fileLength += NUM_SORT_BUFFERS * Integer.BYTES * (long)n;

                    if (fileLength < 0) { // DEBUG, SHOULD NEVER HAPPEN
                        logger.error("***** fileLength < 0 !!!");
                        logger.error("fileLength = " + fileLength);
                        logger.error("n = " + n);
                    }

                    if (File.separatorChar != '\\') // causes problems on Windows
                        fc.truncate(fileLength);

                    // Write the case-sensitive sort order
                    // Because termIndex is a SortedMap, values are returned in key-sorted order.
                    // In other words, the index numbers are in order of sorted terms, so the id
                    // for 'aardvark' comes before the id for 'ape', etc.
                    int i = 0;
                    int[] sortPositionPerId = new int[n];
                    Integer[] insensitive = new Integer[n];
                    for (int id : termIndex.values()) {
                        sortPositionPerId[id] = i;
                        insensitive[i] = id; // fill this so we can re-sort later, faster b/c already partially sorted
                        i++;
                    }
                    ib.put(new int[n]); // NOT USED ANYMORE, JUST FOR FILE COMPATIBILITY
                    ib.put(sortPositionPerId);

                    // Now, sort case-insensitively and write those arrays as well
                    Arrays.sort(insensitive, (a, b) -> collatorInsensitive.compare(terms[a], terms[b]));
                    // Copy into the sortPositionPerIdInsensitive array, making sure that
                    // identical values get identical sort positions!
                    int[] sortPositionPerIdInsensitive = new int[n];
                    int sortPos = 0;
                    for (i = 0; i < n; i++) {
                        if (i == 0
                                || collatorInsensitive.compare(terms[insensitive[i - 1]], terms[insensitive[i]]) != 0) {
                            // Not identical to previous value: gets its own sort position.
                            // If a value is identical to the previous one, it gets the same sort position.
                            sortPos = i;
                        }
                        sortPositionPerIdInsensitive[insensitive[i]] = sortPos;
                    }
                    ib.put(new int[n]); // NOT USED ANYMORE, JUST FOR FILE COMPATIBILITY
                    ib.put(sortPositionPerIdInsensitive);
                }
            }
        } catch (IOException e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    @Override
    public String get(int index) {
        throw new UnsupportedOperationException("Not available during indexing");
    }

    @Override
    public int numberOfTerms() {
        return termIndex.size();
    }

    @Override
    public void toSortOrder(int[] tokenId, int[] sortOrder, MatchSensitivity sensitivity) {
        throw new UnsupportedOperationException("Not available during indexing");
    }

    @Override
    public int compareSortPosition(int tokenId1, int tokenId2, MatchSensitivity sensitivity) {
        throw new UnsupportedOperationException("Not available during indexing");
    }

    @Override
    public int idToSortPosition(int id, MatchSensitivity sensitivity) {
        throw new UnsupportedOperationException("Not available during indexing");
    }

    public void setMaxBlockSize(int maxBlockSize) {
        if ((long) maxBlockSize > ((long) TermsExternalUtil.DEFAULT_MAX_FILE_MAP_SIZE))
            throw new RuntimeException("Max. block size too large, max. " + TermsExternalUtil.DEFAULT_MAX_FILE_MAP_SIZE);
        this.maxBlockSize = maxBlockSize;
    }

    @Override
    public int[] segmentIdsToGlobalIds(int ord, int[] segmentResults) {
        throw new UnsupportedOperationException("Not available during indexing");
    }

    @Override
    public int segmentIdToGlobalId(int ord, int segmentTermId) {
        throw new UnsupportedOperationException("Not available during indexing");
    }
}
