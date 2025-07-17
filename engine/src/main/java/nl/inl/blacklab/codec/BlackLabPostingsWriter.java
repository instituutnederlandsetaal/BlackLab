package nl.inl.blacklab.codec;

import org.apache.lucene.codecs.FieldsConsumer;

public abstract class BlackLabPostingsWriter extends FieldsConsumer {
    protected abstract int maxDoc();

    public abstract String getSegmentName();
}
