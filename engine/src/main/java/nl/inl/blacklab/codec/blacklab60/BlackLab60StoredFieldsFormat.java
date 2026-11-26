package nl.inl.blacklab.codec.blacklab60;

import java.io.IOException;

import org.apache.lucene.codecs.StoredFieldsFormat;
import org.apache.lucene.codecs.StoredFieldsReader;
import org.apache.lucene.codecs.StoredFieldsWriter;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;

import nl.inl.blacklab.codec.BlackLabStoredFieldsFormat;
import nl.inl.blacklab.codec.BlackLabStoredFieldsReader;

/**
 * Stores certain fields as a content store, enabling random access to the stored values.
 *
 * Delegates non-content-store writes and reads to the default implementation.
 */
public class BlackLab60StoredFieldsFormat extends BlackLabStoredFieldsFormat {

    /** Name of this codec. Written to the files and checked on reading. */
    public static final String NAME = "BlackLab60ContentStore";

    /** Oldest version still supported */
    public static final int VERSION_START = 1;

    /** Current version */
    public static final int VERSION_CURRENT = 1;

    /** Standard Lucene StoredFieldsFormat we delegate to for regular (non-content-store) stored fields. */
    private final StoredFieldsFormat delegate;

    public BlackLab60StoredFieldsFormat(StoredFieldsFormat delegate) {
        this.delegate = delegate;
    }

    @Override
    public BlackLabStoredFieldsReader fieldsReader(Directory directory, SegmentInfo segmentInfo,
            FieldInfos fieldInfos, IOContext ioContext) throws IOException {
        StoredFieldsReader delegateReader = delegate.fieldsReader(directory, segmentInfo, fieldInfos, ioContext);
        String delegateFormatName = delegate.getClass().getSimpleName();
        return new BlackLab60StoredFieldsReader(directory, segmentInfo, ioContext, fieldInfos, delegateReader,
                delegateFormatName);
    }

    @Override
    public BlackLab60StoredFieldsWriter fieldsWriter(Directory directory, SegmentInfo segmentInfo, IOContext ioContext)
            throws IOException {
        StoredFieldsWriter delegateWriter = delegate.fieldsWriter(directory, segmentInfo, ioContext);
        String delegateFormatName = delegate.getClass().getSimpleName();
        return new BlackLab60StoredFieldsWriter(directory, segmentInfo, ioContext, delegateWriter, delegateFormatName);
    }
}
