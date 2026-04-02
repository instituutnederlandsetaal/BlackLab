package nl.inl.blacklab.tools.frequency.util;

import java.io.InputStream;

import org.apache.fory.io.ForyInputStream;

/**
 * Better buffer implementation of ForyInputStream.
 */
public final class BufferedForyInputStream extends ForyInputStream {
    // This value is unfortunately a hardcoded estimate.
    // As long as the buffer is large and the objects are small, it should be fine.
    private static final int leeway = 512;

    public BufferedForyInputStream(final InputStream stream, final int bufferSize) {
        super(stream, bufferSize);
    }

    @Override
    public void shrinkBuffer() {
        // shrink is called after every read in the base class, which is not necessary
        // so we override it to only shrink the buffer when we are nearing the end of the stream
        final int idx = getBuffer().readerIndex();
        final int bufferSize = getBuffer().size();
        // are we at the end?
        if (idx + leeway > bufferSize) {
            final int remaining = bufferSize - idx;
            final byte[] oldBuffer = getBuffer().getHeapMemory();
            final byte[] newBuffer = new byte[remaining];
            // copy remaining bytes to the beginning of the buffer
            System.arraycopy(oldBuffer, idx, newBuffer, 0, remaining);
            getBuffer().readerIndex(0);
            getBuffer().initHeapBuffer(newBuffer, 0, remaining);
        }
    }
}
