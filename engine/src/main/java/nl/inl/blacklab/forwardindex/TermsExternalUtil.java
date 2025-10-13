package nl.inl.blacklab.forwardindex;

import java.io.File;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.IntBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;

import org.apache.commons.lang3.tuple.Pair;

/**
 * Some constants and a utility function for dealing with
 * external terms files.
 */
public abstract class TermsExternalUtil {

    /**
     * We set this to a lower value on Windows because we can't properly truncate
     * the file due to the file still being mapped (there is no clean way to unmap a
     * mapped file in Java, and Windows doesn't allow truncating a mapped file). The
     * lower value on Windows prevents too much wasted space.
     */
    protected static final int DEFAULT_MAX_FILE_MAP_SIZE = File.separatorChar == '\\' ? 100_000_000 : Integer.MAX_VALUE - 100;

    /**
     * The maximum block size to use while writing the terms file. Usually around
     * the limit of 2GB, but for testing, we can set this to a lower value. Note
     * that this should be significantly larger than maxBlockSize, because we also
     * need to store offsets.
     */
    protected static final int MAX_FILE_MAP_SIZE = DEFAULT_MAX_FILE_MAP_SIZE;

    /**
     * Read term strings from the given file.
     *
     * @param fc file to read term strings from
     * @param fileLength length of the file
     * @return an IntBuffer where we can continue to read from the file (just mapped), and the term strings
     */
    protected static Pair<IntBuffer, String[]> readTermsFromFileChannel(FileChannel fc, long fileLength) throws
            IOException {
        long fileMapStart = 0;
        long fileMapLength = Math.min(MAX_FILE_MAP_SIZE, fileLength);
        MappedByteBuffer buf = fc.map(MapMode.READ_ONLY, fileMapStart, fileMapLength);

        int n = buf.getInt();
        IntBuffer ib = buf.asIntBuffer();
        int[] termStringOffsets = new int[n + 1];
        String[] terms = new String[n];

        // "New" format, multiple blocks of term strings if necessary, so term strings may total over 2 GB.
        // This is the only supported format now.

        // Read the term string offsets and string data block
        int currentTerm = 0;
        while (currentTerm < n) {

            int numTermsThisBlock = ib.get();
            ib.get(termStringOffsets, currentTerm, numTermsThisBlock); // term string offsets

            // Read term strings data
            int dataBlockSize = termStringOffsets[currentTerm + numTermsThisBlock] = ib.get();
            buf.position(buf.position() + Integer.BYTES * (numTermsThisBlock + 2));
            byte[] termStringsThisBlock = new byte[dataBlockSize];
            buf.get(termStringsThisBlock);

            // Now instantiate String objects from the offsets and byte data
            int firstTermInBlock = currentTerm;
            for (; currentTerm < firstTermInBlock + numTermsThisBlock; currentTerm++) {
                int offset = termStringOffsets[currentTerm];
                int length = termStringOffsets[currentTerm + 1] - offset;
                String str = new String(termStringsThisBlock, offset, length, Terms.TERMS_CHARSET);

                // We need to find term for id while searching
                terms[currentTerm] = str;
            }

            // Re-map a new part of the file before we read the next block.
            // (and before we read the sort buffers)
            long bytesRead = buf.position();
            fileMapStart += bytesRead;
            fileMapLength = Math.min(MAX_FILE_MAP_SIZE, fileLength - fileMapStart);
            if (fileMapLength > 0) {
                buf = fc.map(MapMode.READ_ONLY, fileMapStart, fileMapLength);
                ib = buf.asIntBuffer();
            }
        }
        return Pair.of(ib, terms);
    }


    public static TermsReader openForReading(Collators collators, File termsFile) {
        return new TermsReader(collators, termsFile);
    }

    public static TermsWriter openForWriting(Collators collators, File termsFile) {
        return new TermsWriter(collators, termsFile);
    }
}
