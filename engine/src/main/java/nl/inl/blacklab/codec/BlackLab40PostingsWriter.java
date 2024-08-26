package nl.inl.blacklab.codec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.util.BytesRef;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.BlackLabIndexIntegrated;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.lucene.RelationInfo;

/**
 * BlackLab FieldsConsumer: writes postings information to the index,
 * using a delegate and extending its functionality by also writing a forward
 * index.
 *
 * Adapted from <a href="https://github.com/meertensinstituut/mtas/">MTAS</a>.
 */
public class BlackLab40PostingsWriter extends BlackLabPostingsWriter {

    protected static final Logger logger = LogManager.getLogger(BlackLab40PostingsWriter.class);

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
    public BlackLab40PostingsWriter(FieldsConsumer delegateFieldsConsumer, SegmentWriteState state,
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

    public int maxDoc() {
        return state.segmentInfo.maxDoc();
    }

    /**
     * Write our additions to the default postings (forward index and relations info)
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
        List<PWPlugin> allActions = new ArrayList<>();
        try {
            allActions.add(new PWPluginForwardIndex(this));
            if (RelationInfo.writeRelationInfoToIndex())
                allActions.add(new PWPluginRelationInfo(this));

            // Write our postings extension information

            // Process fields
            for (String luceneField: fields) { // for each field

                // Is this the relation annotation? Then we want to store relation info such as attribute values,
                // so we can look them up for individual relations matched.
                boolean storeRelationInfo = false;
                String[] nameComponents = AnnotatedFieldNameUtil.getNameComponents(luceneField);
                if (nameComponents.length > 1 && AnnotatedFieldNameUtil.isRelationAnnotation(nameComponents[1])) {
                    // Yes, store relation info.
                    storeRelationInfo = true;
                }

                // Should this field get a forward index?
                boolean storeForwardIndex = BlackLabIndexIntegrated.doesFieldHaveForwardIndex(
                        fieldInfos.fieldInfo(luceneField));

                // If we don't need to do any per-term processing, continue
                if (!storeForwardIndex && !storeRelationInfo)
                    continue;

                // Determine what actions to perform for this field
                List<PWPlugin> actions = new ArrayList<>();
                for (PWPlugin action: allActions) {
                    if (action.startField(fieldInfos.fieldInfo(luceneField)))
                        actions.add(action);
                }
                if (actions.isEmpty())
                    continue;

                // For each term in this field...
                PostingsEnum postingsEnum = null; // we'll reuse this for efficiency
                Terms terms = fields.terms(luceneField);
                TermsEnum termsEnum = terms.iterator();
                while (true) {
                    BytesRef term = termsEnum.next();
                    if (term == null)
                        break;

                    for (PWPlugin action: actions) action.startTerm(term);

                    // For each document containing this term...
                    postingsEnum = termsEnum.postings(postingsEnum, PostingsEnum.POSITIONS | PostingsEnum.PAYLOADS);
                    while (true) {
                        int docId = postingsEnum.nextDoc();
                        if (docId == DocIdSetIterator.NO_MORE_DOCS)
                            break;

                        // Go through each occurrence of term in this doc,
                        // gathering the positions where this term occurs as a "primary value"
                        // (the first value at this token position, which we will store in the
                        //  forward index). Also determine docLength.
                        int nOccurrences = postingsEnum.freq();
                        for (PWPlugin action: actions)
                            action.startDocument(docId, nOccurrences);
                        for (int i = 0; i < nOccurrences; i++) {
                            int position = postingsEnum.nextPosition();
                            BytesRef payload = postingsEnum.getPayload();
                            for (PWPlugin action: actions) action.termOccurrence(position, payload);
                        }
                        for (PWPlugin action: actions) action.endDocument();
                    }
                    for (PWPlugin action: actions) action.endTerm();
                }
                for (PWPlugin action: actions) action.endField();
            } // for each field

            for (PWPlugin action: allActions) action.finalize();
        } catch (IOException e) {
            throw new BlackLabRuntimeException(e);
        } finally {
            try {
                for (PWPlugin action: allActions) action.close();
            } catch (IOException e) {
                throw new BlackLabRuntimeException(e);
            }
        }
    }

    IndexOutput createOutput(String ext) throws IOException {
        String fileName = IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, ext);
        IndexOutput output = state.directory.createOutput(fileName, state.context);

        // Write standard header, with the codec name and version, segment info.
        // Also write the delegate codec name (Lucene's default codec).
        CodecUtil.writeIndexHeader(output, BlackLab40PostingsFormat.NAME, BlackLab40PostingsFormat.VERSION_CURRENT,
                state.segmentInfo.getId(), state.segmentSuffix);
        output.writeString(delegatePostingsFormatName);

        return output;
    }

    /** Lucene 8 uses big-endian, Lucene 9 little-endian */
    public IndexInput openInputCorrectEndian(Directory directory, String fileName, IOContext ioContext) throws IOException {
        return directory.openInput(fileName, ioContext);
    }

    @SuppressWarnings("SameParameterValue")
    IndexInput openInput(String ext) throws IOException {
        String fileName = IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, ext);
        IndexInput input = openInputCorrectEndian(state.directory, fileName, state.context);

        // Read and check standard header, with codec name and version and segment info.
        // Also check the delegate codec name (should be the expected version of Lucene's codec).
        CodecUtil.checkIndexHeader(input, BlackLab40PostingsFormat.NAME, BlackLab40PostingsFormat.VERSION_START,
                BlackLab40PostingsFormat.VERSION_CURRENT, state.segmentInfo.getId(), state.segmentSuffix);
        String delegatePFN = input.readString();
        if (!delegatePostingsFormatName.equals(delegatePFN))
            throw new IOException("Segment file " + fileName +
                    " contains wrong delegate postings format name: " + delegatePFN +
                    " (expected " + delegatePostingsFormatName + ")");

        return input;
    }

    @SuppressWarnings("SameParameterValue")
    void deleteIndexFile(String ext) throws IOException {
        String fileName = IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, ext);
        state.directory.deleteFile(fileName);
    }

    @Override
    public void close() throws IOException {
        delegateFieldsConsumer.close();
    }

}
