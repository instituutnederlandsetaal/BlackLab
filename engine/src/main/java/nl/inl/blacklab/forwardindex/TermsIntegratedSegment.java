package nl.inl.blacklab.forwardindex;

import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.store.IndexInput;

import net.jcip.annotations.NotThreadSafe;
import nl.inl.blacklab.codec.BlackLabPostingsFormat;
import nl.inl.blacklab.codec.BlackLabPostingsReader;
import nl.inl.blacklab.codec.ForwardIndexField;
import nl.inl.blacklab.exceptions.InvalidIndex;

/**
 * Presents an iterator over ONE field/annotation in ONE segment of the Index.
 *
 * Ideally short-lived and only used during setup of the forward index and terms instances.
 * In the future, if and when we no longer have need to transform results from segment to global,
 * could be rewritten to do everything with memory mapping, and become longer lived.
 * In that case this will become the primary Terms access (instead of Terms/TermsIntegrated) probably.
 *
 * Iteration is NOT thread safe, as the underlying file buffers are shared.
 */
@NotThreadSafe()
public class TermsIntegratedSegment implements AutoCloseable {
    private boolean isClosed = false;
    private BlackLabPostingsReader segmentReader;
    private ForwardIndexField field;
    private final int ord;

    private IndexInput _termIndexFile;
    private IndexInput _termsFile;
    private IndexInput _termOrderFile;


    public TermsIntegratedSegment(BlackLabPostingsReader segmentReader, String luceneField, int ord) {
        try {
            this.segmentReader = segmentReader;
            this.ord = ord;
            this._termIndexFile = segmentReader.openIndexFile(BlackLabPostingsFormat.TERMINDEX_EXT);
            this._termsFile = segmentReader.openIndexFile(BlackLabPostingsFormat.TERMS_EXT);
            this._termOrderFile = segmentReader.openIndexFile(BlackLabPostingsFormat.TERMORDER_EXT);

            // OPT: read cache these fields somewhere so we don't read them once per annotation
            try (IndexInput fieldInput = segmentReader.openIndexFile(BlackLabPostingsFormat.FIELDS_EXT)) {
                while (fieldInput.getFilePointer() < (fieldInput.length() - CodecUtil.footerLength())) {
                    ForwardIndexField f = new ForwardIndexField(fieldInput);
                    if (f.getFieldName().equals(luceneField)) {
                        this.field = f;
                        break;
                    }
                }
                // we checked all fields but did not find it.
                if (this.field == null)
                    throw new InvalidIndex("Trying to read forward index for field "+luceneField+ ", but it does not exist.");
            }
        } catch (IOException e) {
            throw new InvalidIndex("Error reading forward index/terms for segment");
        }
    }

    public synchronized Iterator<TermInSegment> iterator() {
        // NOTE: method is synchronized because TermInSegmentIterator constructor uses IndexInput.clone(), which is
        // not thread-safe.
        if (this.isClosed) throw new IllegalStateException("Segment is closed");
        return new TermInSegmentIterator(this);
    }

    @Override
    public void close() {
        try {
            isClosed = true;
            _termIndexFile.close();
            _termsFile.close();
            _termOrderFile.close();
            _termIndexFile = _termsFile = _termOrderFile = null;
            segmentReader = null;
        } catch (IOException e) {
            throw new InvalidIndex(e);
        }
    }

    /** returns the total number of terms in this segment for this field */
    public int size() {
        return this.field.getNumberOfTerms();
    }

    public static class TermInSegment {
        public String term;
        /** The local term id. */
        public int id;
    }

    @NotThreadSafe
    private static class TermInSegmentIterator implements Iterator<TermInSegment> {

        /**
         * Metadata about this field(=annotation) in this segment(=section of the index).
         * such as how many terms in the field in this segment,
         * file offsets where to find the data in the segment's files, etc.
         */
        private final ForwardIndexField field;

        /** Ord (ordinal) of the segment */
        private final int ord;

        /**
         * File with the iteration order.
         * All term IDS are local to this segment.
         * for reference, the file contains the following mappings:
         *     int[n] termID2InsensitivePos    ( offset [0*n*int] )
         *     int[n] insensitivePos2TermID    ( offset [1*n*int] )
         *     int[n] termID2SensitivePos      ( offset [2*n*int] )
         *     int[n] sensitivePos2TermID      ( offset [3*n*int] )
         */
        private final IndexInput termID2SensitivePosFile;

        private final IndexInput termID2InsensitivePosFile;

        /** File containing the strings */
        private final IndexInput termStringFile;

        /** index of the term CURRENTLY loaded in peek/how many times has next() been called. */
        private int i = 0;

        /** Total number of terms in the segment */
        private final int n;

        private final TermInSegment t = new TermInSegment();

        /**
         * @param segment only used for initialization, because we need to pass many parameters otherwise.
         */
        private TermInSegmentIterator(TermsIntegratedSegment segment) {
            // first navigate to where the sensitive iteration order is stored in the _termOrderFile.
            try {
                this.field = segment.field;
                this.ord = segment.ord;

                // clone these file accessors, as they are not threadsafe
                // while this code was written these file handles were only ever used in one thread,
                // but doing this ensures we don't break things in the future.
                this.termID2SensitivePosFile = segment._termOrderFile.clone();
                this.termID2InsensitivePosFile = segment._termOrderFile.clone();
                this.termStringFile = segment._termsFile.clone();

                this.i = 0;
                this.n = field.getNumberOfTerms();

                this.termID2SensitivePosFile.seek(((long)n)*Integer.BYTES*2+field.getTermOrderOffset());
                this.termID2InsensitivePosFile.seek(field.getTermOrderOffset());

                // All fields share the same strings file.  Move to the start of our section in the file.
                IndexInput stringoffsets = segment._termIndexFile.clone();
                stringoffsets.seek(field.getTermIndexOffset());
                long firstStringOffset = stringoffsets.readLong();

                this.termStringFile.seek(firstStringOffset);
            } catch (IOException e) {
                throw new InvalidIndex(e);
            }
        }

        @Override
        public boolean hasNext() {
            return i < n;
        }

        @Override
        public TermInSegment next() {
            try {
                if (i >= n)
                    throw new NoSuchElementException();
                this.t.term = termStringFile.readString();
                this.t.id = i++;
                return this.t;
            } catch (IOException e) {
                throw new InvalidIndex(e);
            }
        }

        /**
         * Get the ord (ordinal) of the segment this iterator was constructed on.
         * You could see this as the "id" of the segment. Starts at 0 and increments by 1 for every next segment.
         */
        public int ord() {
            return this.ord;
        }

        /** returns the total number of terms in this segment */
        public int size() {
            return this.n;
        }
    }

    /**
     * Get the ord (ordinal) of the segment.
     * You could see this as the "id" of the segment. Starts at 0 and increments by 1 for every next segment.
     */
    public int ord() {
        return this.ord;
    }

    public ForwardIndexField field() {
        return this.field;
    }
}
