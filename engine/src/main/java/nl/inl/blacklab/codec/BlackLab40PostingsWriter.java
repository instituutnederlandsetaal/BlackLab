package nl.inl.blacklab.codec;

import org.apache.lucene.codecs.FieldsConsumer;
import org.apache.lucene.index.SegmentWriteState;

/**
 * BlackLab FieldsConsumer: writes postings information to the index,
 * using a delegate and extending its functionality by also writing a forward
 * index.
 *
 * Adapted from <a href="https://github.com/meertensinstituut/mtas/">MTAS</a>.
 */
public class BlackLab40PostingsWriter extends BlackLabPostingsWriter {

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
        super(BlackLab40PostingsFormat.NAME, BlackLab40PostingsFormat.VERSION_START, BlackLab40PostingsFormat.VERSION_CURRENT,
                delegateFieldsConsumer, state, delegatePostingsFormatName, true);
    }

}
