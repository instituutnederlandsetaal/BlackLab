package nl.inl.blacklab.codec.blacklab50;

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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.codecs.FieldsConsumer;
import org.apache.lucene.codecs.FieldsProducer;
import org.apache.lucene.codecs.NormsProducer;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.index.MappedMultiFields;
import org.apache.lucene.index.MergeState;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.ReaderSlice;
import org.apache.lucene.index.SegmentWriteState;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.ByteArrayDataOutput;
import org.apache.lucene.store.DataOutput;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.util.BytesRef;

import it.unimi.dsi.fastutil.ints.IntArrays;
import nl.inl.blacklab.analysis.PayloadUtils;
import nl.inl.blacklab.codec.BlackLabPostingsFormat;
import nl.inl.blacklab.codec.BlackLabPostingsWriter;
import nl.inl.blacklab.codec.ForwardIndexField;
import nl.inl.blacklab.codec.ForwardIndexFieldMutable;
import nl.inl.blacklab.codec.ThreeByteInt;
import nl.inl.blacklab.codec.TokensCodec;
import nl.inl.blacklab.codec.TokensCodec.VALUE_PER_TOKEN_PARAMETER;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.forwardindex.Collators;
import nl.inl.blacklab.search.BlackLabIndexIntegrated;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

/**
 * BlackLab FieldsConsumer: writes postings information to the index,
 * using a delegate and extending its functionality by also writing a forward
 * index.
 *
 * Adapted from <a href="https://github.com/meertensinstituut/mtas/">MTAS</a>.
 */
public class BlackLab50PostingsWriter extends BlackLabPostingsWriter {

    protected static final Logger logger = LogManager.getLogger(BlackLab50PostingsWriter.class);

    private static final int NO_TERM = nl.inl.blacklab.forwardindex.Terms.NO_TERM;

    /** The FieldsConsumer we're adapting and delegating some requests to. */
    private final FieldsConsumer delegateFieldsConsumer;

    /** Holds common information used for writing to index files. */
    private final SegmentWriteState state;

    /** Name of the postings format we've adapted. */
    private final String delegatePostingsFormatName;

    /**
     * Instantiates a fields consumer.
     *
     * @param delegateFieldsConsumer FieldsConsumer to be adapted by us
     * @param state holder class for common parameters used during write
     * @param delegatePostingsFormatName name of the delegate postings format
     *                                   (the one our PostingsFormat class adapts)
     */
    public BlackLab50PostingsWriter(FieldsConsumer delegateFieldsConsumer, SegmentWriteState state,
            String delegatePostingsFormatName) {
        this.delegateFieldsConsumer = delegateFieldsConsumer;
        this.state = state;
        this.delegatePostingsFormatName = delegatePostingsFormatName;
    }

    /**
     * Merges in the fields from the readers in <code>mergeState</code>.
     *
     * Identical to {@link FieldsConsumer#merge}, essentially cancelling the delegate's
     * own merge method, e.g. FieldsWriter#merge in
     * {@link org.apache.lucene.codecs.perfield.PerFieldPostingsFormat}}.
     *
     * As suggested by the name and above comments, this seems to be related to segment merging.
     * Notice the call to write() at the end of the method, writing the merged segment to disk.
     *
     * (not sure why this is done; presumably the overridden merge method caused problems?
     * the javadoc for FieldsConsumer's version does mention that subclasses can provide more sophisticated
     * merging; maybe that interferes with this FieldsConsumer's customizations?)
     */
    @Override
    public void merge(MergeState mergeState, NormsProducer norms) throws IOException {
        final List<Fields> fields = new ArrayList<>();
        final List<ReaderSlice> slices = new ArrayList<>();

        int docBase = 0;

        for (int readerIndex = 0; readerIndex < mergeState.fieldsProducers.length; readerIndex++) {
            final FieldsProducer f = mergeState.fieldsProducers[readerIndex];

            final int maxDoc = mergeState.maxDocs[readerIndex];
            f.checkIntegrity();
            slices.add(new ReaderSlice(docBase, maxDoc, readerIndex));
            fields.add(f);
            docBase += maxDoc;
        }

        Fields mergedFields = new MappedMultiFields(mergeState,
                new MultiFields(fields.toArray(Fields.EMPTY_ARRAY),
                        slices.toArray(ReaderSlice.EMPTY_ARRAY)));
        write(mergedFields, norms);
    }

    /**
     * Called by Lucene to write fields, terms and postings.
     *
     * Seems to be called whenever a segment is written, either initially or after
     * a segment merge.
     *
     * Delegates to the default fields consumer, but also uses the opportunity
     * to write our forward index.
     *
     * @param fields fields to write
     * @param norms norms (not used by us)
     */
    @Override
    public void write(Fields fields, NormsProducer norms) throws IOException {
        write(state.fieldInfos, fields);
        delegateFieldsConsumer.write(fields, norms);
    }

    /**
     * Offset in term vector file per term id for a single doc and field.
     *
     * Keeps track of position in the term vector file where the occurrences of each term id
     * are stored for a single document and field.
     */
    private static class TermVecFileOffsetPerTermId extends LinkedHashMap<Integer, Long> {
        // intentionally blank; this is just a typedef for readability
    }

    /**
     * Keeps track of tokens length and term vector file offsets for a single Lucene field.
     *
     * Tokens length is shared between all Lucene fields belonging to a single annotated
     * field, because those all have the same length.
     */
    private static class LengthsAndOffsetsPerDocument {
        /** For each document id (key), the length in tokens of the annotated field this Lucene field is a part of.
         *  (shared with other annotations on this annotated field) */
        private final Map<Integer, Integer> docId2annotatedFieldTokenLengths;

        /** For each document id (key), record the term vector file offsets (value). */
        private final SortedMap<Integer, TermVecFileOffsetPerTermId> docId2TermVecFileOffsets = new TreeMap<>();

        LengthsAndOffsetsPerDocument(Map<Integer, Integer> docId2annotatedFieldTokenLengths) {
            this.docId2annotatedFieldTokenLengths = docId2annotatedFieldTokenLengths;
        }

        void putTermOffset(Integer docId, int termId, long filePointer) {
            TermVecFileOffsetPerTermId vecFileOffsetsPerTermId =
                    docId2TermVecFileOffsets.computeIfAbsent(docId, k -> new TermVecFileOffsetPerTermId());
            vecFileOffsetsPerTermId.put(termId, filePointer);
        }

        void updateFieldLength(Integer docId, int length) {
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

    /**
     * Write our additions to the default postings (i.e. the forward index)
     *
     * Iterates over the term vector to build the forward index in a temporary file.
     *
     * Tokens are sorted by field, term, doc, then position, so not by field, doc, position as
     * you might expect with a forward index. This is a temporary measure for efficiency.
     *
     * The second pass links all the doc+position for each term together and writes them to another
     * temporary file.
     *
     * Finally, everything is written to the final objects file in the correct order.
     *
     * This method also records metadata about fields in the FieldInfo attributes.
     */
    private void write(FieldInfos fieldInfos, Fields fields) {

        Map<String, ForwardIndexFieldMutable> fiFields = new HashMap<>();

        try (   IndexOutput outTokensIndexFile = createOutput(BlackLabPostingsFormat.TOKENS_INDEX_EXT);
                IndexOutput outTokensFile = createOutput(BlackLabPostingsFormat.TOKENS_EXT);
                IndexOutput termIndexFile = createOutput(BlackLabPostingsFormat.TERMINDEX_EXT);
                IndexOutput termsFile = createOutput(BlackLabPostingsFormat.TERMS_EXT);
                IndexOutput termsOrderFile = createOutput(BlackLabPostingsFormat.TERMORDER_EXT)
        ) {

            // Write our postings extension information



            // We'll keep track of doc lengths so we can preallocate our forward index structure.
            // (we do this per annotated field, e.g. contents, NOT per annotation, e.g. contents%word@s,
            //  because all annotations on the same field have the same length)
            Map<String, Map<Integer, Integer>> docLengthsPerAnnotatedField = new HashMap<>();

            // First we write a temporary dump of the term vector, and keep track of
            // where we can find term occurrences per document so we can reverse this
            // file later.
            // (we iterate per field & term first, because that is how Lucene's reverse
            //  index stores the information. What we need is per field, then per document
            //  (we're trying to reconstruct the document), so we will do that below.
            //   we use temporary files because this might take a huge amount of memory)
            // (use a LinkedHashMap to maintain the same field order when we write the tokens below)
            Map<String, LengthsAndOffsetsPerDocument> field2docTermVecFileOffsets = new LinkedHashMap<>();
            try (IndexOutput outTempTermVectorFile = createOutput(BlackLabPostingsFormat.TERMVEC_TMP_EXT)) {

                // Process fields
                for (String luceneField: fields) { // for each field
                    // Make sure doc lengths are shared between all annotations for a single annotated field.
                    String annotatedFieldName = AnnotatedFieldNameUtil.getBaseName(luceneField);
                    Map<Integer, Integer> docLengths = docLengthsPerAnnotatedField.computeIfAbsent(
                            annotatedFieldName, __ -> new HashMap<>());

                    // If this field should get a forward index...
                    if (!BlackLabIndexIntegrated.doesFieldHaveForwardIndex(fieldInfos.fieldInfo(luceneField))) {
                        continue;
                    }
                    ForwardIndexFieldMutable offsets = fiFields.computeIfAbsent(luceneField, ForwardIndexFieldMutable::new);

                    // We're creating a forward index for this field.
                    // That also means that the payloads will include an "is-primary-value" indicator,
                    // so we know which value to store in the forward index (the primary value, i.e.
                    // the first value, to be used in concordances, sort, group, etc.).
                    // We must skip these indicators when working with other payload info. See PayloadUtils
                    // for details.

                    // Record starting offset of field in termindex file (written to fields file later)
                    offsets.setTermIndexOffset(termIndexFile.getFilePointer());

                    // Keep track of where to find term positions for each document
                    // (for reversing index)
                    // The map is keyed by docId and stores a list of offsets into the
                    // temporary termvector file where the occurrences for each term can be
                    // found.
                    LengthsAndOffsetsPerDocument lengthsAndOffsetsPerDocument =
                            field2docTermVecFileOffsets.computeIfAbsent(luceneField,
                                    __ -> new LengthsAndOffsetsPerDocument(docLengths));

                    // For each term in this field...
                    PostingsEnum postingsEnum = null; // we'll reuse this for efficiency
                    Terms terms = fields.terms(luceneField);
                    TermsEnum termsEnum = terms.iterator();


                    int termId = 0;
                    List<String> termsList = new ArrayList<>();

                    while (true) {
                        BytesRef term = termsEnum.next();
                        if (term == null)
                            break;

                        // Write the term to the terms file
                        String termString = term.utf8ToString();
                        termIndexFile.writeLong(termsFile.getFilePointer()); // where to find term string
                        termsFile.writeString(termString);
                        termsList.add(termString);

                        // For each document containing this term...
                        postingsEnum = termsEnum.postings(postingsEnum, PostingsEnum.POSITIONS | PostingsEnum.PAYLOADS);
                        while (true) {
                            Integer docId = postingsEnum.nextDoc();
                            if (docId.equals(DocIdSetIterator.NO_MORE_DOCS))
                                break;

                            // Keep track of term positions offsets in term vector file
                            lengthsAndOffsetsPerDocument.putTermOffset(docId, termId,
                                    outTempTermVectorFile.getFilePointer());

                            // Go through each occurrence of term in this doc,
                            // gathering the positions where this term occurs as a "primary value"
                            // (the first value at this token position, which we will store in the
                            //  forward index). Also determine docLength.
                            int nOccurrences = postingsEnum.freq();
                            int docLength = -1;
                            byte[] bytesPositions = new byte[nOccurrences * Integer.BYTES];
                            DataOutput positions = new ByteArrayDataOutput(bytesPositions);
                            int numOccurrencesWritten = 0;
                            for (int i = 0; i < nOccurrences; i++) {
                                int position = postingsEnum.nextPosition();
                                if (position >= docLength)
                                    docLength = position + 1;

                                // Is this a primary value or a secondary one?
                                // Primary values are e.g. the original word from the document,
                                // and will be stored in the forward index to be used for concordances,
                                // sorting and grouping. Secondary values may be synonyms or stemmed versions
                                // and will not be stored in the forward index.
                                BytesRef payload = postingsEnum.getPayload();
                                if (PayloadUtils.isPrimaryValue(payload)) {
                                    // primary value; write to buffer
                                    positions.writeInt(position);
                                    numOccurrencesWritten++;
                                }
                            }
                            if (docLength > -1)
                                lengthsAndOffsetsPerDocument.updateFieldLength(docId, docLength);

                            // Write the positions where this term occurs as primary value
                            // (will be reversed below to get the forward index)
                            outTempTermVectorFile.writeInt(numOccurrencesWritten);
                            if (numOccurrencesWritten > 0) {
                                outTempTermVectorFile.writeBytes(bytesPositions, 0,
                                        numOccurrencesWritten * Integer.BYTES);
                            }
                        }

                        termId++;
                    }

                    // begin writing term IDs and sort orders
                    Collators collators = Collators.defaultCollator();
                    int[] sensitivePos2TermID = getTermSortOrder(termsList, collators.get(MatchSensitivity.SENSITIVE));
                    int[] insensitivePos2TermID = getTermSortOrder(termsList, collators.get(MatchSensitivity.INSENSITIVE));
                    int[] termID2SensitivePos = invert(termsList, sensitivePos2TermID, collators.get(MatchSensitivity.SENSITIVE));
                    int[] termID2InsensitivePos = invert(termsList, insensitivePos2TermID, collators.get(MatchSensitivity.INSENSITIVE));

                    int numTerms = termsList.size();
                    fiFields.get(luceneField).setNumberOfTerms(numTerms);
                    fiFields.get(luceneField).setTermOrderOffset(termsOrderFile.getFilePointer());
                    // write out, specific order.
                    for (int i : termID2InsensitivePos) termsOrderFile.writeInt(i);
                    for (int i : insensitivePos2TermID) termsOrderFile.writeInt(i);
                    for (int i : termID2SensitivePos) termsOrderFile.writeInt(i);
                    for (int i : sensitivePos2TermID) termsOrderFile.writeInt(i);
                } // for each field
                CodecUtil.writeFooter(outTempTermVectorFile);
            }

            // Reverse the reverse index to create forward index
            // (this time we iterate per field and per document first, then reconstruct the document by
            //  looking at each term's occurrences. This produces our forward index)
            try (IndexInput inTermVectorFile = openInput(BlackLabPostingsFormat.TERMVEC_TMP_EXT)) {

                // For each field...
                for (Entry<String, LengthsAndOffsetsPerDocument> fieldEntry: field2docTermVecFileOffsets.entrySet()) {
                    String luceneField = fieldEntry.getKey();
                    LengthsAndOffsetsPerDocument docPosOffsets = fieldEntry.getValue();

                    // Record starting offset of field in tokensindex file (written to fields file later)
                    fiFields.get(luceneField).setTokensIndexOffset(outTokensIndexFile.getFilePointer());

                    // Make sure we know our document lengths
                    String annotatedFieldName = AnnotatedFieldNameUtil.getBaseName(luceneField);
                    Map<Integer, Integer> docLengths = docLengthsPerAnnotatedField.get(annotatedFieldName);

                    // For each document...
                    for (int docId = 0; docId < state.segmentInfo.maxDoc(); docId++) {
                        final int docLength = docLengths.getOrDefault(docId, 0);
                        TermVecFileOffsetPerTermId offsets = docPosOffsets.get(docId);
                        int[] termIds = getDocumentContents(docLength, inTermVectorFile, offsets);
                        writeTokensInDoc(outTokensIndexFile, outTokensFile, termIds);
                    }
                }
            } finally {
                // Clean up after ourselves
                deleteIndexFile(BlackLabPostingsFormat.TERMVEC_TMP_EXT);
            }

            // Write fields file, now that we know all the relevant offsets
            try (IndexOutput fieldsFile = createOutput(BlackLabPostingsFormat.FIELDS_EXT)) {
                // for each field that has a forward index...
                for (ForwardIndexField field : fiFields.values()) {
                    // write the information to fields file, see integrated.md
                    field.write(fieldsFile);
                }
                CodecUtil.writeFooter(fieldsFile);
            }

            CodecUtil.writeFooter(outTokensIndexFile);
            CodecUtil.writeFooter(outTokensFile);
            CodecUtil.writeFooter(termIndexFile);
            CodecUtil.writeFooter(termsFile);
            CodecUtil.writeFooter(termsOrderFile);
        } catch (IOException e) {
            throw new BlackLabRuntimeException(e);
        }
    }

    private int[] getDocumentContents(int docLength,
            IndexInput inTermVectorFile, TermVecFileOffsetPerTermId termPosOffsets)
            throws IOException {

        final int[] tokensInDoc = new int[docLength]; // reconstruct the document here

        // NOTE: sometimes docs won't have any values for a field, but we'll
        //   still write all NO_TERMs in this case. This is similar to sparse
        //   fields (e.g. the field that stores <p> <s> etc.) which also have a
        //   lot of NO_TERMs.
        Arrays.fill(tokensInDoc, NO_TERM);

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
    private void writeTokensInDoc(IndexOutput outTokensIndexFile, IndexOutput outTokensFile, int[] tokensInDoc) throws IOException {
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
                if (min >= Byte.MIN_VALUE && max <= Byte.MAX_VALUE) tokensCodecParameter = VALUE_PER_TOKEN_PARAMETER.BYTE.code;
                else if (min >= Short.MIN_VALUE && max <= Short.MAX_VALUE) tokensCodecParameter = VALUE_PER_TOKEN_PARAMETER.SHORT.code;
                else if (min >= ThreeByteInt.MIN_VALUE && max <= ThreeByteInt.MAX_VALUE) tokensCodecParameter = VALUE_PER_TOKEN_PARAMETER.THREE_BYTES.code;
                else tokensCodecParameter = VALUE_PER_TOKEN_PARAMETER.INT.code;
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
            switch (VALUE_PER_TOKEN_PARAMETER.fromCode(tokensCodecParameter)) {
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

    private IndexOutput createOutput(String ext) throws IOException {
        String fileName = IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, ext);
        IndexOutput output = state.directory.createOutput(fileName, state.context);

        // Write standard header, with the codec name and version, segment info.
        // Also write the delegate codec name (Lucene's default codec).
        CodecUtil.writeIndexHeader(output, BlackLab50PostingsFormat.NAME, BlackLab50PostingsFormat.VERSION_CURRENT,
                state.segmentInfo.getId(), state.segmentSuffix);
        output.writeString(delegatePostingsFormatName);

        return output;
    }

    public IndexInput openInputCorrectEndian(Directory directory, String fileName, IOContext ioContext) throws IOException {
        return directory.openInput(fileName, ioContext);
    }

    @SuppressWarnings("SameParameterValue")
    private IndexInput openInput(String ext) throws IOException {
        String fileName = IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, ext);
        // NOTE: we have to deal with Lucene 9's switch to little-endian.
        //IndexInput input = state.directory.openInput(fileName, state.context);
        IndexInput input = openInputCorrectEndian(state.directory, fileName, state.context);

        // Read and check standard header, with codec name and version and segment info.
        // Also check the delegate codec name (should be the expected version of Lucene's codec).
        CodecUtil.checkIndexHeader(input, BlackLab50PostingsFormat.NAME, BlackLab50PostingsFormat.VERSION_START,
                BlackLab50PostingsFormat.VERSION_CURRENT, state.segmentInfo.getId(), state.segmentSuffix);
        String delegatePFN = input.readString();
        if (!delegatePostingsFormatName.equals(delegatePFN))
            throw new IOException("Segment file " + fileName +
                    " contains wrong delegate postings format name: " + delegatePFN +
                    " (expected " + delegatePostingsFormatName + ")");

        return input;
    }

    @SuppressWarnings("SameParameterValue")
    private void deleteIndexFile(String ext) throws IOException {
        String fileName = IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, ext);
        state.directory.deleteFile(fileName);
    }

    @Override
    public void close() throws IOException {
        delegateFieldsConsumer.close();
    }
    /**
     * Given a list of terms, return the indices to sort them.
     * E.G: getTermSortOrder(['b','c','a']) --> [2, 0, 1]
     */
    private static int[] getTermSortOrder(List<String> terms, Collator coll) {
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
    private static int[] invert(List<String> terms, int[] array, Collator collator) {
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
}
