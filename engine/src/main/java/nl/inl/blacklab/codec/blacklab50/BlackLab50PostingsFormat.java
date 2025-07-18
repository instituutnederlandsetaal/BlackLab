package nl.inl.blacklab.codec.blacklab50;

import java.io.IOException;

import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FieldsConsumer;
import org.apache.lucene.codecs.FieldsProducer;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.SegmentWriteState;

import nl.inl.blacklab.codec.BlackLabPostingsFormat;

/**
 * The custom postings format that BlackLab uses.
 *
 * This is an extension of Lucene's "postings" information
 * (e.g. term frequence, positions, offsets, etc.) in the index, to accomodate our forward
 * index and (optional) content store.

 * A {@link PostingsFormat} is the object responsible for
 * saving/loading postings data (the actual inverted index, with
 * frequencies, offsets, payloads, etc.).
 *
 * This functions as an adapter that wraps a delegate
 * PostingsFormat (usually the default Solr PostingsFormat)
 * which is used to create adapted versions of e.g. FieldsConsumer.
 *
 * This class is declared in META-INF/services/org.apache.lucene.codecs.PostingsFormat
 *
 * Adapted from <a href="https://github.com/meertensinstituut/mtas/">MTAS</a>.
 */
public class BlackLab50PostingsFormat extends BlackLabPostingsFormat {

    /** Name of this codec. Written to the files and checked on reading. */
    static final String NAME = "BlackLab50Postings";

    /** Oldest version still supported */
    static final int VERSION_START = 1;

    /** Current version */
    static final int VERSION_CURRENT = 1;

    /** The PostingsFormat we're wrapping and we delegate most requests to. */
    private final PostingsFormat delegatePostingsFormat;

    // Used when opening index (see corresponding PostingsReader constructor)
    @SuppressWarnings("unused")
    public BlackLab50PostingsFormat() {
        super(NAME);
        BlackLabPostingsFormat pf = (BlackLabPostingsFormat)(Codec.forName(BlackLab50Codec.NAME)).postingsFormat();
        delegatePostingsFormat = pf.getDelegatePostingsFormat();
    }

    public BlackLab50PostingsFormat(PostingsFormat delegate) {
        super(NAME);
        delegatePostingsFormat = delegate;
    }

    @Override
    public PostingsFormat getDelegatePostingsFormat() {
        return delegatePostingsFormat;
    }

    @Override
    public final FieldsProducer fieldsProducer(SegmentReadState state) throws IOException {
        return new BlackLab50PostingsReader(state);
    }

    @Override
    public final FieldsConsumer fieldsConsumer(SegmentWriteState state) throws IOException {
        return new BlackLab50PostingsWriter(delegatePostingsFormat.fieldsConsumer(state), state,
                delegatePostingsFormat.getName());
    }

}
