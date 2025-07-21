package nl.inl.blacklab.codec.blacklab50;

import org.apache.lucene.codecs.FieldsConsumer;
import org.apache.lucene.index.SegmentWriteState;

import nl.inl.blacklab.codec.BlackLabPostingsWriter;

/**
 * BlackLab FieldsConsumer: writes postings information to the index,
 * using a delegate and extending its functionality by also writing a forward
 * index.
 *
 * Adapted from <a href="https://github.com/meertensinstituut/mtas/">MTAS</a>.
 */
public class BlackLab50PostingsWriter extends BlackLabPostingsWriter {

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
        super(BlackLab50PostingsFormat.NAME, BlackLab50PostingsFormat.VERSION_START, BlackLab50PostingsFormat.VERSION_CURRENT,
                delegateFieldsConsumer, state, delegatePostingsFormatName, false);
    }

}
