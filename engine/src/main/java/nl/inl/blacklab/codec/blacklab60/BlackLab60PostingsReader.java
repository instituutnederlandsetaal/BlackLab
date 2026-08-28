package nl.inl.blacklab.codec.blacklab60;

import java.io.IOException;

import org.apache.lucene.index.SegmentReadState;

import net.jcip.annotations.ThreadSafe;
import nl.inl.blacklab.codec.BlackLabPostingsReader;

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
public class BlackLab60PostingsReader extends BlackLabPostingsReader {

    public BlackLab60PostingsReader(SegmentReadState state) throws IOException {
        super(BlackLab60PostingsFormat.NAME, BlackLab60PostingsFormat.VERSION_START,
                BlackLab60PostingsFormat.VERSION_CURRENT, state, false);
    }

}
