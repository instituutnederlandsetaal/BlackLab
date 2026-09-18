package nl.inl.blacklab.search.indexmetadata;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.Arrays;

import org.apache.lucene.util.BytesRef;
import org.eclipse.collections.api.factory.primitive.IntLists;
import org.junit.Test;

import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.index.BLInputDocument;
import nl.inl.blacklab.index.BLInputDocumentLucene;

public class TestSourceUnitCodec {

    @Test
    public void testFixedWidthRoundTripAndBinaryStorage() {
        byte[] encoded = SourceUnitCodec.encode(IntLists.mutable.of(
                100, 120, 2,
                10, 30, 5));
        assertEquals(24, encoded.length);
        assertEquals(2, SourceUnitCodec.unitCount(new BytesRef(encoded)));
        assertEquals(100, SourceUnitCodec.value(new BytesRef(encoded), 0, 0));
        assertEquals(10, SourceUnitCodec.value(new BytesRef(encoded), 1, 0));
        assertArrayEquals(new byte[] { 0, 0, 0, 100, 0, 0, 0, 120 }, Arrays.copyOf(encoded, 8));

        BLInputDocumentLucene document = new BLInputDocumentLucene(BLInputDocument.DocType.DOCUMENT);
        document.addStoredField("contents#source_units", encoded);
        BytesRef stored = document.getDocument().getField("contents#source_units").binaryValue();
        assertArrayEquals(encoded, Arrays.copyOfRange(stored.bytes, stored.offset, stored.offset + stored.length));
    }

    @Test
    public void testRejectsTruncatedRecord() {
        assertThrows(InvalidIndex.class, () -> SourceUnitCodec.unitCount(new BytesRef(new byte[11])));
        assertThrows(InvalidIndex.class, () -> SourceUnitCodec.value(new BytesRef(new byte[11]), 0, 2));
        assertThrows(InvalidIndex.class, () -> SourceUnitCodec.value(new BytesRef(new byte[12]), 1, 0));
        assertThrows(InvalidIndex.class, () -> SourceUnitCodec.value(new BytesRef(new byte[12]), 0, 3));
        assertThrows(IllegalArgumentException.class,
                () -> SourceUnitCodec.encode(IntLists.mutable.of(1, 2)));
    }
}
