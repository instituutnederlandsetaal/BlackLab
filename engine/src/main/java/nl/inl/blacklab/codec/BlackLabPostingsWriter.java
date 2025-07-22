package nl.inl.blacklab.codec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.backward_codecs.store.EndiannessReverserUtil;
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

import nl.inl.blacklab.exceptions.IndexVersionMismatch;
import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.search.BlackLabIndexIntegrated;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.RelationsStrategy;
import nl.inl.blacklab.search.indexmetadata.RelationsStrategySeparateTerms;

public abstract class BlackLabPostingsWriter extends FieldsConsumer {

    protected static final Logger logger = LogManager.getLogger(BlackLabPostingsWriter.class);

    /** The FieldsConsumer we're adapting and delegating some requests to. */
    protected final FieldsConsumer delegateFieldsConsumer;

    /** Holds common information used for writing to index files. */
    protected final SegmentWriteState state;

    /** Name of the postings format we've adapted. */
    protected final String delegatePostingsFormatName;

    /** Extensions to our PostingsWriter (write the forward index and relation index) */
    protected final List<PWPlugin> plugins;

    /** Name of the postings format, for writing and checking the header. */
    final String postingsFormatName;

    /** Minimum version, for checking the header. */
    final int minVersion;

    /** Current version, for writing and checking the header. */
    final int currentVersion;

    private final boolean reverseEndian;

    public BlackLabPostingsWriter(String postingsFormatName, int minVersion, int currentVersion,
            FieldsConsumer delegateFieldsConsumer, SegmentWriteState state, String delegatePostingsFormatName,
            boolean reverseEndian) {
        this.postingsFormatName = postingsFormatName;
        this.minVersion = minVersion;
        this.currentVersion = currentVersion;
        this.delegateFieldsConsumer = delegateFieldsConsumer;
        this.state = state;
        this.delegatePostingsFormatName = delegatePostingsFormatName;
        this.reverseEndian = reverseEndian;
        plugins = new ArrayList<>();

        RelationsStrategy relationsStrategy = RelationsStrategy.forNewIndex();

        try {
            plugins.add(new PWPluginForwardIndex(this));
            if (relationsStrategy.writeRelationInfoToIndex()) {
                if (relationsStrategy instanceof RelationsStrategySeparateTerms) {
                    // This is the current version of the relation info plugin, used for new indexes.
                    plugins.add(new PWPluginRelationInfo(this, (RelationsStrategySeparateTerms) relationsStrategy));
                } else {
                    throw new IndexVersionMismatch("Unknown relationsStrategy: " + relationsStrategy.getName() + "; likely an older index, please re-index your data.");
                }
            }
        } catch (IOException e) {
            // Something went wrong, e.g. we couldn't create the output files.
            throw new InvalidIndex("Error initializing PostingsWriter plugins", e);
        }
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

    protected int maxDoc() {
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
        try {
            // Write our postings extension information

            // Process fields
            for (String luceneField: fields) { // for each field
                if (!forwardIndexOrRelationAnnotation(fieldInfos, luceneField)) {
                    // We don't need to do any per-term processing.
                    continue;
                }

                // Call the startField() method of each plugin to determine
                // which actions to perform for this field.
                List<PWPlugin> actions = startField(fieldInfos, luceneField);
                if (actions.isEmpty())
                    continue; // nothing to do for this field

                // For each term in this field...
                PostingsEnum postingsEnum = null; // we'll reuse this for efficiency
                Terms terms = fields.terms(luceneField);
                TermsEnum termsEnum = terms.iterator();
                while (true) {
                    BytesRef term = termsEnum.next();
                    if (term == null)
                        break;

                    postingsEnum = handleTerm(actions, term, postingsEnum, termsEnum);
                }
                endField(actions);
            } // for each field
        } catch (IOException e) {
            throw new InvalidIndex(e);
        }
    }

    private static PostingsEnum handleTerm(List<PWPlugin> actions, BytesRef term, PostingsEnum postingsEnum,
            TermsEnum termsEnum) throws IOException {
        startTerm(actions, term);

        // For each document containing this term...
        postingsEnum = termsEnum.postings(postingsEnum, PostingsEnum.POSITIONS | PostingsEnum.PAYLOADS);
        while (true) {
            int docId = postingsEnum.nextDoc();
            if (docId == DocIdSetIterator.NO_MORE_DOCS)
                break;

            processDocument(postingsEnum, actions, docId);
        }
        endTerm(actions);
        return postingsEnum;
    }

    private static void processDocument(PostingsEnum postingsEnum, List<PWPlugin> actions, int docId) throws IOException {
        // Go through each occurrence of term in this doc,
        // gathering the positions where this term occurs as a "primary value"
        // (the first value at this token position, which we will store in the
        //  forward index). Also determine docLength.
        int nOccurrences = postingsEnum.freq();
        startDocument(actions, docId, nOccurrences);
        for (int i = 0; i < nOccurrences; i++) {
            int position = postingsEnum.nextPosition();
            BytesRef payload = postingsEnum.getPayload();
            for (PWPlugin action: actions)
                action.termOccurrence(position, payload);
        }
        endDocument(actions);
    }

    private static boolean forwardIndexOrRelationAnnotation(FieldInfos fieldInfos, String luceneField) {
        // Is this the relation annotation? Then we want to store relation info such as attribute values,
        // so we can look them up for individual relations matched.
        boolean storeRelationInfo = isStoreRelationInfo(luceneField);

        // Should this field get a forward index?
        boolean storeForwardIndex = BlackLabIndexIntegrated.doesFieldHaveForwardIndex(
                fieldInfos.fieldInfo(luceneField));
        return storeForwardIndex || storeRelationInfo;
    }

    private static void endField(List<PWPlugin> actions) throws IOException {
        for (PWPlugin action: actions)
            action.endField();
    }

    private static void endTerm(List<PWPlugin> actions) {
        for (PWPlugin action: actions)
            action.endTerm();
    }

    private static void endDocument(List<PWPlugin> actions) throws IOException {
        for (PWPlugin action: actions)
            action.endDocument();
    }

    private static void startDocument(List<PWPlugin> actions, int docId, int nOccurrences) {
        for (PWPlugin action: actions)
            action.startDocument(docId, nOccurrences);
    }

    private static void startTerm(List<PWPlugin> actions, BytesRef term) throws IOException {
        for (PWPlugin action: actions)
            action.startTerm(term);
    }

    private List<PWPlugin> startField(FieldInfos fieldInfos, String luceneField) {
        List<PWPlugin> actions = new ArrayList<>();
        for (PWPlugin action: plugins) {
            // Check if this applies to this field or not
            if (action.startField(fieldInfos.fieldInfo(luceneField)))
                actions.add(action); // yes
        }
        return actions;
    }

    /** Is this the field in which we should store relation info?
     *  E.g. contents%_relation@s */
    private static boolean isStoreRelationInfo(String luceneField) {
        boolean storeRelationInfo = false;
        String[] nameComponents = AnnotatedFieldNameUtil.getNameComponents(luceneField);
        if (nameComponents.length > 1 && nameComponents[1].equals(
                AnnotatedFieldNameUtil.RELATIONS_ANNOT_NAME)) {
            // Yes, store relation info.
            storeRelationInfo = true;
        }
        return storeRelationInfo;
    }

    IndexOutput createOutput(String ext) throws IOException {
        String fileName = IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, ext);
        IndexOutput output;
        if (reverseEndian) {
            output = EndiannessReverserUtil.createOutput(state.directory, fileName, state.context); // flip for Lucene 9
        } else
            output = state.directory.createOutput(fileName, state.context);

        // Write standard header, with the codec name and version, segment info.
        // Also write the delegate codec name (Lucene's default codec).
        CodecUtil.writeIndexHeader(output, postingsFormatName, currentVersion,
                state.segmentInfo.getId(), state.segmentSuffix);
        output.writeString(delegatePostingsFormatName);

        return output;
    }

    /** Lucene 8 uses big-endian, Lucene 9 little-endian */
    public IndexInput openInputCorrectEndian(Directory directory, String fileName, IOContext ioContext) throws IOException {
        if (reverseEndian) {
            return EndiannessReverserUtil.openInput(directory, fileName, ioContext); // flip for Lucene 9
        } else
            return directory.openInput(fileName, ioContext);
    }

    @SuppressWarnings("SameParameterValue")
    IndexInput openInput(String ext) throws IOException {
        String fileName = IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, ext);
        IndexInput input = openInputCorrectEndian(state.directory, fileName, state.context);

        // Read and check standard header, with codec name and version and segment info.
        // Also check the delegate codec name (should be the expected version of Lucene's codec).
        CodecUtil.checkIndexHeader(input, postingsFormatName, minVersion,
                currentVersion, state.segmentInfo.getId(), state.segmentSuffix);
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
        try {
            for (PWPlugin action: plugins) {
                action.finish();
            }
        } finally {
            for (PWPlugin action: plugins)
                action.close();
        }
        delegateFieldsConsumer.close();
    }

    public String getSegmentName() {
        return state.segmentInfo.name;
    }
}
