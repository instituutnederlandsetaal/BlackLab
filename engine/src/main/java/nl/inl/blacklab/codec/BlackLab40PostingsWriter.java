package nl.inl.blacklab.codec;

import java.io.IOException;

import org.apache.lucene.codecs.FieldsConsumer;
import org.apache.lucene.index.SegmentWriteState;

import nl.inl.blacklab.exceptions.IndexVersionMismatch;
import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.search.indexmetadata.RelationsStrategy;
import nl.inl.blacklab.search.indexmetadata.RelationsStrategySeparateTerms;
import nl.inl.blacklab.search.indexmetadata.RelationsStrategySingleTerm;

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
                delegateFieldsConsumer, state, delegatePostingsFormatName, false);
        RelationsStrategy relationsStrategy = RelationsStrategy.forNewIndex();

        try {
            plugins.add(new PWPluginForwardIndex(this));
            if (relationsStrategy.writeRelationInfoToIndex()) {
                if (relationsStrategy instanceof RelationsStrategySingleTerm) {
                    throw new IndexVersionMismatch("This index uses a tags/relations format that was temporarily used in development, but is not supported anymore. Please re-index.");
                } else if (relationsStrategy instanceof RelationsStrategySeparateTerms) {
                    // This is the current version of the relation info plugin, used for new indexes.
                    plugins.add(new PWPluginRelationInfo(this, (RelationsStrategySeparateTerms) relationsStrategy));
                } else {
                    throw new IndexVersionMismatch("Unknown relationsStrategy: " + relationsStrategy.getName());
                }
            }
        } catch (IOException e) {
            // Something went wrong, e.g. we couldn't create the output files.
            throw new InvalidIndex("Error initializing PostingsWriter plugins", e);
        }
    }

}
