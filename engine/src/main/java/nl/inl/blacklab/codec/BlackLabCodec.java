package nl.inl.blacklab.codec;

import java.lang.reflect.Constructor;

import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.lucene87.Lucene87Codec;

public abstract class BlackLabCodec extends Codec {

    final Constructor<? extends BlackLabPostingsFormat> ctorPf;

    String fallBackPostingsFormatName;

    Class<? extends Codec> defaultLuceneCodec = Lucene87Codec.class;

    protected BlackLabCodec(String name, Class<? extends Codec> defaultLuceneCodec, String fallBackPostingsFormatName,
            Class<? extends BlackLabPostingsFormat> postingsFormatClass) {
        super(name);
        this.defaultLuceneCodec = defaultLuceneCodec;
        this.fallBackPostingsFormatName = fallBackPostingsFormatName;
        try {
            ctorPf = postingsFormatClass.getDeclaredConstructor(PostingsFormat.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public abstract BlackLabStoredFieldsFormat storedFieldsFormat();
}
