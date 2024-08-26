package nl.inl.blacklab.codec;

import java.io.IOException;
import java.text.CollationKey;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.store.ByteArrayDataOutput;
import org.apache.lucene.store.DataOutput;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.util.BytesRef;

import it.unimi.dsi.fastutil.ints.IntArrays;
import nl.inl.blacklab.analysis.PayloadUtils;
import nl.inl.blacklab.forwardindex.Collators;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.BlackLabIndexIntegrated;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

/**
 * Hook into the postings writer to construct the forward index.
 *
 * Reverses the reverse index while it's being written, constructing the forward index.
 *
 * First we write a temporary dump of the term vector, and keep track of
 * where we can find term occurrences per document so we can reverse this
 * file later.
 * (we iterate per field & term first, because that is how Lucene's reverse
 *  index stores the information. What we need is per field, then per document
 *  (we're trying to reconstruct the document), so we will do that below.
 *  we use temporary files because this might take a huge amount of memory)
 * (use a LinkedHashMap to maintain the same field order when we write the tokens below)
 */
class PWPluginForwardIndex implements PWPlugin {

    private final BlackLab40PostingsWriter postingsWriter;

    private Map<String, ForwardIndexFieldMutable> fiFields = new HashMap<>();

    private final IndexOutput outTokensIndexFile;
    private final IndexOutput outTokensFile;
    private final IndexOutput termIndexFile;
    private final IndexOutput termsFile;
    private final IndexOutput termsOrderFile;

    /**
     * Doc lengths per annotated field (e.g. "contents").
     *
     * We keep track of doc lengths so we can preallocate our forward index structure.
     * (we do this per annotated field, e.g. contents, NOT per annotation, e.g. contents%word@s,
     * because all annotations on the same field have the same length)
     */
    private final Map<String, Map<Integer, Integer>> docLengthsPerAnnotatedField = new HashMap<>();

    /**
     * Keep track of where we can find term occurrences per document so we can reverse term vector
     * file later.
     */
    private final Map<String, LengthsAndOffsetsPerDocument> field2docTermVecFileOffsets = new LinkedHashMap<>();

    /**
     * Temporary term vector file that will be reversed to form the forward index
     */
    private IndexOutput outTempTermVectorFile;


    // Per field

    /**
     * Term index offset
     */
    private ForwardIndexFieldMutable currentField;

    private LengthsAndOffsetsPerDocument lengthsAndOffsetsPerDocument;

    private List<String> termsList;


    // Per term

    private int currentTermId;


    // Per document

    private int currentDocId;

    private int currentDocLength;

    private byte[] currentDocPositionsArray;

    private DataOutput currentDocPositionsOutput;

    private int currentDocOccurrencesWritten;


    PWPluginForwardIndex(BlackLab40PostingsWriter postingsWriter) throws IOException {
        this.postingsWriter = postingsWriter;

        outTokensIndexFile = postingsWriter.createOutput(BlackLabPostingsFormat.TOKENS_INDEX_EXT);
        outTokensFile = postingsWriter.createOutput(BlackLabPostingsFormat.TOKENS_EXT);
        termIndexFile = postingsWriter.createOutput(BlackLabPostingsFormat.TERMINDEX_EXT);
        termsFile = postingsWriter.createOutput(BlackLabPostingsFormat.TERMS_EXT);
        termsOrderFile = postingsWriter.createOutput(BlackLabPostingsFormat.TERMORDER_EXT);
        outTempTermVectorFile = postingsWriter.createOutput(BlackLabPostingsFormat.TERMVEC_TMP_EXT);
    }

    static int[] getDocumentContents(int docLength,
            IndexInput inTermVectorFile, TermVecFileOffsetPerTermId termPosOffsets)
            throws IOException {

        final int[] tokensInDoc = new int[docLength]; // reconstruct the document here

        // NOTE: sometimes docs won't have any values for a field, but we'll
        //   still write all NO_TERMs in this case. This is similar to sparse
        //   fields (e.g. the field that stores <p> <s> etc.) which also have a
        //   lot of NO_TERMs.
        Arrays.fill(tokensInDoc, Terms.NO_TERM);

        // For each term...
        for (Entry<Integer/*term ID*/, Long/*file offset*/> e: termPosOffsets.entrySet()) {
            int termId = e.getKey();
            inTermVectorFile.seek(e.getValue());
            int nOccurrences = inTermVectorFile.readInt();
            // For each occurrence...
            for (int i = 0; i < nOccurrences; i++) {
                int position = inTermVectorFile.readInt();
                tokensInDoc[position] = termId;
            }
        }

        return tokensInDoc;
    }

    /**
     * Given a list of terms, return the indices to sort them.
     * E.G: getTermSortOrder(['b','c','a']) --> [2, 0, 1]
     */
    static int[] getTermSortOrder(List<String> terms, Collator coll) {
        int[] ret = new int[terms.size()];
        for (int i = 0; i < ret.length; ++i) ret[i] = i;

        // Collator.compare() is synchronized, so precomputing the collation keys speeds things up
        CollationKey[] ck = new CollationKey[terms.size()];
        for (int i = 0; i < ck.length; ++i)
            ck[i] = coll.getCollationKey(terms.get(i));

        IntArrays.parallelQuickSort(ret, (a, b) -> ck[a].compareTo(ck[b]));
        return ret;
    }

    /**
     * Invert the given array so the values become the indexes and vice versa.
     *
     * @param array array to invert
     * @return inverted array
     */
    static int[] invert(List<String> terms, int[] array, Collator collator) {
        int[] result = new int[array.length];
        int prevSortPosition = -1;
        int prevTermId = -1;
        for (int i = 0; i < array.length; i++) {
            int termId = array[i];
            int sortPosition = i;
            if (prevTermId >= 0 && collator.equals(terms.get(prevTermId), terms.get(termId))) {
                // Keep the same sort position because the terms are the same
                sortPosition = prevSortPosition;
            } else {
                // Remember the sort position in case the next term is identical
                prevSortPosition = sortPosition;
            }
            result[termId] = sortPosition;
            prevTermId = termId;
        }
        return result;
    }

    /**
     * Write the tokens to the tokens file.
     *
     * Also records offset, length and encoding in the tokens index file.
     *
     * Chooses the most appropriate encoding for the tokens and records this choice in
     * the tokens index file.
     *
     * @param outTokensIndexFile token index file
     * @param outTokensFile      tokens file
     * @param tokensInDoc        tokens to write
     * @throws IOException       When failing to write
     */
    static void writeTokensInDoc(IndexOutput outTokensIndexFile, IndexOutput outTokensFile, int[] tokensInDoc) throws IOException {
        int max = 0, min = 0;
        boolean allTheSame = tokensInDoc.length > 0; // if no tokens, then not all the same.
        int last = -1;
        for (int token: tokensInDoc) {
            max = Math.max(max, token);
            min = Math.min(min, token);
            allTheSame = allTheSame && (last == -1 || last == token);
            last = token;
            if ((min < Short.MIN_VALUE || max > Short.MAX_VALUE) && !allTheSame) // stop if already at worst case (int per token + not all the same).
                break;
        }

        // determine codec
        TokensCodec tokensCodec = allTheSame ? TokensCodec.ALL_TOKENS_THE_SAME : TokensCodec.VALUE_PER_TOKEN;

        // determine parameter byte for codec.
        byte tokensCodecParameter = 0;
        switch (tokensCodec) {
            case ALL_TOKENS_THE_SAME: tokensCodecParameter = 0; break;
            case VALUE_PER_TOKEN: {
                if (min >= Byte.MIN_VALUE && max <= Byte.MAX_VALUE) tokensCodecParameter = TokensCodec.VALUE_PER_TOKEN_PARAMETER.BYTE.code;
                else if (min >= Short.MIN_VALUE && max <= Short.MAX_VALUE) tokensCodecParameter = TokensCodec.VALUE_PER_TOKEN_PARAMETER.SHORT.code;
                else if (min >= ThreeByteInt.MIN_VALUE && max <= ThreeByteInt.MAX_VALUE) tokensCodecParameter = TokensCodec.VALUE_PER_TOKEN_PARAMETER.THREE_BYTES.code;
                else tokensCodecParameter = TokensCodec.VALUE_PER_TOKEN_PARAMETER.INT.code;
                break;
            }
            default: throw new NotImplementedException("Parameter byte determination for tokens codec " + tokensCodec + " not implemented.");
        }

        // Write offset in the tokens file, doc length in tokens and tokens codec used
        outTokensIndexFile.writeLong(outTokensFile.getFilePointer());
        outTokensIndexFile.writeInt(tokensInDoc.length);
        outTokensIndexFile.writeByte(tokensCodec.code);
        outTokensIndexFile.writeByte(tokensCodecParameter);

        if (tokensInDoc.length == 0) {
            return; // done.
        }

        // Write the tokens
        switch (tokensCodec) {
        case VALUE_PER_TOKEN:
            switch (TokensCodec.VALUE_PER_TOKEN_PARAMETER.fromCode(tokensCodecParameter)) {
                case BYTE:
                    for (int token: tokensInDoc) {
                        outTokensFile.writeByte((byte) token);
                    }
                    break;
                case SHORT:
                    for (int token: tokensInDoc) {
                        outTokensFile.writeShort((short) token);
                    }
                    break;
                case THREE_BYTES:
                    for (int token : tokensInDoc) {
                        ThreeByteInt.write((b) -> outTokensFile.writeByte(b), token);
                    }
                    break;
                case INT:
                    for (int token: tokensInDoc) {
                        outTokensFile.writeInt((int) token);
                    }
                    break;
                    default: throw new NotImplementedException("Handling for tokens codec " + tokensCodec + " with parameter " + tokensCodecParameter + " not implemented.");
                }
                break;
        case ALL_TOKENS_THE_SAME:
            outTokensFile.writeInt(tokensInDoc[0]);
            break;
        }
    }

    @Override
    public void close() throws IOException {
        if (outTempTermVectorFile != null) {
            outTempTermVectorFile.close();
            outTempTermVectorFile = null;
        }
        termsOrderFile.close();
        termsFile.close();
        termIndexFile.close();
        outTokensFile.close();
        outTokensIndexFile.close();
    }

    public boolean startField(FieldInfo fieldInfo) {

        // Should this field get a forward index?
        if (!BlackLabIndexIntegrated.doesFieldHaveForwardIndex(fieldInfo))
            return false;

        // Make sure doc lengths are shared between all annotations for a single annotated field.
        Map<Integer, Integer> docLengths = docLengthsPerAnnotatedField.computeIfAbsent(
                AnnotatedFieldNameUtil.getBaseName(fieldInfo.name), __ -> new HashMap<>());

        // Write the term vector file and keep track of where we can find term occurrences per document,
        // so we can turn this into the actual forward index below.
        currentField = fiFields.computeIfAbsent(fieldInfo.name, ForwardIndexFieldMutable::new);

        // We're creating a forward index for this field.
        // That also means that the payloads will include an "is-primary-value" indicator,
        // so we know which value to store in the forward index (the primary value, i.e.
        // the first value, to be used in concordances, sort, group, etc.).
        // We must skip these indicators when working with other payload info. See PayloadUtils
        // for details.

        // Record starting offset of field in termindex file (written to fields file later)
        currentField.setTermIndexOffset(termIndexFile.getFilePointer());

        // Keep track of where to find term positions for each document
        // (for reversing index)
        // The map is keyed by docId and stores a list of offsets into the
        // temporary termvector file where the occurrences for each term can be
        // found.
        lengthsAndOffsetsPerDocument =
                field2docTermVecFileOffsets.computeIfAbsent(fieldInfo.name,
                        __ -> new LengthsAndOffsetsPerDocument(docLengths));

        termsList = new ArrayList<>();

        currentTermId = 0;
        return true;
    }

    public void endField() throws IOException {
        // begin writing term IDs and sort orders
        Collators collators = Collators.defaultCollator();
        int[] sensitivePos2TermID = getTermSortOrder(termsList,
                collators.get(MatchSensitivity.SENSITIVE));
        int[] insensitivePos2TermID = getTermSortOrder(termsList,
                collators.get(MatchSensitivity.INSENSITIVE));
        int[] termID2SensitivePos = invert(termsList, sensitivePos2TermID,
                collators.get(MatchSensitivity.SENSITIVE));
        int[] termID2InsensitivePos = invert(termsList, insensitivePos2TermID,
                collators.get(MatchSensitivity.INSENSITIVE));

        int numTerms = termsList.size();
        currentField.setNumberOfTerms(numTerms);
        currentField.setTermOrderOffset(termsOrderFile.getFilePointer());
        // write out, specific order.
        for (int i: termID2InsensitivePos)
            termsOrderFile.writeInt(i);
        for (int i: insensitivePos2TermID)
            termsOrderFile.writeInt(i);
        for (int i: termID2SensitivePos)
            termsOrderFile.writeInt(i);
        for (int i: sensitivePos2TermID)
            termsOrderFile.writeInt(i);
    }

    public void startTerm(BytesRef term) throws IOException {
        // Write the term to the terms file
        String termString = term.utf8ToString();
        termIndexFile.writeLong(termsFile.getFilePointer()); // where to find term string
        termsFile.writeString(termString);
        termsList.add(termString);
    }

    public void endTerm() {
        currentTermId++;
    }

    public void startDocument(int docId, int nOccurrences) {
        // Keep track of term positions offsets in term vector file
        this.currentDocId = docId;
        currentDocLength = -1;
        currentDocPositionsArray = new byte[nOccurrences * Integer.BYTES];
        currentDocPositionsOutput = new ByteArrayDataOutput(currentDocPositionsArray);
        currentDocOccurrencesWritten = 0;
    }

    public void endDocument() throws IOException {
        if (currentDocLength > -1)
            lengthsAndOffsetsPerDocument.updateFieldLength(currentDocId, currentDocLength);

        // Write the positions where this term occurs as primary value
        // (will be reversed below to get the forward index)
        lengthsAndOffsetsPerDocument.putTermOffset(currentDocId, currentTermId,
                outTempTermVectorFile.getFilePointer());
        outTempTermVectorFile.writeInt(currentDocOccurrencesWritten);
        if (currentDocOccurrencesWritten > 0) {
            outTempTermVectorFile.writeBytes(currentDocPositionsArray, 0,
                    currentDocOccurrencesWritten * Integer.BYTES);
        }

    }

    public void termOccurrence(int position, BytesRef payload) throws IOException {
        // Go through each occurrence of term in this doc,
        // gathering the positions where this term occurs as a "primary value"
        // (the first value at this token position, which we will store in the
        //  forward index). Also determine docLength.
        if (position >= currentDocLength)
            currentDocLength = position + 1;
        // Is this a primary value or a secondary one?
        // Primary values are e.g. the original word from the document,
        // and will be stored in the forward index to be used for concordances,
        // sorting and grouping. Secondary values may be synonyms or stemmed versions
        // and will not be stored in the forward index.
        if (PayloadUtils.isPrimaryValue(payload)) {
            // primary value; write to buffer
            currentDocPositionsOutput.writeInt(position);
            currentDocOccurrencesWritten++;
        }
    }

    public void finalize() throws IOException {
        if (outTempTermVectorFile != null) {
            CodecUtil.writeFooter(outTempTermVectorFile);
            outTempTermVectorFile.close();
            outTempTermVectorFile = null;
        }

        // Reverse the reverse index to create forward index
        // (this time we iterate per field and per document first, then reconstruct the document by
        //  looking at each term's occurrences. This produces our forward index)
        try (IndexInput inTermVectorFile = postingsWriter.openInput(BlackLabPostingsFormat.TERMVEC_TMP_EXT);
                IndexOutput fieldsFile = postingsWriter.createOutput(BlackLabPostingsFormat.FIELDS_EXT)) {

            // For each field...
            for (Entry<String, LengthsAndOffsetsPerDocument> fieldEntry: field2docTermVecFileOffsets.entrySet()) {
                String luceneField = fieldEntry.getKey();
                LengthsAndOffsetsPerDocument docPosOffsets = fieldEntry.getValue();

                // Record starting offset of field in tokensindex file,
                // and write the field information to the fields file
                ForwardIndexFieldMutable fieldMutable = fiFields.get(luceneField);
                fieldMutable.setTokensIndexOffset(outTokensIndexFile.getFilePointer());
                fieldMutable.write(fieldsFile);

                // Make sure we know our document lengths
                String annotatedFieldName = AnnotatedFieldNameUtil.getBaseName(luceneField);
                Map<Integer, Integer> docLengths = docLengthsPerAnnotatedField.get(annotatedFieldName);

                // For each document...
                for (int docId = 0; docId < postingsWriter.maxDoc(); docId++) {
                    final int docLength = docLengths.getOrDefault(docId, 0);
                    TermVecFileOffsetPerTermId offsets = docPosOffsets.get(docId);
                    int[] termIds = getDocumentContents(docLength, inTermVectorFile, offsets);
                    writeTokensInDoc(outTokensIndexFile, outTokensFile, termIds);
                }
            }
            CodecUtil.writeFooter(fieldsFile);
        } finally {
            // Clean up after ourselves
            postingsWriter.deleteIndexFile(BlackLabPostingsFormat.TERMVEC_TMP_EXT);
        }

        CodecUtil.writeFooter(outTokensIndexFile);
        CodecUtil.writeFooter(outTokensFile);
        CodecUtil.writeFooter(termIndexFile);
        CodecUtil.writeFooter(termsFile);
        CodecUtil.writeFooter(termsOrderFile);
    }

    /**
     * Offset in term vector file per term id for a single doc and field.
     *
     * Keeps track of position in the term vector file where the occurrences of each term id
     * are stored for a single document and field.
     */
    static class TermVecFileOffsetPerTermId extends LinkedHashMap<Integer, Long> {
        // intentionally blank; this is just a typedef for readability
    }

    /**
     * Keeps track of tokens length and term vector file offsets for a single Lucene field.
     *
     * Tokens length is shared between all Lucene fields belonging to a single annotated
     * field, because those all have the same length.
     */
    static class LengthsAndOffsetsPerDocument {
        /** For each document id (key), the length in tokens of the annotated field this Lucene field is a part of.
         *  (shared with other annotations on this annotated field) */
        private final Map<Integer, Integer> docId2annotatedFieldTokenLengths;

        /** For each document id (key), record the term vector file offsets (value). */
        private final SortedMap<Integer, TermVecFileOffsetPerTermId> docId2TermVecFileOffsets = new TreeMap<>();

        LengthsAndOffsetsPerDocument(Map<Integer, Integer> docId2annotatedFieldTokenLengths) {
            this.docId2annotatedFieldTokenLengths = docId2annotatedFieldTokenLengths;
        }

        void putTermOffset(int docId, int termId, long filePointer) {
            TermVecFileOffsetPerTermId vecFileOffsetsPerTermId =
                    docId2TermVecFileOffsets.computeIfAbsent(docId, k -> new TermVecFileOffsetPerTermId());
            vecFileOffsetsPerTermId.put(termId, filePointer);
        }

        void updateFieldLength(int docId, int length) {
            docId2annotatedFieldTokenLengths.compute(docId, (k, v) -> v == null ? length : Math.max(v, length));
        }

        TermVecFileOffsetPerTermId get(int docId) {
            TermVecFileOffsetPerTermId termVecFileOffsetPerTermId = docId2TermVecFileOffsets.get(docId);
            if (termVecFileOffsetPerTermId == null) {
                termVecFileOffsetPerTermId = new TermVecFileOffsetPerTermId();
            }
            return termVecFileOffsetPerTermId;
        }
    }

}
