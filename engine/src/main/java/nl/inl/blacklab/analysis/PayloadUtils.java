package nl.inl.blacklab.analysis;

import org.apache.lucene.store.ByteArrayDataInput;
import org.apache.lucene.util.BytesRef;

/**
 * Utilities for dealing with payloads in BlackLab.
 *
 * Specifically:
 * <ul>
 * <li>a payload is stored in the "_relation" (previously "starttag") annotation to indicate the end position
 * of a span</li>
 * <li>while indexing, payloads are used to distinguish between "primary values" (that are
 * recorded in the forward index) and "secondary values" (that are not). These indicators will stripped
 * before writing to the Lucene index, however, leaving the original payload.</li>
 * </ul>
 */
public class PayloadUtils {

    /** Utility class, don't instantiate */
    private PayloadUtils() {}

    // Annotations to be stored in a forward index sometimes start with a byte indicating whether a value is primary
    // or secondary. Primary is the default if there is no indicator or no payload.

    /** First payload byte for primary token values (original value, to be used for concordances) */
    static final byte BYTE_PRIMARY = 127;

    /** First payload byte for secondary token values (e.g. synonym, lowercased, stemmed, etc.) */
    static final byte BYTE_SECONDARY = 126;

    /** BytesRef to a single {@link #BYTE_SECONDARY} */
    private static final BytesRef BYTES_REF_SECONDARY = new BytesRef(new byte[] { BYTE_SECONDARY });

    /**
     * Make sure this payload indicates whether or not this is a primary token value.
     *
     * @param isPrimary whether this is a primary value or not
     * @param payload value to prepend to (may be null if there was no paylaod)
     * @return new payload value (may be null if there was no payload and this is a primary value)
     */
    public static BytesRef addIsPrimary(boolean isPrimary, BytesRef payload) {
        if (payload == null || payload.length == 0) {
            // There is no payload.
            if (isPrimary) {
                // Primary is the default, so no payload is fine.
                return null;
            } else {
                // Payload only indicates that it is secondary.
                return BYTES_REF_SECONDARY;
            }
        } else {
            // There's an existing payload.
            byte firstByte = payload.bytes[payload.offset];
            if (isPrimary && (firstByte != BYTE_PRIMARY && firstByte != BYTE_SECONDARY)) {
                // This is a primary value (the default), and the original payload does not start with
                // one of our indicator bytes. So the existing payload is fine as-is (no indicator
                // always means it is a primary value).
                return payload;
            } else {
                // This is a secondary value, and/or the original payload starts with one of our indicator bytes.
                // We need to prepend an indicator byte that specifies whether this is a primary or secondary value.
                byte[] newPayload = new byte[payload.length + 1];
                newPayload[0] = isPrimary ? BYTE_PRIMARY : BYTE_SECONDARY;
                System.arraycopy(payload.bytes, payload.offset, newPayload, 1, payload.length);
                return new BytesRef(newPayload);
            }
        }
    }

    /**
     * Check if payload indicates a primary value or not, and whether or not bytes were prepended.
     *
     * Note that not all annotations store the is-primary status in the payload (e.g. an annotation without a forward
     * index probably won't); only call this method for an annotation that stores this.
     *
     * Used to distinguish between primary (for concordances) and secondary token values
     * (e.g. synonyms, lowercased, stemmed, etc.).
     *
     * @param payload payload to check (or null if there is no payload)
     * @return whether this is a primary value or not
     */
    public static boolean isPrimaryValue(BytesRef payload) {
        return payload == null || payload.length == 0 || payload.bytes[payload.offset] != BYTE_SECONDARY;
    }

    /**
     * Check if payload indicates a primary value or not, and whether or not bytes were prepended.
     *
     * Note that not all annotations store the is-primary status in the payload (e.g. an annotation without a forward
     * index probably won't); only call this method for an annotation that stores this.
     *
     * Used to distinguish between primary (for concordances) and secondary token values
     * (e.g. synonyms, lowercased, stemmed, etc.).
     *
     * @param payload payload to check (or null if there is no payload)
     * @return whether this is a primary value or not
     */
    public static boolean isPrimaryValue(byte[] payload) {
        return payload == null || payload.length == 0 || payload[0] != BYTE_SECONDARY;
    }

    /**
     * Get the length of the "is primary value" indicator (may be 0).
     *
     * Note that not all annotations store the is-primary status in the payload (e.g. an annotation
     * without a forward index probably won't); only call this method for an annotation that stores this!
     *
     * @param payload payload value with optional indicator
     * @return length in bytes of the payload indicator (or 0 if none)
     */
    public static int getPrimaryValueIndicatorLength(BytesRef payload) {
        if (payload == null || payload.length == 0)
            return 0;
        byte firstByte = payload.bytes[payload.offset];
        return firstByte == BYTE_PRIMARY || firstByte == BYTE_SECONDARY ? 1 : 0;
    }

    /**
     * Get the length of the "is primary value" indicator (may be 0).
     *
     * Note that not all annotations store the is-primary status in the payload (e.g. an annotation
     * without a forward index probably won't); only call this method for an annotation that stores this!
     *
     * @param payload payload value with optional indicator
     * @return length in bytes of the payload indicator (or 0 if none)
     */
    public static int getPrimaryValueIndicatorLength(byte[] payload) {
        if (payload == null || payload.length == 0)
            return 0;
        byte firstByte = payload[0];
        return firstByte == BYTE_PRIMARY || firstByte == BYTE_SECONDARY ? 1 : 0;
    }

    /**
     * Strip the "is primary value" indicator from the payload, if there was one.
     *
     * Note that not all annotations store the is-primary status in the payload (e.g. an annotation without a forward
     * index probably won't); only call this method for an annotation that stores this.
     *
     * @param payload value to optionally strip from
     * @return resulting BytesRef value
     */
    public static BytesRef stripIsPrimaryValue(BytesRef payload) {
        if (payload == null || payload.length == 0)
            return payload;
        byte firstByte = payload.bytes[payload.offset];
        if (firstByte != BYTE_PRIMARY && firstByte != BYTE_SECONDARY)
            return payload;

        // payload.bytes[ offset .. offset + length - 1] shouldn't ever change (that would change the original payload)
        // so using it as a backing array for the stripped BytesRef should be fine here.
        if (payload.length == 1)
            return null;
        return new BytesRef(payload.bytes, payload.offset + 1, payload.length - 1);
    }

    /**
     * Get the bytes from a BytesRef in a separate array.
     *
     * @param bytesRef source
     * @return a new array with the same bytes
     */
    public static byte[] getBytes(BytesRef bytesRef) {
        byte[] bytes = null;
        if (bytesRef != null) {
            bytes = new byte[bytesRef.length];
            System.arraycopy(bytesRef.bytes, bytesRef.offset, bytes, 0, bytesRef.length);
        }
        return bytes;
    }

    /** Get a DataInput from a payload that may contain primary-value indicators. */
    public static ByteArrayDataInput getDataInput(byte[] payload, boolean payloadIndicatesPrimaryValues) {
        int skipBytes = payloadIndicatesPrimaryValues ? getPrimaryValueIndicatorLength(payload) : 0;
        return new ByteArrayDataInput(payload, skipBytes, payload.length - skipBytes);
    }

    /** Get a DataInput from a payload that may contain primary-value indicators. */
    public static ByteArrayDataInput getDataInput(BytesRef payload, boolean payloadIndicatesPrimaryValues) {
        int skipBytes = payloadIndicatesPrimaryValues ? getPrimaryValueIndicatorLength(payload) : 0;
        return new ByteArrayDataInput(payload.bytes, payload.offset + skipBytes, payload.length - skipBytes);
    }
}
