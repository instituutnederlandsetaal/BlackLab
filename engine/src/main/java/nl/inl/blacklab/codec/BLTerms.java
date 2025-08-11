package nl.inl.blacklab.codec;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.RandomAccessInput;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.automaton.CompiledAutomaton;

import nl.inl.blacklab.Constants;
import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.forwardindex.Collators;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

/**
 * Overridden version of the Lucene Terms class so we
 * can access our BLFieldsProducer from the rest of our code
 * We need this to access the forward index.
 * Also used to read extra terms information (i.e. term strings and sort order).
 * Thread-safe.
 */
public class BLTerms extends org.apache.lucene.index.Terms {

    public static BLTerms forSegment(LeafReaderContext lrc, String luceneField) {
        try {
            return (BLTerms) lrc.reader().terms(luceneField);
        } catch (IOException e) {
            throw new InvalidIndex(e);
        }
    }

    /** Field for which these are the terms */
    private final String luceneField;

    /** FieldProducer, so it can be accessed from outside the Codec (for access to forward index) */
    private final BlackLabPostingsReader postingsReader;

    /** The Lucene terms object we're wrapping */
    private final org.apache.lucene.index.Terms terms;

    private IndexInput _termIndexFile;
    private IndexInput _termsFile;
    private IndexInput _termOrderFile;

    /** Contains field names and offsets to term index file, where the terms for the field can be found */
    private final Map<String, ForwardIndexField> fieldsByName = new LinkedHashMap<>();

    public BLTerms(String luceneField, org.apache.lucene.index.Terms terms, BlackLabPostingsReader postingsReader) throws IOException {
        this.luceneField = luceneField;
        this.terms = terms;
        this.postingsReader = postingsReader;
        this._termIndexFile = postingsReader.openIndexFile(BlackLabPostingsFormat.TERMINDEX_EXT);
        this._termsFile = postingsReader.openIndexFile(BlackLabPostingsFormat.TERMS_EXT);
        this._termOrderFile = postingsReader.openIndexFile(BlackLabPostingsFormat.TERMORDER_EXT);

        // OPT: read cache these fields somewhere so we don't read them once per annotation
        try (IndexInput fieldInput = postingsReader.openIndexFile(BlackLabPostingsFormat.FIELDS_EXT)) {
            while (fieldInput.getFilePointer() < (fieldInput.length() - CodecUtil.footerLength())) {
                ForwardIndexField f = new ForwardIndexField(fieldInput);
                fieldsByName.put(f.getFieldName(), f);
            }
        }
    }

    private synchronized IndexInput getCloneOfTermIndexFile() {
        // synchronized because clone() is not thread-safe
        return _termIndexFile.clone();
    }

    private synchronized IndexInput getCloneOfTermsFile() {
        // synchronized because clone() is not thread-safe
        return _termsFile.clone();
    }

    private synchronized IndexInput getCloneOfTermOrderFile() {
        // synchronized because clone() is not thread-safe
        return _termOrderFile.clone();
    }

    public void close() throws IOException {
        _termIndexFile.close();
        _termsFile.close();
        _termOrderFile.close();
        _termIndexFile = _termsFile = _termOrderFile = null;
    }

    @Override
    public TermsEnum iterator() throws IOException {
        return terms.iterator();
    }

    @Override
    public TermsEnum intersect(CompiledAutomaton compiled, BytesRef startTerm) throws IOException {
        return terms.intersect(compiled, startTerm);
    }

    @Override
    public long size() throws IOException {
        return terms.size();
    }

    @Override
    public long getSumTotalTermFreq() throws IOException {
        return terms.getSumTotalTermFreq();
    }

    @Override
    public long getSumDocFreq() throws IOException {
        return terms.getSumDocFreq();
    }

    @Override
    public int getDocCount() throws IOException {
        return terms.getDocCount();
    }

    @Override
    public boolean hasFreqs() {
        return terms.hasFreqs();
    }

    @Override
    public boolean hasOffsets() {
        return terms.hasOffsets();
    }

    @Override
    public boolean hasPositions() {
        return terms.hasPositions();
    }

    @Override
    public boolean hasPayloads() {
        return terms.hasPayloads();
    }

    @Override
    public BytesRef getMin() throws IOException {
        return terms.getMin();
    }

    @Override
    public BytesRef getMax() throws IOException {
        return terms.getMax();
    }

    @Override
    public Object getStats() throws IOException {
        return terms.getStats();
    }

    Collators collators = Collators.getDefault();

    public Terms reader() {
        return new Terms() { // not thread-safe

            /** Field we're reading terms from */
            private final ForwardIndexField field;

            /** For looking up sort position for a term id (sensitive) */
            private final RandomAccessInput termIdToSensitivePos;

            /** For looking up sort position for a term id (insensitive) */
            private final RandomAccessInput termIdToInsensitivePos;

            /** For looking up term id for a sort position (sensitive) */
            private final RandomAccessInput sensitivePosToTermId;

            /** For looking up term id for a sort position (insensitive) */
            private final RandomAccessInput insensitivePosToTermId;

            /** Offset of each term in termStrings */
            private final RandomAccessInput termStringOffsets;

            /** Where to read term strings */
            final IndexInput termStrings;

            {
                try {
                    field = getField(luceneField);

                    // Find the sort orders. All term IDS are local to this segment.
                    // for reference, the term order file contains the following mappings:
                    // int[n] termID2InsensitivePos    ( offset [0+n*int] )
                    // int[n] insensitivePos2TermID    ( offset [1+n*int] )
                    // int[n] termID2SensitivePos      ( offset [2+n*int] )
                    // int[n] sensitivePos2TermID      ( offset [3+n*int] )

                    // Get random access to the sort order arrays for this field
                    long arrayLength = ((long) field.getNumberOfTerms()) * Integer.BYTES;
                    IndexInput termOrderFile = getCloneOfTermOrderFile();
                    long offset = field.getTermOrderOffset();
                    termIdToInsensitivePos = termOrderFile.randomAccessSlice(offset, arrayLength);
                    offset += arrayLength;
                    insensitivePosToTermId = termOrderFile.randomAccessSlice(offset, arrayLength);
                    offset += arrayLength;
                    termIdToSensitivePos = termOrderFile.randomAccessSlice(offset, arrayLength);
                    offset += arrayLength;
                    sensitivePosToTermId = termOrderFile.randomAccessSlice(offset, arrayLength);

                    // All fields share the same strings file.  Move to the start of our section in the file.
                    long termStringOffsetsLength = (long) field.getNumberOfTerms() * Long.BYTES;
                    termStringOffsets = getCloneOfTermIndexFile().randomAccessSlice(field.getTermIndexOffset(), termStringOffsetsLength);
                    termStrings = getCloneOfTermsFile();
                } catch (IOException e) {
                    throw new InvalidIndex(e);
                }

            }

            @Override
            public String get(int id) {
                if (id == Constants.NO_TERM)
                    return "";
                try {
                    long termStringOffset = termStringOffsets.readLong((long) id * Long.BYTES);
                    termStrings.seek(termStringOffset);
                    return termStrings.readString();
                } catch (IOException e) {
                    throw new InvalidIndex(e);
                }
            }

            @Override
            public int idToSortPosition(int id, MatchSensitivity sensitivity) {
                if (id == Constants.NO_TERM)
                    return Constants.NO_TERM;
                try {
                    RandomAccessInput array = sensitivity == MatchSensitivity.SENSITIVE ? termIdToSensitivePos :
                            termIdToInsensitivePos;
                    return array.readInt((long) id * Integer.BYTES);
                } catch (IOException e) {
                    throw new InvalidIndex(e);
                }
            }

            private int compareTerms(String term1, String term2, MatchSensitivity sensitivity) {

                // FIXME: actually, we should use the configured collator for this field here.
                //   (RELATED: Collator vs. MatchSensitivity is still a bit of an issue...)
                return collators.get(sensitivity).compare(term1, term2);
            }

            @Override
            public int termToSortPosition(String term, MatchSensitivity sensitivity) {
                int indexOfTerm;

                // First, find the index in the sort position array.
                // This may not be the actual sort position because multiple terms can have the same sort position.
                // In this case, the index of first term determines the sort position for all those terms.
                RandomAccessInput sortPosToTermId = sensitivity == MatchSensitivity.SENSITIVE ?
                        sensitivePosToTermId : insensitivePosToTermId;
                indexOfTerm = findIndexInSortPositionArray(term, sensitivity, sortPosToTermId);
                if (indexOfTerm < 0) {
                    // Not found, return NO_TERM
                    return Constants.NO_TERM;
                }

                // Now, find the first term equal to this one.
                int sortPos = indexOfTerm - 1;
                try {
                    while (sortPos > 0 &&
                            compareTerms(get(sortPosToTermId.readInt((long) sortPos * Integer.BYTES)), term, sensitivity) == 0) {
                        // If the term is equal to the one we're looking for, we have to go back further
                        // until we find the first occurrence of this term.
                        // (NOTE: this is relatively inefficient and we could precalculate it, but this method isn't
                        //  used in any hot loops)
                        sortPos--;
                    }
                } catch (IOException e) {
                    throw new InvalidIndex(e);
                }
                return sortPos + 1; // +1 because we went one too far
            }

            private int findIndexInSortPositionArray(String term, MatchSensitivity sensitivity,
                    RandomAccessInput sortPosToTermId) {
                // Use binary search by sort position to find the term's sort position.
                int low = 0;
                int high = field.getNumberOfTerms() - 1;
                try {
                    while (low <= high) {
                        int mid = (low + high) >>> 1;
                        String midVal = get(sortPosToTermId.readInt((long) mid * Integer.BYTES));
                        int cmp = compareTerms(midVal, term, sensitivity);
                        if (cmp < 0) {
                            low = mid + 1;
                        } else if (cmp > 0) {
                            high = mid - 1;
                        } else {
                            // found it!
                            return mid;
                        }
                    }
                } catch (IOException e) {
                    throw new InvalidIndex(e);
                }
                // not found
                return Constants.NO_TERM;
            }

            @Override
            public int numberOfTerms() {
                return field.getNumberOfTerms();
            }
        };
    }

    private synchronized ForwardIndexField getField(String luceneField) {
        return fieldsByName.get(luceneField);
    }

}
