package nl.inl.blacklab.codec;

import java.io.IOException;

import org.apache.lucene.codecs.StoredFieldsWriter;
import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;

/**
 * Stores values as a content store, to enable random access.
 * Delegates non-content-store writes to the default implementation.
 */
public class BlackLab40StoredFieldsWriter extends BlackLabStoredFieldsWriter {

    public BlackLab40StoredFieldsWriter(Directory directory, SegmentInfo segmentInfo, IOContext ioContext,
            StoredFieldsWriter delegate, String delegateFormatName)
            throws IOException {
        super(BlackLab40StoredFieldsFormat.NAME, BlackLab40StoredFieldsFormat.VERSION_START,
                BlackLab40StoredFieldsFormat.VERSION_CURRENT, directory, segmentInfo, ioContext,
                delegate, delegateFormatName, false);

        // NOTE: we can make this configurable later (to optimize for specific usage scenarios),
        // but for now we'll just use the default value.
        fieldsFile.writeInt(blockSizeChars);

    }

}
