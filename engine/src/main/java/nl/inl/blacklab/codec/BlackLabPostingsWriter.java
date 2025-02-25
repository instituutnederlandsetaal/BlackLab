package nl.inl.blacklab.codec;

import java.io.IOException;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.lucene.codecs.FieldsConsumer;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;

public abstract class BlackLabPostingsWriter extends FieldsConsumer {
    protected abstract IndexOutput createOutput(String riFieldsExt) throws IOException;

    protected abstract IndexInput openInput(String termvecTmpExt) throws IOException;

    protected abstract int maxDoc();

    protected abstract void deleteIndexFile(String termvecTmpExt) throws IOException;

    protected abstract String getSegmentName();
}
