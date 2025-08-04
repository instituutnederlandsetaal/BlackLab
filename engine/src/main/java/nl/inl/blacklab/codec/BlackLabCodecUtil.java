package nl.inl.blacklab.codec;

import org.apache.lucene.index.LeafReaderContext;

public class BlackLabCodecUtil {

    private BlackLabCodecUtil() {
    }

    /**
     * Get the BlackLabStoredFieldsReader for the given leafreader.
     *
     * The luceneField must be any existing Lucene field in the index.
     * It doesn't matter which. This is because BLTerms is used as an
     * intermediate to get access to BlackLab40StoredFieldsReader.
     *
     * The returned BlackLab40StoredFieldsReader is not specific for the specified field,
     * but can be used to read information related to any field from the segment.
     *
     * @param lrc leafreader to get the BLFieldsProducer for
     * @return BlackLab40StoredFieldsReader for this leafreader
     */
    public static BlackLabStoredFieldsReader getStoredFieldsReader(LeafReaderContext lrc) {
        return getPostingsReader(lrc).getStoredFieldsReader();
    }

    /**
     * Get the BlackLabPostingsReader for the given leafreader.
     *
     * @param lrc leafreader to get the BlackLab40PostingsReader for
     * @return BlackLab40PostingsReader for this leafreader
     */
    public static BlackLabPostingsReader getPostingsReader(LeafReaderContext lrc) {
        return BLTerms.getAnyTermsObject(lrc).getFieldsProducer();
    }

}
