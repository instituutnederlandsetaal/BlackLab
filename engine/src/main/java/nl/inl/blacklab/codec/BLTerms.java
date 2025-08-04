package nl.inl.blacklab.codec;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.RandomAccessInput;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.automaton.CompiledAutomaton;

import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.forwardindex.TermsSegmentReader;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

/**
 * Overridden version of the Lucene Terms class so we
 * can access our BLFieldsProducer from the rest of our code
 * We need this to access the forward index.
 * Also used to read extra terms information (i.e. term strings and sort order).
 * Thread-safe.
 */
public class BLTerms extends Terms {

    /**
     * Return BLTerms instance for one of the fields, so that we have access to the
     * FieldsProducer. (HACK)
     *
     * @param lrc leaf reader context
     * @return BLTerms instance
     */
    public static BLTerms getAnyTermsObject(LeafReaderContext lrc) {
        // Find the first field that has terms.
        for (FieldInfo fieldInfo: lrc.reader().getFieldInfos()) {
            try {
                BLTerms terms = (BLTerms) (lrc.reader().terms(fieldInfo.name));
                if (terms != null)
                    return terms;
            } catch (IOException e) {
                throw new InvalidIndex(e);
            }
        }
        throw new IllegalStateException("No suitable field found for codec access!");
    }

    /** Field for which these are the terms */
    private final String luceneField;

    /** FieldProducer, so it can be accessed from outside the Codec (for access to forward index) */
    private final BlackLabPostingsReader postingsReader;

    /** The Lucene terms object we're wrapping */
    private final Terms terms;

    private IndexInput _termIndexFile;
    private IndexInput _termsFile;
    private IndexInput _termOrderFile;

    /** Contains field names and offsets to term index file, where the terms for the field can be found */
    private final Map<String, ForwardIndexField> fieldsByName = new LinkedHashMap<>();

    public BLTerms(String luceneField, Terms terms, BlackLabPostingsReader postingsReader) throws IOException {
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

    public BlackLabPostingsReader getFieldsProducer() {
        return postingsReader;
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

    TermsSegmentReader reader() {
        return new TermsSegmentReader() {

            private final RandomAccessInput sensitive;
            private final RandomAccessInput insensitive;
            private final RandomAccessInput stringOffsets;
            IndexInput termStrings;

            {
                // first navigate to where the sensitive iteration order is stored in the _termOrderFile.
                try {
                    /**
                     * File with the iteration order.
                     * All term IDS are local to this segment.
                     * for reference, the file contains the following mappings:
                     *     int[n] termID2InsensitivePos    ( offset [0*n*int] )
                     *     int[n] insensitivePos2TermID    ( offset [1*n*int] )
                     *     int[n] termID2SensitivePos      ( offset [2*n*int] )
                     *     int[n] sensitivePos2TermID      ( offset [3*n*int] )
                     */
                    IndexInput termID2SensitivePos = getCloneOfTermOrderFile();
                    IndexInput termID2InsensitivePos = getCloneOfTermOrderFile();

                    // Get random access to the sort order arrays for this field
                    ForwardIndexField field = getField(luceneField);
                    long arrayLength = ((long) field.getNumberOfTerms()) * Integer.BYTES;
                    insensitive = termID2InsensitivePos.randomAccessSlice(field.getTermOrderOffset(),
                            arrayLength);
                    sensitive = termID2SensitivePos.randomAccessSlice(
                            arrayLength * 2 + field.getTermOrderOffset(), arrayLength);

                    // All fields share the same strings file.  Move to the start of our section in the file.
                    IndexInput stringOffsetsFile = getCloneOfTermIndexFile();
                    stringOffsets = stringOffsetsFile.randomAccessSlice(field.getTermIndexOffset(), field.getNumberOfTerms() * Long.BYTES);
                    termStrings = getCloneOfTermsFile();
                } catch (IOException e) {
                    throw new InvalidIndex(e);
                }

            }

            @Override
            public String get(int id) {
                try {
                    long offset = stringOffsets.readLong(id * Long.BYTES);
                    termStrings.seek(offset);
                    return termStrings.readString();
                } catch (IOException e) {
                    throw new InvalidIndex(e);
                }
            }

            @Override
            public boolean termsEqual(int[] termIds, MatchSensitivity sensitivity) {
                if (termIds.length < 2)
                    return true;
                // optimize?
                int expected = idToSortPosition(termIds[0], sensitivity);
                for (int termIdIndex = 1; termIdIndex < termIds.length; ++termIdIndex) {
                    int cur = idToSortPosition(termIds[termIdIndex], sensitivity);
                    if (cur != expected)
                        return false;
                }
                return true;
            }

            @Override
            public int idToSortPosition(int id, MatchSensitivity sensitivity) {
                try {
                    RandomAccessInput array = sensitivity == MatchSensitivity.SENSITIVE ? sensitive : insensitive;
                    return array.readInt(id * Integer.BYTES);
                } catch (IOException e) {
                    throw new InvalidIndex(e);
                }
            }

            @Override
            public void toSortOrder(int[] termIds, int[] sortOrder, MatchSensitivity sensitivity) {
                // optimize?
                for (int i = 0; i < termIds.length; i++) {
                    sortOrder[i] = idToSortPosition(termIds[i], sensitivity);
                }
            }
        };
    }

    private synchronized ForwardIndexField getField(String luceneField) {
        return fieldsByName.get(luceneField);
    }

}
