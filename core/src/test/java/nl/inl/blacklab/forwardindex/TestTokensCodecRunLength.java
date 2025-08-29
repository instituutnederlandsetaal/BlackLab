package nl.inl.blacklab.forwardindex;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import nl.inl.blacklab.codec.tokens.TokenValueType;
import nl.inl.blacklab.codec.tokens.TokensCodec;
import nl.inl.blacklab.codec.tokens.TokensCodecType;

public class TestTokensCodecRunLength {

    TokensCodec codec;

    private IndexOutput out;

    private IndexInput in;

    @Before
    public void setUp() {
        codec = TokensCodec.fromType(TokensCodecType.RUN_LENGTH_ENCODING,
                TokenValueType.BYTE.code);
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        out = new MockIndexOutput(buffer);
        in = new MockIndexInput("test", buffer);
    }

    private void testWriteAndReadBack(int[] tokens) throws IOException {
        codec.writeTokens(tokens, out);
        int[] snippet = new int[tokens.length];
        codec.readSnippet(in, 0, 0, snippet);
        Assert.assertArrayEquals(tokens, snippet);
    }

    @Test
    public void testSimple() throws IOException {
        testWriteAndReadBack(new int[] {1, 1, 1, 2, 3, 3, 3, 3, 4, 5, 5, 5, 5, 5});
    }

    @Test
    public void testEmpty() throws IOException {
        testWriteAndReadBack(new int[0]);
    }

    @Test
    public void testLarge() throws IOException {
        int v = codec.valueType().maxValue();
        testWriteAndReadBack(new int[] { v, v, v, v, v, v });
    }

    private static class MockIndexOutput extends IndexOutput {
        private final ByteBuffer buffer;

        public MockIndexOutput(ByteBuffer buffer) {
            super("test", "test");
            this.buffer = buffer;
        }

        @Override
        public void writeByte(byte b) throws IOException {
            buffer.put(b);
        }

        @Override
        public void writeBytes(byte[] b, int offset, int length) throws IOException {
            buffer.put(b, offset, length);
        }

        @Override
        public void close() {
        }

        @Override
        public long getFilePointer() {
            return buffer.position();
        }

        @Override
        public long getChecksum() throws IOException {
            return 0;
        }
    }

    private static class MockIndexInput extends IndexInput {

        private final ByteBuffer buffer;

        public MockIndexInput(String description, ByteBuffer buffer) {
            super(description);
            this.buffer = buffer;
        }

        @Override
        public byte readByte() throws IOException {
            return buffer.get();
        }

        @Override
        public void readBytes(byte[] b, int offset, int len) throws IOException {
            buffer.get(b, offset, len);
        }

        @Override
        public void close() {
        }

        @Override
        public long getFilePointer() {
            return buffer.position();
        }

        @Override
        public void seek(long pos) {
            buffer.position((int) pos);
        }

        @Override
        public long length() {
            return buffer.limit();
        }

        @Override
        public IndexInput slice(String sliceDescription, long offset, long length) throws IOException {
            return new MockIndexInput(sliceDescription, buffer.slice((int)offset, (int)length));
        }
    }
}
