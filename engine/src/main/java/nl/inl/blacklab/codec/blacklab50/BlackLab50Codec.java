package nl.inl.blacklab.codec.blacklab50;

import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.CompoundFormat;
import org.apache.lucene.codecs.DocValuesFormat;
import org.apache.lucene.codecs.FieldInfosFormat;
import org.apache.lucene.codecs.KnnVectorsFormat;
import org.apache.lucene.codecs.LiveDocsFormat;
import org.apache.lucene.codecs.NormsFormat;
import org.apache.lucene.codecs.PointsFormat;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.SegmentInfoFormat;
import org.apache.lucene.codecs.TermVectorsFormat;
import org.apache.lucene.codecs.lucene99.Lucene99Codec;
import org.apache.lucene.codecs.perfield.PerFieldPostingsFormat;

import nl.inl.blacklab.codec.BlackLabCodec;

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
 * that returns the {@link BlackLab50PostingsFormat} object responsible for
 * saving/loading postings data (the actual inverted index, with
 * frequencies, offsets, payloads, etc.).
 *
 * This is referenced in Solr schema, e.g.:
 * <pre>
 * &lt;fieldType name="blacklab_text_example_test" class="solr.TextField" postingsFormat="BlackLab50"&gt;
 * </pre>
 *
 * This class is declared in META-INF/services/org.apache.lucene.codecs.Codec
 *
 * Adapted from <a href="https://github.com/meertensinstituut/mtas/">MTAS</a>.
 */
public class BlackLab50Codec extends BlackLabCodec {

    /** Our codec's name. */
    static final String NAME = "BlackLab50";

    /** The codec we're basing this codec on. */
    private Codec _delegate;

    /** Our postings format, that takes care of the forward index as well. */
    private BlackLab50PostingsFormat postingsFormat;

    /** Our stored fields format, that takes care of the content stores as well. */
    private BlackLab50StoredFieldsFormat storedFieldsFormat;

    public BlackLab50Codec() {
        super(NAME);
    }

    private synchronized Codec delegate() {
        if (_delegate == null) {
            // We defer initialization to prevent an error about getting the default codec before all codecs
            // are initialized.
            _delegate = new Lucene99Codec();
        }
        return _delegate;
    }

    /**
     * Determine the right wrapped postings format to use.
     *
     * @return our postingsformat
     */
    private BlackLab50PostingsFormat determinePostingsFormat() {
        /*

        // This causes errors. We cannot handle a per-field postings format properly yet.
        // Fortunately, Lucene by default just uses the same postings format for each field.
        // Maybe look into this later.

        if (delegate().postingsFormat() instanceof PerFieldPostingsFormat) {
            // Each field can potentially get its own postingsFormat.
            // Keep track of each one and wrap BLCodecPostingsFormat around it.
            return new PerFieldPostingsFormat() {
                Map<String, PostingsFormat> postingFormatPerField = new ConcurrentHashMap<>();

                @Override
                public PostingsFormat getPostingsFormatForField(String field) {
                    return postingFormatPerField.computeIfAbsent(field, f -> {
                        PerFieldPostingsFormat delegatePF = ((PerFieldPostingsFormat) delegate().postingsFormat());
                        // this is probably why this doesn't work: we shouldn't instantiate independent postings formats
                        // for each field, because those will try to write the same files to the index directory.
                        // Instead there should be one class that handles all the read/writes with some per-field logic.
                        return new BlackLab50PostingsFormat(delegatePF.getPostingsFormatForField(field));
                    });
                }
            };
        } else {
            // Simple delegate, not per-field.
            return new BlackLab50PostingsFormat(delegate().postingsFormat());
        }*/

        if (delegate().postingsFormat() instanceof PerFieldPostingsFormat) {
            Codec defaultCodec = new Lucene99Codec();
            PostingsFormat defaultPostingsFormat = defaultCodec.postingsFormat();
            if (defaultPostingsFormat instanceof PerFieldPostingsFormat) {
                defaultPostingsFormat = ((PerFieldPostingsFormat) defaultPostingsFormat)
                        .getPostingsFormatForField("");
                if ((defaultPostingsFormat == null)
                        || (defaultPostingsFormat instanceof PerFieldPostingsFormat)) {
                    // fallback option
                    defaultPostingsFormat = PostingsFormat.forName("Lucene99");
                }
            }
            return new BlackLab50PostingsFormat(defaultPostingsFormat);
        }
        return new BlackLab50PostingsFormat(delegate().postingsFormat());
    }

    @Override
    public synchronized BlackLab50PostingsFormat postingsFormat() {
        if (postingsFormat == null)
            postingsFormat = determinePostingsFormat();
        return postingsFormat;
    }

    @Override
    public DocValuesFormat docValuesFormat() {
        return delegate().docValuesFormat();
    }

    @Override
    public synchronized BlackLab50StoredFieldsFormat storedFieldsFormat() {
        if (storedFieldsFormat == null)
            storedFieldsFormat = new BlackLab50StoredFieldsFormat(delegate().storedFieldsFormat());
        return storedFieldsFormat;
    }

    @Override
    public TermVectorsFormat termVectorsFormat() {
        return delegate().termVectorsFormat();
    }

    @Override
    public FieldInfosFormat fieldInfosFormat() {
        return delegate().fieldInfosFormat();
    }

    @Override
    public SegmentInfoFormat segmentInfoFormat() {
        return delegate().segmentInfoFormat();
    }

    @Override
    public NormsFormat normsFormat() {
        return delegate().normsFormat();
    }

    @Override
    public LiveDocsFormat liveDocsFormat() {
        return delegate().liveDocsFormat();
    }

    @Override
    public CompoundFormat compoundFormat() {
        return delegate().compoundFormat();
    }

    @Override
    public PointsFormat pointsFormat() {
        return delegate().pointsFormat();
    }

    @Override
    public KnnVectorsFormat knnVectorsFormat() {
        return delegate().knnVectorsFormat();
    }
}
