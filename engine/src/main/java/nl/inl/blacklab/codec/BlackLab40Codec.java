package nl.inl.blacklab.codec;

import java.lang.reflect.InvocationTargetException;

import org.apache.lucene.backward_codecs.lucene87.Lucene87Codec;
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
import org.apache.lucene.codecs.perfield.PerFieldPostingsFormat;

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
 * that returns the postings format object responsible for
 * saving/loading postings data (the actual inverted index, with
 * frequencies, offsets, payloads, etc.).
 *
 * This is referenced in Solr schema, e.g.:
 * <pre>
 * &lt;fieldType name="blacklab_text_example_test" class="solr.TextField" postingsFormat="BlackLab40"&gt;
 * </pre>
 *
 * This class is declared in META-INF/services/org.apache.lucene.codecs.Codec
 *
 * Adapted from <a href="https://github.com/meertensinstituut/mtas/">MTAS</a>.
 */
public class BlackLab40Codec extends BlackLabCodec {

    /** Our codec's name. */
    static final String NAME = "BlackLab40";

    /** The codec we're basing this codec on. */
    private Codec _delegate;

    /** Our postings format, that takes care of the forward index as well. */
    private BlackLabPostingsFormat postingsFormat;

    /** Our stored fields format, that takes care of the content stores as well. */
    private BlackLabStoredFieldsFormat storedFieldsFormat;

    public BlackLab40Codec() {
        super(NAME, Lucene87Codec.class, "Lucene87", BlackLab40PostingsFormat.class);
    }

    private synchronized Codec delegate() {
        if (_delegate == null) {
            // We defer initialization to prevent an error about getting the default codec before all codecs
            // are initialized.
            _delegate = new Lucene87Codec(); //Codec.getDefault();
        }
        return _delegate;
    }

    /**
     * Determine the right wrapped postings format to use.
     *
     * @return our postingsformat
     */
    private BlackLabPostingsFormat determinePostingsFormat() {
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
                        return new BlackLabXXPostingsFormat(delegatePF.getPostingsFormatForField(field));
                    });
                }
            };
        } else {
            // Simple delegate, not per-field.
            return new BlackLabXXPostingsFormat(delegate().postingsFormat());
        }*/

        try {
            PostingsFormat delegatePf;
            if (delegate().postingsFormat() instanceof PerFieldPostingsFormat) {
                Codec defaultCodec = defaultLuceneCodec.getDeclaredConstructor().newInstance();
                PostingsFormat defaultPostingsFormat = defaultCodec.postingsFormat();
                if (defaultPostingsFormat instanceof PerFieldPostingsFormat) {
                    defaultPostingsFormat = ((PerFieldPostingsFormat) defaultPostingsFormat)
                            .getPostingsFormatForField("");
                    if ((defaultPostingsFormat == null)
                            || (defaultPostingsFormat instanceof PerFieldPostingsFormat)) {
                        // fallback option
                        defaultPostingsFormat = PostingsFormat.forName(fallBackPostingsFormatName);
                    }
                }
                delegatePf = defaultPostingsFormat;
            } else {
                delegatePf = delegate().postingsFormat();
            }
            return ctorPf.newInstance(delegatePf);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                 NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized BlackLabPostingsFormat postingsFormat() {
        if (postingsFormat == null)
            postingsFormat = determinePostingsFormat();
        return postingsFormat;
    }

    @Override
    public DocValuesFormat docValuesFormat() {
        return delegate().docValuesFormat();
    }

    @Override
    public synchronized BlackLabStoredFieldsFormat storedFieldsFormat() {
        if (storedFieldsFormat == null)
            storedFieldsFormat = new BlackLab40StoredFieldsFormat(delegate().storedFieldsFormat());
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
