package nl.inl.blacklab.codec;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.codecs.FieldsProducer;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.Terms;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.Accountables;

import net.jcip.annotations.ThreadSafe;
import nl.inl.blacklab.forwardindex.ForwardIndexSegmentReader;
import nl.inl.blacklab.forwardindex.RelationInfoSegmentReader;

/**
 * Adds forward index reading to default FieldsProducer.
 *
 * Each index segment has an instance of BLFieldsProducer.
 * It opens the custom segment files for the forward index
 * (and any other custom files).
 *
 * Delegates all other methods to the default FieldsProducer.
 *
 * Adapted from <a href="https://github.com/meertensinstituut/mtas/">MTAS</a>.
 *
 * Thread-safe. It does store IndexInput which contains state, but those
 * are cloned whenever a thread needs to use them.
 */
@ThreadSafe
public class BlackLab40PostingsReader extends BlackLabPostingsReader {

    protected static final Logger logger = LogManager.getLogger(BlackLab40PostingsReader.class);

    private final SegmentReadState state;

    /** Name of PF we delegate to (the one from Lucene) */
    private String delegateFormatName;

    /** The delegate whose functionality we're extending */
    private final FieldsProducer delegateFieldsProducer;

    /** The forward index */
    private final SegmentForwardIndex forwardIndex;

    /** The relation info (if it was stored) */
    private final SegmentRelationInfo relationInfo;

    /** Terms object for each field */
    private final Map<String, BLTerms> termsPerField = new HashMap<>();

    public BlackLab40PostingsReader(SegmentReadState state) throws IOException {
        this.state = state;

        // NOTE: opening the forward index calls openInputFile, which reads
        //       delegatePostingsFormatName, so this must be done first.
        forwardIndex = new SegmentForwardIndex(this);
        if (delegateFormatName == null)
            throw new IllegalStateException("Opening the segment FI should have set the delegate format name");
        PostingsFormat delegatePostingsFormat = PostingsFormat.forName(delegateFormatName);
        delegateFieldsProducer = delegatePostingsFormat.fieldsProducer(state);

        // If relation info was stored, make sure we can access it
        relationInfo = SegmentRelationInfo.openIfPresent(this);
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
        BLTerms terms;
        synchronized (termsPerField) {
            terms = termsPerField.get(field);
            if (terms == null) {
                Terms delegateTerms = delegateFieldsProducer.terms(field);

                terms = delegateTerms == null ? null : new BLTerms(delegateTerms, this);
                termsPerField.put(field, terms);
            }
        }
        return terms;
    }

    @Override
    public BlackLabStoredFieldsReader getStoredFieldsReader() {
        try {
            BlackLab40Codec codec = (BlackLab40Codec) state.segmentInfo.getCodec();
            return codec.storedFieldsFormat().fieldsReader(
                    state.directory, state.segmentInfo, state.fieldInfos, state.context);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int size() {
        return delegateFieldsProducer.size();
    }

    @Override
    public long ramBytesUsed() {
        return 3 * delegateFieldsProducer.ramBytesUsed();
    }

    @Override
    public Collection<Accountable> getChildResources() {
        return List.of(Accountables.namedAccountable("delegate", delegateFieldsProducer));
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
        return directory.openInput(fileName, ioContext);
    }

    /**
     * Open a custom file for reading and check the header.
     *
     * @param extension extension of the file to open (should be one of the prefixed constants from Blacklab40PostingsFormat)
     * @return handle to the opened segment file
     */
    public IndexInput openIndexFile(String extension) throws IOException {
        String fileName = IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, extension);
        IndexInput input = openInputCorrectEndian(state.directory, fileName, state.context);
        try {
            // Check index header
            CodecUtil.checkIndexHeader(input, BlackLab40PostingsFormat.NAME, BlackLab40PostingsFormat.VERSION_START,
                    BlackLab40PostingsFormat.VERSION_CURRENT, state.segmentInfo.getId(), state.segmentSuffix);

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

    public RelationInfoSegmentReader relationInfo() {
        return relationInfo.reader();
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

//    /**
//     * Get the BlackLab40PostingsReader for the given leafreader.
//     *
//     * @param lrc leafreader to get the BlackLab40PostingsReader for
//     * @return BlackLab40PostingsReader for this leafreader
//     */
//    public static BlackLab40PostingsReader get(LeafReaderContext lrc) {
//        return (BlackLab40PostingsReader) BLTerms.getTerms(lrc).getFieldsProducer();
//    }

}
