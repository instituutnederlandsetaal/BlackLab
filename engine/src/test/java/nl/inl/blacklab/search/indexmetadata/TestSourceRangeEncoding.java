package nl.inl.blacklab.search.indexmetadata;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;

import nl.inl.blacklab.exceptions.InvalidIndex;

public class TestSourceRangeEncoding {

    @Test
    public void testAbsentEncodingMeansLegacyNativeOffsets() {
        IndexMetadata metadata = metadataWithEncoding("");

        assertNull(metadata.sourceRangeEncoding());
        assertFalse(metadata.usesSourceRangeVectors());
    }

    @Test
    public void testRecognizesSupportedEncoding() {
        IndexMetadata metadata = metadataWithEncoding(SourceRangeEncoding.VERSION);

        assertEquals(SourceRangeEncoding.VERSION, metadata.sourceRangeEncoding());
        assertTrue(metadata.usesSourceRangeVectors());
    }

    @Test
    public void testRejectsUnknownEncoding() {
        IndexMetadata metadata = metadataWithEncoding("source-range-vectors-v999");

        assertThrows(InvalidIndex.class, metadata::sourceRangeEncoding);
    }

    @Test
    public void testRejectsExperimentalFiveIntegerUnitEncoding() {
        // The old container layout cannot be interpreted as three-integer records.
        IndexMetadata metadata = metadataWithEncoding("source-range-vectors-v1");
        assertThrows(InvalidIndex.class, metadata::sourceRangeEncoding);
    }

    private static IndexMetadata metadataWithEncoding(String encoding) {
        IndexMetadata metadata = mock(IndexMetadata.class, CALLS_REAL_METHODS);
        when(metadata.indexFlag(SourceRangeEncoding.INDEX_FLAG)).thenReturn(encoding);
        return metadata;
    }
}
