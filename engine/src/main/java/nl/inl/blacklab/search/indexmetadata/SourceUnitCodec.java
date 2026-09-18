package nl.inl.blacklab.search.indexmetadata;

import org.apache.lucene.util.BytesRef;
import org.eclipse.collections.api.list.primitive.MutableIntList;

import nl.inl.blacklab.exceptions.InvalidIndex;

/** Fixed-width source-start, source-end, exclusive-token-end records for XML retrieval containers. */
public final class SourceUnitCodec {

    public static final int VALUES_PER_UNIT = 3;

    public static final int BYTES_PER_UNIT = VALUES_PER_UNIT * Integer.BYTES;

    public static byte[] encode(MutableIntList values) {
        if (values.size() % VALUES_PER_UNIT != 0)
            throw new IllegalArgumentException("Incomplete source-unit record");
        byte[] result = new byte[values.size() * Integer.BYTES];
        for (int i = 0, offset = 0; i < values.size(); i++, offset += Integer.BYTES) {
            int value = values.get(i);
            result[offset] = (byte) (value >>> 24);
            result[offset + 1] = (byte) (value >>> 16);
            result[offset + 2] = (byte) (value >>> 8);
            result[offset + 3] = (byte) value;
        }
        return result;
    }

    public static int unitCount(BytesRef data) {
        if (data.length % BYTES_PER_UNIT != 0)
            throw new InvalidIndex("Invalid source-unit data length: " + data.length);
        return data.length / BYTES_PER_UNIT;
    }

    public static int value(BytesRef data, int unit, int component) {
        int units = unitCount(data);
        if (unit < 0 || unit >= units || component < 0 || component >= VALUES_PER_UNIT)
            throw new InvalidIndex("Invalid source-unit coordinate: " + unit + ", " + component);
        int offset = data.offset + (unit * VALUES_PER_UNIT + component) * Integer.BYTES;
        return (data.bytes[offset] & 0xff) << 24 |
                (data.bytes[offset + 1] & 0xff) << 16 |
                (data.bytes[offset + 2] & 0xff) << 8 |
                data.bytes[offset + 3] & 0xff;
    }

    private SourceUnitCodec() {
    }
}
