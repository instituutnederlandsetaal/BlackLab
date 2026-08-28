package nl.inl.blacklab.codec.blacklab60;

import org.apache.lucene.codecs.lucene104.Lucene104Codec;

import nl.inl.blacklab.codec.BlackLabCodec;
import nl.inl.blacklab.codec.BlackLabPostingsFormat;
import nl.inl.blacklab.codec.BlackLabStoredFieldsFormat;

/**
 * The custom codec that BlackLab uses.
 *
 * This is a customization of Lucene's way of storing information in the index,
 * to accomodate our forward index and (optional) content store.
 *
 * This functions as an adapter that wraps a delegate
 * codec (usually the default Solr codec) and simply proxies
 * most requests to that codec. It will handle specific requests
 * itself, though, in this case the {@link #postingsFormat()} method
 * that returns the {@link BlackLab60PostingsFormat} object responsible for
 * saving/loading postings data (the actual inverted index, with
 * frequencies, offsets, payloads, etc.).
 *
 * This is referenced in Solr schema, e.g.:
 * <pre>
 * &lt;fieldType name="blacklab_text_example_test" class="solr.TextField" postingsFormat="BlackLab60"&gt;
 * </pre>
 *
 * This class is declared in META-INF/services/org.apache.lucene.codecs.Codec
 *
 * Adapted from <a href="https://github.com/meertensinstituut/mtas/">MTAS</a>.
 */
public class BlackLab60Codec extends BlackLabCodec {

    /** Our codec's name. */
    static final String NAME = "BlackLab60";

    /** Our postings format, that takes care of the forward index as well. */
    private BlackLabPostingsFormat postingsFormat;

    /** Our stored fields format, that takes care of the content stores as well. */
    private BlackLabStoredFieldsFormat storedFieldsFormat;

    public BlackLab60Codec() {
        super(NAME, Lucene104Codec.class, "Lucene104");
    }

    @Override
    public synchronized BlackLabPostingsFormat postingsFormat() {
        if (postingsFormat == null)
            postingsFormat = new BlackLab60PostingsFormat(getDelegatePostingsFormat());
        return postingsFormat;
    }

    @Override
    public synchronized BlackLabStoredFieldsFormat storedFieldsFormat() {
        if (storedFieldsFormat == null)
            storedFieldsFormat = new BlackLab60StoredFieldsFormat(getDelegateStoredFieldsFormat());
        return storedFieldsFormat;
    }

}
