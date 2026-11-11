package nl.inl.blacklab.codec.blacklab60;

import java.io.IOException;

import org.apache.lucene.codecs.StoredFieldsWriter;
import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;

import nl.inl.blacklab.codec.BlackLabStoredFieldsWriter;

/**
 * Stores values as a content store, to enable random access.
 * Delegates non-content-store writes to the default implementation.
 */
public class BlackLab60StoredFieldsWriter extends BlackLabStoredFieldsWriter {

    public BlackLab60StoredFieldsWriter(Directory directory, SegmentInfo segmentInfo, IOContext ioContext,
            StoredFieldsWriter delegate, String delegateFormatName)
            throws IOException {
        super(BlackLab60StoredFieldsFormat.NAME, BlackLab60StoredFieldsFormat.VERSION_START,
                BlackLab60StoredFieldsFormat.VERSION_CURRENT, directory, segmentInfo, ioContext,
                delegate, delegateFormatName, false);
    }

}
