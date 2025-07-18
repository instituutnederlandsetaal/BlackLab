package nl.inl.blacklab.codec;

import java.io.IOException;

import org.apache.lucene.index.SegmentReadState;

import net.jcip.annotations.ThreadSafe;

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

    public BlackLab40PostingsReader(SegmentReadState state) throws IOException {
        super(BlackLab40PostingsFormat.NAME, BlackLab40PostingsFormat.VERSION_START,
                BlackLab40PostingsFormat.VERSION_CURRENT, state, false);
    }

}
