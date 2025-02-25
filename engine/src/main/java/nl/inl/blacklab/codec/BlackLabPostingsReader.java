package nl.inl.blacklab.codec;

import java.io.IOException;

import org.apache.lucene.codecs.FieldsProducer;
import org.apache.lucene.store.IndexInput;

import nl.inl.blacklab.forwardindex.ForwardIndexSegmentReader;
import nl.inl.blacklab.forwardindex.RelationInfoSegmentReader;

public abstract class BlackLabPostingsReader extends FieldsProducer {
    public abstract BlackLabStoredFieldsReader getStoredFieldsReader();

    public abstract ForwardIndexSegmentReader forwardIndex();

    public abstract RelationInfoSegmentReader relationInfo();

    @Override
    public abstract BLTerms terms(String s) throws IOException;

    public abstract IndexInput openIndexFile(String extension) throws IOException;
}
