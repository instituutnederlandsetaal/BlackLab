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
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;

import net.jcip.annotations.NotThreadSafe;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.indexmetadata.Annotation;

/**
 * Keeps a forward index of documents, to quickly answer the question "what word
 * occurs in doc X at position Y"?
 *
 * This implementation is not thread-safe.
 */
@NotThreadSafe
public class AnnotationForwardIndexExternalWriter extends AnnotationForwardIndexExternalAbstract {

    protected static final Logger logger = LogManager.getLogger(AnnotationForwardIndexExternalWriter.class);

    /** Table of contents entry; stored in docs.dat */
    private static class TocEntry implements Comparable<TocEntry> {
        /** token offset in tokens.dat */
        public long offset;

        /** Number of tokens in document.
         *  NOTE: this INCLUDES the extra closing token at the end.
         */
        public int length;

        /** was this entry deleted? (remove in next compacting run) */
        public boolean deleted;

        public TocEntry(long offset, int length, boolean deleted) {
            super();
            this.offset = offset;
            this.length = length;
            this.deleted = deleted;
        }

        /**
         * Compare this entry to another (for sorting).
         *
         * @param o the entry to compare with
         * @return the comparison result
         */
        @Override
        public int compareTo(TocEntry o) {
            return Long.compare(offset, o.offset);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof TocEntry tocEntry))
                return false;
            return offset == tocEntry.offset && length == tocEntry.length && deleted == tocEntry.deleted;
        }

        @Override
        public int hashCode() {
            return Objects.hash(offset, length, deleted);
        }
    }

    /** The unique terms in our index */
    private final TermsWriter terms;

    /** The memory mapped write int buffer */
    private IntBuffer writeBuffer;

    /**
     * Buffer offset (position in file of start of writeBuffer) in integer positions
     * (so we don't count bytes, we count ints)
     */
    private long writeBufOffset;

    /** Handle for the tokens file */
    private RandomAccessFile writeTokensFp;

    /** File channel for the tokens file */
    private FileChannel writeTokensFileChannel;

    /** Has the table of contents been modified? */
    private boolean tocModified;

    /**
     * The table of contents (where documents start in the tokens file and how long
     * they are)
     */
    private List<TocEntry> toc = new ArrayList<>();

    /** Deleted TOC entries. Always sorted by size. */
    private List<TocEntry> deletedTocEntries = new ArrayList<>();

    AnnotationForwardIndexExternalWriter(IndexReader reader, Annotation annotation, File dir, Collators collators, boolean create) {
        super(reader, annotation, dir, collators);

        if (!dir.exists()) {
            if (!create)
                throw new IllegalArgumentException("ForwardIndex doesn't exist: " + dir);
            if (!dir.mkdir())
                throw new BlackLabRuntimeException("Could not create dir: " + dir);
        }

        if (create) {
            if (tokensFile.exists() && !tokensFile.delete())
                throw new BlackLabRuntimeException("Could not delete file: " + tokensFile);
            if (tocFile.exists() && !tocFile.delete())
                throw new BlackLabRuntimeException("Could not delete file: " + tocFile);
            if (termsFile.exists() && !termsFile.delete())
                throw new BlackLabRuntimeException("Could not delete file: " + termsFile);
        }
        try {
            if (tocFile.exists()) {
                readToc();
                terms = TermsExternalUtil.openForWriting(collators, termsFile);
                tocModified = false;
            } else {
                terms = TermsExternalUtil.openForWriting(collators, null);
                if (!tokensFile.createNewFile())
                    throw new BlackLabRuntimeException("Could not create file: " + tokensFile);
                tocModified = true;
            }
            // Tricks to speed up reading
            // Index mode. Open for writing.
            openTokensFileForWriting();
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }

        if (create) {
            clear();
        }
    }

    /**
     * Open the tokens file for writing.
     *
     * @throws IOException on error
     */
    protected void openTokensFileForWriting() throws IOException {
        writeTokensFp = new RandomAccessFile(tokensFile, "rw");
        writeTokensFileChannel = writeTokensFp.getChannel();
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
            long[] offset = new long[n];
            int[] length = new int[n];
            byte[] deleted = new byte[n];
            LongBuffer lb = buf.asLongBuffer();
            lb.get(offset);
            ((Buffer)buf).position(buf.position() + Long.BYTES * n);
            IntBuffer ib = buf.asIntBuffer();
            ib.get(length);
            ((Buffer)buf).position(buf.position() + Integer.BYTES * n);
            buf.get(deleted);
            toc = new ArrayList<>(n);
            deletedTocEntries = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                TocEntry e = new TocEntry(offset[i], length[i], deleted[i] != 0);
                toc.add(e);
                if (e.deleted) {
                    deletedTocEntries.add(e);
                }
                long end = e.offset + e.length;
                if (end > tokenFileEndPosition)
                    tokenFileEndPosition = end;
            }
            sortDeletedTocEntries();
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    protected void sortDeletedTocEntries() {
        deletedTocEntries.sort(Comparator.comparingInt(o -> o.length));
    }



    /**
     * Delete all content in the forward index
     */
    private void clear() {
        // delete data files and empty TOC
        try {
            if (writeTokensFp == null) {
                openTokensFileForWriting();
            }

            if (File.separatorChar != '\\') // causes problems on Windows
                writeTokensFp.setLength(0);

        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
        if (termsFile.exists() && !termsFile.delete())
            throw new BlackLabRuntimeException("Could not delete file: " + termsFile);
        if (tocFile.exists() && !tocFile.delete())
            throw new BlackLabRuntimeException("Could not delete file: " + tocFile);
        if (toc != null)
            toc.clear();
        if (deletedTocEntries != null)
            deletedTocEntries.clear();
        tokenFileEndPosition = 0;
        tocModified = true;
    }

    /**
     * Write the table of contents to the file
     */
    private void writeToc() {
        try {
            int n = toc.size();
            long[] offset = new long[n];
            int[] length = new int[n];
            byte[] deleted = new byte[n];
            int i = 0;
            for (TocEntry e : toc) {
                offset[i] = e.offset;
                length[i] = e.length;
                deleted[i] = (byte) (e.deleted ? 1 : 0);
                i++;
            }
            try (RandomAccessFile raf = new RandomAccessFile(tocFile, "rw");
                    FileChannel fc = raf.getChannel()) {
                long fileSize = Integer.BYTES + (long) (Long.BYTES + Integer.BYTES + 1) * n;
                fc.truncate(fileSize);
                MappedByteBuffer buf = fc.map(MapMode.READ_WRITE, 0, fileSize);
                buf.putInt(n);
                LongBuffer lb = buf.asLongBuffer();
                lb.put(offset);
                ((Buffer)buf).position(buf.position() + Long.BYTES * n);
                IntBuffer ib = buf.asIntBuffer();
                ib.put(length);
                ((Buffer)buf).position(buf.position() + Integer.BYTES * n);
                buf.put(deleted);
            }
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
        tocModified = false;
    }

    public void close() {
        try {
            if (tocModified) {
                writeToc();
                terms.write(termsFile);
            }

            // Close the FileChannel and RandomAccessFile
            if (writeTokensFileChannel != null) {
                // Cannot truncate if still mapped; cannot force demapping.
                if (File.separatorChar != '\\')
                    writeTokensFileChannel.truncate(tokenFileEndPosition * Integer.BYTES);
                writeTokensFileChannel.close();
            }
            if (writeTokensFp != null)
                writeTokensFp.close();

        } catch (Exception e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    /**
     * Find the best-fitting deleted entry for the specified length
     *
     * @param length length the entry should at least be
     * @return the best-fitting entry
     */
    TocEntry findBestFittingGap(int length) {
        int n = deletedTocEntries.size();

        // Are there any fitting gaps?
        if (n == 0 || deletedTocEntries.get(n - 1).length < length)
            return null;

        // Does the smallest gap fit?
        if (deletedTocEntries.get(0).length >= length)
            return deletedTocEntries.get(0);

        // Do a binary search to find the best fit
        int doesntFit = 0, bestFitSoFar = n - 1;
        while (bestFitSoFar - doesntFit > 1) {
            int newTry = doesntFit + bestFitSoFar / 2;
            if (deletedTocEntries.get(newTry).length < length)
                doesntFit = newTry;
            else
                bestFitSoFar = newTry;
        }
        return deletedTocEntries.get(bestFitSoFar);
    }

    /**
      * Store the given content and assign an id to it
      *
      * @param content the content to store
      * @return the id assigned to the content
      */
    int addDocument(List<String> content) {
        return addDocument(content, null);
    }

    /**
     * Store the given content and assign an id to it.
     *
     * Note that if more than one token occurs at any position, we only store the
     * first in the forward index.
     *
     * @param content the content to store
     * @param posIncr the associated position increments, or null if position
     *         increment is always 1.
     * @return the id assigned to the content
     */
    public synchronized int addDocument(List<String> content, List<Integer> posIncr) {
        // Calculate the total number of tokens we need to store, based on the number
        // of positions (we store 1 token per position, regardless of whether we have
        // none, one or multiple values for that position)
        int numberOfTokens;
        if (posIncr == null) {
            // No position increments given; assume always 1
            numberOfTokens = content.size();
        } else {
            // Calculate using position increments
            numberOfTokens = 0;
            for (int inc : posIncr) {
                numberOfTokens += inc;
            }
        }

        // Decide where we're going to store this document,
        // and update ToC
        TocEntry gap = findBestFittingGap(numberOfTokens);
        long newDocumentOffset;
        int mapReserve;
        tocModified = true;
        boolean addNewEntry = true;
        int newDocumentFiid = -1;
        if (gap == null) {
            // No fitting gap; just write it at the end
            newDocumentOffset = tokenFileEndPosition;
            mapReserve = WRITE_MAP_RESERVE; // if writing at end, reserve more space
        } else {
            // Found a fitting gap; write it there
            newDocumentOffset = gap.offset;
            mapReserve = 0; // don't reserve extra write space, not needed
            if (gap.length == numberOfTokens) {
                // Exact fit; delete from free list and re-use entry
                deletedTocEntries.remove(gap);
                gap.deleted = false;
                addNewEntry = false;
                newDocumentFiid = toc.indexOf(gap);
            } else {
                // Not an exact fit; calculate remaining gap and re-sort free list
                gap.offset += numberOfTokens;
                gap.length -= numberOfTokens;
                sortDeletedTocEntries();
            }
        }
        // Do we need to create a new entry for this document in the ToC?
        // (always, unless we found an exact-fitting gap)
        if (addNewEntry) {
            // See if there's an unused entry
            TocEntry smallestFreeEntry = deletedTocEntries.isEmpty() ? null : deletedTocEntries.get(0);
            if (smallestFreeEntry != null && smallestFreeEntry.length == 0) {
                // Yes; re-use
                deletedTocEntries.remove(0);
                smallestFreeEntry.offset = newDocumentOffset;
                smallestFreeEntry.length = numberOfTokens;
                smallestFreeEntry.deleted = false;
                newDocumentFiid = toc.indexOf(smallestFreeEntry);
            } else {
                // No; make new entry
                toc.add(new TocEntry(newDocumentOffset, numberOfTokens, false));
                newDocumentFiid = toc.size() - 1;
            }
        }

        try {
            // Can we use the current write buffer for this write?
            long writeBufEnd = writeBuffer == null ? 0 : writeBufOffset + writeBuffer.limit();
            if (writeBuffer == null || writeBufOffset > newDocumentOffset
                    || writeBufEnd < newDocumentOffset + numberOfTokens) {
                // No, remap it
                writeBufOffset = newDocumentOffset;
                ByteBuffer byteBuffer = writeTokensFileChannel.map(FileChannel.MapMode.READ_WRITE,
                        writeBufOffset * Integer.BYTES, (long) (numberOfTokens + mapReserve)
                                * Integer.BYTES);
                writeBuffer = byteBuffer.asIntBuffer();
            }

            // Set the correct start position
            ((Buffer)writeBuffer).position((int) (newDocumentOffset - writeBufOffset));

            // Did we increase the length of the tokens file?
            long end = newDocumentOffset + numberOfTokens;
            if (end > tokenFileEndPosition)
                tokenFileEndPosition = end;

            // Write the token ids
            // (first fill the buffer, then write the buffer in 1 call)
            int[] tokenIds = new int[numberOfTokens];
            int tokenIdsIndex = 0;
            Iterator<String> contentIt = content.iterator();
            Iterator<Integer> posIncrIt = posIncr == null ? null : posIncr.iterator();
            int emptyStringTokenId = posIncrIt != null ? terms.indexOf("") : -1;
            while (contentIt.hasNext()) {
                String token = contentIt.next();
                int pi = posIncrIt == null ? 1 : posIncrIt.next();
                if (pi == 0)
                    continue; // we only store the first token at any position
                if (pi > 1) {
                    // Skipped a few tokens; add empty tokens for these positions
                    for (int i = 0; i < pi - 1; i++) {
                        tokenIds[tokenIdsIndex] = emptyStringTokenId;
                        tokenIdsIndex++;
                    }
                }

                tokenIds[tokenIdsIndex] = terms.indexOf(token);
                tokenIdsIndex++;
            }
            if (tokenIdsIndex != numberOfTokens)
                throw new BlackLabRuntimeException(
                        "tokenIdsIndex != numberOfTokens (" + tokenIdsIndex + " != " + numberOfTokens + ")");
            writeBuffer.put(tokenIds);

            return newDocumentFiid;
        } catch (IOException e1) {
            throw BlackLabRuntimeException.wrap(e1);
        }
    }

    @Override
    public synchronized List<int[]> retrievePartsInt(int docId, int[] start, int[] end) {
        // Ideally we shouldn't have this duplicate here, but we use this during tests.
        // Better to refactor reader/writer to share this code.
        //throw new UnsupportedOperationException("Writer doesn't read!");
        try {
            int fiid = docId2fiid(docId);
            TocEntry e = toc.get(fiid);
            if (e == null || e.deleted)
                return null;

            int n = start.length;
            if (n != end.length)
                throw new IllegalArgumentException("start and end must be of equal length");
            List<int[]> result = new ArrayList<>(n);

            for (int i = 0; i < n; i++) {
                if (start[i] == -1)
                    start[i] = 0;
                if (end[i] == -1)
                    end[i] = e.length;
                if (start[i] < 0 || end[i] < 0) {
                    throw new IllegalArgumentException("Illegal values, start = " + start[i] + ", end = "
                            + end[i]);
                }
                if (end[i] > e.length) // Can happen while making KWICs because we don't know the
                                       // doc length until here
                    end[i] = e.length;
                if (start[i] > e.length || end[i] > e.length) {
                    throw new IllegalArgumentException("Value(s) out of range, start = " + start[i]
                            + ", end = " + end[i] + ", content length = " + e.length);
                }
                if (end[i] <= start[i]) {
                    throw new IllegalArgumentException(
                            "Tried to read empty or negative length snippet (from " + start[i]
                                    + " to " + end[i] + ")");
                }

                // Get an IntBuffer to read the desired content
                IntBuffer ib;

                int snippetLength = end[i] - start[i];
                int[] snippet = new int[snippetLength];
                // Chunks are not mapped (index mode).
                // Explicitly read the part we require from disk into an int buffer.
                long offset = e.offset + start[i];

                int bytesToRead = snippetLength * Integer.BYTES;
                ByteBuffer buffer = ByteBuffer.allocate(bytesToRead);
                int bytesRead = writeTokensFileChannel.read(buffer, offset * Integer.BYTES);
                if (bytesRead < bytesToRead) {
                    throw new BlackLabRuntimeException("Not enough bytes read: " + bytesRead
                            + " < " + bytesToRead);
                }
                ((Buffer)buffer).position(0);
                ib = buffer.asIntBuffer();
                ib.get(snippet);
                result.add(snippet);
            }

            return result;
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    public void deleteDocument(int fiid) {
        TocEntry tocEntry = toc.get(fiid);
        tocEntry.deleted = true;
        deletedTocEntries.add(tocEntry); // NOTE: mergeAdjacentDeletedEntries takes care of re-sorting
        mergeAdjacentDeletedEntries();
        tocModified = true;
    }

    public void deleteDocumentByLuceneDoc(Document d) {
        deleteDocument(Integer.parseInt(d.get(annotation().forwardIndexIdField())));
    }

    /**
     * Check if we can merge two (or more) deleted entries to create a larger gap,
     * and do so.
     *
     * Also takes care of truncating the file if there are deleted entries at the
     * end.
     */
    private void mergeAdjacentDeletedEntries() {
        // Sort by offset, so we can find adjacent entries
        deletedTocEntries.sort(Comparator.naturalOrder());

        // Find and merge adjacent entries
        TocEntry prev = deletedTocEntries.get(0);
        for (int i = 1; i < deletedTocEntries.size(); i++) {
            TocEntry current = deletedTocEntries.get(i);
            if (current.offset == prev.offset + prev.length) {
                // Found two adjacent deleted entries. Merge them.
                current.offset = prev.offset;
                current.length += prev.length;

                // length == 0 means a toc entry is unused
                // we can't delete toc entries because it messes up
                // the fiids. We will reuse them in addDocument().
                prev.length = 0;
            }
            prev = current;
        }

        TocEntry lastEntry = deletedTocEntries.get(deletedTocEntries.size() - 1);
        if (lastEntry.offset + lastEntry.length >= tokenFileEndPosition) {
            // Free entry at the end of the token file. Remove the entry and
            // make the tokens file shorter.
            tokenFileEndPosition -= lastEntry.length;
            lastEntry.length = 0;
        }

        // Re-sort on gap length
        sortDeletedTocEntries();
    }

    /**
     * @return the number of documents in the forward index
     */
    @Override
    public int numDocs() {
        if (!initialized)
            initialize();
        return toc.size();
    }

    /**
     * Gets the length (in tokens) of a document
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
        return toc.get(docId2fiid(docId)).length;
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
