package nl.inl.blacklab.codec;

import java.io.IOException;

import org.apache.lucene.codecs.StoredFieldsReader;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;

import nl.inl.blacklab.exceptions.InvalidIndex;

/**
 * Provides random access to values stored as a content store.
 * Delegates non-content-store reads to the default implementation.
 */
public class BlackLab40StoredFieldsReader extends BlackLabStoredFieldsReader {

    public BlackLab40StoredFieldsReader(Directory directory, SegmentInfo segmentInfo, IOContext ioContext, FieldInfos fieldInfos,
            StoredFieldsReader delegate, String delegateFormatName)
            throws IOException {
        super(BlackLab40StoredFieldsFormat.NAME, BlackLab40StoredFieldsFormat.VERSION_START,
                BlackLab40StoredFieldsFormat.VERSION_CURRENT, directory, segmentInfo, ioContext, fieldInfos,
                delegate, delegateFormatName, true);
    }

    @Override
    public StoredFieldsReader clone() {
        try {
            return new BlackLab40StoredFieldsReader(directory, segmentInfo, ioContext, fieldInfos,
                    delegate.clone(), delegateFormatName);
        } catch (IOException e) {
            throw new InvalidIndex(e);
        }
    }

    @Override
    public StoredFieldsReader getMergeInstance() {

        // For now we don't have a merging-optimized version of this class,
        // but maybe in the future.

        StoredFieldsReader mergeInstance = delegate.getMergeInstance();
        if (mergeInstance != delegate) {
            // The delegate has a specific merge instance (i.e. didn't return itself).
            // Create a new instance with the new delegate and return that.
            try {
                return new BlackLab40StoredFieldsReader(directory, segmentInfo, ioContext, fieldInfos,
                        mergeInstance, delegateFormatName);
            } catch (IOException e) {
                throw new InvalidIndex(e);
            }
        }
        return this;
    }

}
