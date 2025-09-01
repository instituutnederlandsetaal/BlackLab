package nl.inl.blacklab.codec;

import java.io.IOException;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.RandomAccessInput;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.automaton.CompiledAutomaton;

import com.ibm.icu.text.Collator;

import nl.inl.blacklab.Constants;
import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.forwardindex.Collators;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.index.BLFieldTypeLucene;
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
    private final ForwardIndexField forwardIndexField;

    /** Collators to use for sorting and comparing terms */
    Collators collators;

    /** The Lucene terms object we're wrapping */
    private final org.apache.lucene.index.Terms terms;

    private IndexInput _termIndexFile;
    private IndexInput _termsFile;
    private IndexInput _termOrderFile;

    public BLTerms(ForwardIndexField forwardIndexField, Collators collators, org.apache.lucene.index.Terms terms,
            BlackLabPostingsReader postingsReader) throws IOException {
        this.forwardIndexField = forwardIndexField;
        this.collators = collators;
        this.terms = terms;
        this._termIndexFile = postingsReader.openIndexFile(BlackLabPostingsFormat.TERMINDEX_EXT);
        this._termsFile = postingsReader.openIndexFile(BlackLabPostingsFormat.TERMS_EXT);
        this._termOrderFile = postingsReader.openIndexFile(BlackLabPostingsFormat.TERMORDER_EXT);
    }

    public Collators getCollators() {
        return collators;
    }

    public static BLTerms get(BlackLabPostingsReader postingsReader, String fieldName, ForwardIndexField field) {
        try {
            org.apache.lucene.index.Terms delegateTerms = postingsReader.delegateFieldsProducer.terms(fieldName);
            if (delegateTerms != null) {
                Collators collators = BLFieldTypeLucene.getFieldCollators(postingsReader.state.fieldInfos.fieldInfo(fieldName));
                return new BLTerms(field, collators, delegateTerms, postingsReader);
            }
            return null;
        } catch (IOException e) {
            throw new InvalidIndex(e);
        }
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

    /** Array of term strings, if we read them into memory */
    private String[] termStringsArr = null;

    /** For looking up sort position for a term id (sensitive) */
    private int[] termIdToSensitivePos;

    /** For looking up sort position for a term id (insensitive) */
    private int[] termIdToInsensitivePos;

    /** For looking up term id for a sort position (sensitive) */
    private int[] sensitivePosToTermId;

    /** For looking up term id for a sort position (insensitive) */
    private int[] insensitivePosToTermId;

    public synchronized Terms reader() { // synchronized because the first one loads term data
        if (forwardIndexField == null)
            throw new InvalidIndex("No forward index field specified for this terms reader");

        return new Terms() {  // not thread-safe

            private static final boolean READ_TERM_STRINGS_INTO_MEMORY = false;

            /** Offset of each term in termStrings */
            private final RandomAccessInput termStringOffsets;

            /** Where to read term strings */
            final IndexInput termStrings;

            {
                try {

                    // Find the sort orders. All term IDS are local to this segment.
                    // for reference, the term order file contains the following mappings:
                    // int[n] termID2InsensitivePos    ( offset [0+n*int] )
                    // int[n] insensitivePos2TermID    ( offset [1+n*int] )
                    // int[n] termID2SensitivePos      ( offset [2+n*int] )
                    // int[n] sensitivePos2TermID      ( offset [3+n*int] )

                    // Get random access to the sort order arrays for this field
                    int numberOfTerms = forwardIndexField.numberOfTerms;
                    if (termIdToInsensitivePos == null) {
                        IndexInput termOrderFile = _termOrderFile.clone();
                        long offset = forwardIndexField.getTermOrderOffset();
                        int arrayLength = numberOfTerms * Integer.BYTES;
                        termIdToInsensitivePos = readTermOrderIntArray(termOrderFile, offset);
                        offset += arrayLength;
                        insensitivePosToTermId = readTermOrderIntArray(termOrderFile, offset);
                        offset += arrayLength;
                        termIdToSensitivePos = readTermOrderIntArray(termOrderFile, offset);
                        offset += arrayLength;
                        sensitivePosToTermId = readTermOrderIntArray(termOrderFile, offset);
                    }

                    // All fields share the same strings file.  Move to the start of our section in the file.
                    long termStringOffsetsLength = (long) numberOfTerms * Long.BYTES;
                    termStringOffsets = _termIndexFile.clone().randomAccessSlice(
                            forwardIndexField.getTermIndexOffset(), termStringOffsetsLength);
                    termStrings = _termsFile.clone();

                    if (termStringsArr == null && READ_TERM_STRINGS_INTO_MEMORY) {
                        // Read all term strings into memory for fast access
                        termStringsArr = new String[numberOfTerms];
                        long firstTermStringOffset = termStringOffsets.readLong(0);
                        termStrings.seek(firstTermStringOffset);
                        for (int i = 0; i < numberOfTerms; i++) {
                            termStringsArr[i] = termStrings.readString(); //.intern();
                        }
                        termStringOffsets = null;
                        termStrings = null;
                    }
                } catch (IOException e) {
                    throw new InvalidIndex(e);
                }

            }

            private int[] readTermOrderIntArray(IndexInput termOrderFile, long offset) {
                try {
                    termOrderFile.seek(offset);
                    int[] arr = new int[numberOfTerms()];
                    for (int i = 0; i < arr.length; i++) {
                        arr[i] = termOrderFile.readInt();
                    }
                    return arr;
                } catch (IOException e) {
                    throw new InvalidIndex(e);
                }
            }

            @Override
            public String get(int id) {
                if (id == Constants.NO_TERM)
                    return "";
                assert id >= 0 && id < numberOfTerms() : "Term id " + id + " is out of bounds (max is " + (numberOfTerms() - 1) + ")";

                // If we read the term strings into memory, use that
                if (termStringsArr != null) {
                    return termStringsArr[id];
                }
                // Otherwise, read the term string from the file (slow unless you have a lot of memory for cache or a
                // very fast SSD)
                try {
                    long termStringOffset = termStringOffsets.readLong((long) id * Long.BYTES);
                    termStrings.seek(termStringOffset);
                    return termStrings.readString();
                } catch (IOException e) {
                    throw new InvalidIndex(e);
                }
            }

            @Override
            public int indexOf(String word, MatchSensitivity sensitivity) {
                // Simple linear search. We could use binary search via sort positions, but this is only used by
                // PropertyValueContext.deserializeToken(), which is not called often, and should only be called
                // on the global terms object anyway.
                Collator collator = collators.get(sensitivity);
                for (int i = 0; i < numberOfTerms(); i++) {
                    String term1 = get(i);
                    if (collator.compare(term1, word) == 0) {
                        return i;
                    }
                }
                return Constants.NO_TERM; // Not found
            }

            @Override
            public int idToSortPosition(int id, MatchSensitivity sensitivity) {
                if (id == Constants.NO_TERM)
                    return Constants.NO_TERM;
                int[] termIdToPos = sensitivity == MatchSensitivity.SENSITIVE ?
                        termIdToSensitivePos : termIdToInsensitivePos;
                return termIdToPos[id];
            }

            @Override
            public int termToSortPosition(String term, MatchSensitivity sensitivity) {
                int indexOfTerm;

                // First, find the index in the sort position array.
                // This may not be the actual sort position because multiple terms can have the same sort position.
                // In this case, the index of first term determines the sort position for all those terms.
                int[] sortPosToTermId = sensitivity == MatchSensitivity.SENSITIVE ?
                        sensitivePosToTermId : insensitivePosToTermId;
                indexOfTerm = findIndexInSortPositionArray(term, sensitivity, sortPosToTermId);
                if (indexOfTerm < 0) {
                    // Not found, return NO_TERM
                    return Constants.NO_TERM;
                }

                // Now, find the first term equal to this one.
                int sortPos = indexOfTerm - 1;
                Collator collator = collators.get(sensitivity);
                while (sortPos > 0) {
                    String term1 = get(sortPosToTermId[sortPos]);
                    if (collator.compare(term1, term) != 0)
                        break;
                    // If the term is equal to the one we're looking for, we have to go back further
                    // until we find the first occurrence of this term.
                    // (NOTE: this is relatively inefficient and we could precalculate it, but this method isn't
                    //  used in any hot loops)
                    sortPos--;
                }
                return sortPos + 1; // +1 because we went one too far
            }

            private int findIndexInSortPositionArray(String term, MatchSensitivity sensitivity, int[] sortPosToTermId) {
                // Use binary search by sort position to find the term's sort position.
                int low = 0;
                int high = numberOfTerms() - 1;
                Collator collator = collators.get(sensitivity);
                while (low <= high) {
                    int mid = (low + high) >>> 1;
                    String midVal = get(sortPosToTermId[mid]);
                    int cmp = collator.compare(midVal, term);
                    if (cmp < 0) {
                        low = mid + 1;
                    } else if (cmp > 0) {
                        high = mid - 1;
                    } else {
                        // found it!
                        return mid;
                    }
                }
                // not found
                return Constants.NO_TERM;
            }

//            @Override
//            public void convertToGlobalTermIds(int[] segmentTermIds) {
//                getGlobalTerms(); // ensure global terms are loaded
//                for (int i = 0; i < segmentTermIds.length; i++) {
//                    if (segmentTermIds[i] != Constants.NO_TERM)
//                        segmentTermIds[i] = segmentToGlobal[segmentTermIds[i]];
//                }
//            }
//
//            @Override
//            public int toGlobalTermId(int segmentTermId) {
//                getGlobalTerms(); // ensure global terms are loaded
//                if (segmentTermId != Constants.NO_TERM)
//                    return segmentToGlobal[segmentTermId];
//                return Constants.NO_TERM;
//            }

            @Override
            public int numberOfTerms() {
                return forwardIndexField.numberOfTerms;
            }
        };
    }
}
