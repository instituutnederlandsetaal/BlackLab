package nl.inl.blacklab.codec;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.backward_codecs.store.EndiannessReverserUtil;
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.codecs.FieldsProducer;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.Terms;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;

import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.forwardindex.ForwardIndexSegmentReader;
import nl.inl.blacklab.forwardindex.RelationInfoSegmentReader;

public abstract class BlackLabPostingsReader extends FieldsProducer {

    protected static final Logger logger = LogManager.getLogger(BlackLabPostingsReader.class);

    /** The delegate whose functionality we're extending */
    protected final FieldsProducer delegateFieldsProducer;

    /** The forward index */
    protected final SegmentForwardIndex forwardIndex;

    /** The relation info (if it was stored) */
    protected final SegmentRelationInfo relationInfo;

    /** Postings format name, for checking the header */
    final String postingsFormatName;

    /** Minimum version, for checking the header */
    final int minVersion;

    /** Maximum version, for checking the header */
    final int maxVersion;

    protected final SegmentReadState state;

    private final boolean reverseEndian;

    /** Terms object for each field */
    private final Map<String, BLTerms> termsPerField = new HashMap<>();

    /** Name of PF we delegate to (the one from Lucene) */
    protected String delegateFormatName;

    public BlackLabPostingsReader(String postingsFormatName, int minVersion, int maxVersion, SegmentReadState state, boolean reverseEndian) throws IOException {
        this.postingsFormatName = postingsFormatName;
        this.minVersion = minVersion;
        this.maxVersion = maxVersion;
        this.state = state;
        this.reverseEndian = reverseEndian;

        forwardIndex = new SegmentForwardIndex(this);
        relationInfo = SegmentRelationInfo.openIfPresent(this);

        // NOTE: opening the forward index calls openInputFile, which reads
        //       delegatePostingsFormatName, so this must be done first.
        if (delegateFormatName == null)
            throw new IllegalStateException("Opening the segment FI should have set the delegate format name");
        PostingsFormat delegatePostingsFormat = PostingsFormat.forName(delegateFormatName);
        delegateFieldsProducer = delegatePostingsFormat.fieldsProducer(state);
    }

    public BlackLabStoredFieldsReader getStoredFieldsReader() {
        try {
            BlackLabCodec codec = (BlackLabCodec) state.segmentInfo.getCodec();
            return codec.storedFieldsFormat().fieldsReader(
                    state.directory, state.segmentInfo, state.fieldInfos, state.context);
        } catch (IOException e) {
            throw new InvalidIndex(e);
        }
    }

    /**
     * Create a forward index reader for this segment.
     *
     * The returned reader is not threadsafe and shouldn't be stored.
     * A single thread may use it for reading from this segment. It
     * can then be discarded.
     *
     * @return forward index segment reader
     */
    public ForwardIndexSegmentReader forwardIndex() {
        return forwardIndex.reader();
    }

    /**
     * Create a relation info reader for this segment.
     *
     * The returned reader is not threadsafe and shouldn't be stored.
     * A single thread may use it for reading from this segment. It
     * can then be discarded.
     *
     * @return relation info segment reader if available, otherwise null
     */
    public RelationInfoSegmentReader relationInfo() {
        return relationInfo == null ? null : relationInfo.reader();
    }

    @Override
    public Iterator<String> iterator() {
        return delegateFieldsProducer.iterator();
    }

    @Override
    public void close() throws IOException {
        if (relationInfo != null)
            relationInfo.close();
        forwardIndex.close();
        delegateFieldsProducer.close();
    }

    @Override
    public BLTerms terms(String field) throws IOException {
        synchronized (termsPerField) {
            BLTerms terms = termsPerField.get(field);
            if (terms == null) {
                Terms delegateTerms = delegateFieldsProducer.terms(field);

                terms = delegateTerms == null ? null : new BLTerms(field, delegateTerms, this);
                termsPerField.put(field, terms);
            }
            return terms;
        }
    }

    @Override
    public int size() {
        return delegateFieldsProducer.size();
    }

    @Override
    public void checkIntegrity() throws IOException {
        delegateFieldsProducer.checkIntegrity();

        // TODO: check integrity of our own (FI) files?
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(delegate=" + delegateFieldsProducer + ")";
    }

    /** Lucene 8 uses big-endian, Lucene 9 little-endian */
    public IndexInput openInputCorrectEndian(Directory directory, String fileName, IOContext ioContext) throws IOException {
        if (reverseEndian) {
            return EndiannessReverserUtil.openInput(directory, fileName, ioContext); // flip for Lucene 9
        } else {
            return directory.openInput(fileName, ioContext);
        }
    }

    /**
     * Open a custom file for reading and check the header.
     *
     * @param extension extension of the file to open (should be one of the prefixed constants from the postings format class)
     * @return handle to the opened segment file
     */
    public IndexInput openIndexFile(String extension) throws IOException {
        String fileName = IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, extension);
        IndexInput input = openInputCorrectEndian(state.directory, fileName, state.context);
        try {
            // Check index header
            CodecUtil.checkIndexHeader(input, postingsFormatName, minVersion,
                    maxVersion, state.segmentInfo.getId(), state.segmentSuffix);

            // Check delegate format name
            String delegateFN = input.readString();
            if (delegateFormatName == null)
                delegateFormatName = delegateFN;
            if (!delegateFormatName.equals(delegateFN))
                throw new IOException("Segment file " + fileName +
                        " contains wrong delegate format name: " + delegateFN +
                        " (expected " + delegateFormatName + ")");

            return input;
        } catch (Exception e) {
            input.close();
            throw e;
        }
    }
}
