package org.ivdnt.blacklab.solr;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.io.input.ReaderInputStream;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.BytesTermAttribute;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.TermToBytesRefAttribute;
import org.apache.lucene.document.Field;
import org.apache.lucene.store.InputStreamDataInput;
import org.apache.lucene.store.OutputStreamDataOutput;
import org.apache.lucene.util.AttributeSource;
import org.apache.lucene.util.BytesRef;
import org.apache.solr.schema.PreAnalyzedField;

/**
 * Allow serializing a Field containing a TokenStream to a String, and decoding a Reader back into the TokenStream
 * This class is required because it appears SOLR cannot directly handle TokenStreams in the input layer (i.e. in a SolrInputDocument).
 * Instead, Solr assumes all field values are strings (or perhaps numbers), and parsing those into TokenStreams (using a Tokenizer or PreAnalyzedParser)
 * happens later.
 *
 * Since Lucene CAN directly index a TokenStream, BlackLab is written to produce TokenStreams.
 * Therefor, this class sits between a BLInputDocument and a SolrInputDocument to facilitate the conversion from/to string/tokenstream.
 * See {@link nl.inl.blacklab.index.BLInputDocumentSolr}.
 *
 * Also see the managed-schema where a fieldtype exists that points to this parser.
 * (Which is relevant when loading a document from the solr commitlog, or when distributing a document across shard).
 * (documents are parsed from their stringified representation in these situations).
 */
public class BLSolrPreAnalyzedFieldParser implements PreAnalyzedField.PreAnalyzedParser {

    @Override
    public PreAnalyzedField.ParseResult parse(Reader reader, AttributeSource parent) throws IOException {
        PreAnalyzedField.ParseResult r = new PreAnalyzedField.ParseResult();
        try (var in = new InputStreamDataInputWithChar(new GZIPInputStream(
                new ReaderInputStream(reader, new ByteStringCharset())))) {
            var token = in.readByte() != 0 ?
                    parent.addAttribute(CharTermAttribute.class) : null;
            var increment = in.readByte() != 0 ?
                    parent.addAttribute(PositionIncrementAttribute.class) : null; // optional?
            var offsets = in.readByte() != 0 ?
                    parent.addAttribute(OffsetAttribute.class) : null; // optional?
            var payloads = in.readByte() != 0 ?
                    parent.addAttribute(PayloadAttribute.class) : null; // optional?
            while (true) {
                readCharTermAttribute(in, token);
                readPositionIncrementAttribute(in, increment);
                readOffsetAttribute(in, offsets);
                readPayloadAttribute(in, payloads);

                r.states.add(parent.captureState());
                parent.clearAttributes();
            }
        } catch (EOFException e) {
            // There is no other way to detect all input has been read :(
            // ignore.
        }
        return r;
    }

    @Override
    public String toFormattedString(Field f) throws IOException {
        if (f.binaryValue() != null || f.stringValue() != null) throw new IllegalStateException("BlackLab PreAnalyzedField only supports tokenstream fields.");

        TokenStream ts = f.tokenStreamValue();
        if (ts == null) return null;

        var token = ts.getAttribute(CharTermAttribute.class);
        var increment = ts.getAttribute(PositionIncrementAttribute.class); // optional?
        var offsets = ts.getAttribute(OffsetAttribute.class); // optional?
        var payloads = ts.getAttribute(PayloadAttribute.class); // optional?

        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        GZIPOutputStream zo = new GZIPOutputStream(bao);
        OutputStreamDataOutputWithChar o = new OutputStreamDataOutputWithChar(zo);

        // when decoding, we must know which fields are present.
        // so write a 0 or 1 for every attribute indicating presence
        o.writeByte((byte) (token != null ? 1 : 0));
        o.writeByte((byte) (increment != null ? 1 : 0));
        o.writeByte((byte) (offsets != null ? 1 : 0));
        o.writeByte((byte) (payloads != null ? 1 : 0));

        while (ts.incrementToken()) {
            writeCharTermAttribute(o, token);
            writePositionIncrementAttribute(o, increment);
            writeOffsetAttribute(o, offsets);
            writePayloadAttribute(o, payloads);
        }
        o.close(); // ensure all data has been flushed.

        return new String(bao.toByteArray(),new ByteStringCharset());
    }

    private void writeCharTermAttribute(OutputStreamDataOutputWithChar o, CharTermAttribute a) throws IOException {
        if (a == null) return;
        int length = a.length();
        char[] buffer = a.buffer();
        o.writeInt(length);
        for (int i = 0; i < length; ++i) o.writeChar(buffer[i]);
    }

    private void readCharTermAttribute(InputStreamDataInputWithChar in, CharTermAttribute a) throws IOException {
        if (a == null) return;

        int length = in.readInt();
        if (a.buffer().length < length)
            a.resizeBuffer(length);

        a.setLength(length);
        for (int i = 0; i < length; ++i)
            a.buffer()[i] = in.readChar();
    }
    private void writeBytesRef(OutputStreamDataOutputWithChar o, BytesRef r) throws IOException {
        // null case
        if (r == null) {
            o.writeInt(0);
            return;
        }
        // regular case
        int length = r.length;
        int offset = r.offset;
        byte[] buffer = r.bytes;
        o.writeInt(length);
        for (int i = 0; i < length; ++i) o.writeByte(buffer[offset + i]);
    }

    private BytesRef readBytesRef(InputStreamDataInputWithChar in, BytesRef r) throws IOException {
        // null case.
        int length = in.readInt();
        if (length == 0) return null;
        // regular case
        if (r == null) r = new BytesRef();
        r.length = in.readInt();
        r.offset = 0;
        // be safe and use a new byte array, since it may be shared with another bytesref, we don't want to accidentally clobber its data.
        if (r.length > 0) r.bytes = new byte[r.length];
        for (int i = 0; i < r.length; ++i) r.bytes[i + r.offset] = in.readByte();
        return r;
    }

    private void writeTermToBytesRefAttribute(OutputStreamDataOutputWithChar o, TermToBytesRefAttribute a) throws IOException {
        if (a != null) writeBytesRef(o, a.getBytesRef());
    }

    private void readTermToBytesRefAttribute(InputStreamDataInputWithChar in, TermToBytesRefAttribute a) throws IOException {
        if (a != null) readBytesRef(in, a.getBytesRef());
    }

    private void writeBytesTermAttribute(OutputStreamDataOutputWithChar o, BytesTermAttribute a) throws IOException {
        if (a != null) writeBytesRef(o, a.getBytesRef());
    }

    private void readBytesTermAttribute(InputStreamDataInputWithChar in, BytesTermAttribute a) throws IOException {
        if (a != null) a.setBytesRef(readBytesRef(in, a.getBytesRef()));
    }

    private void writePositionIncrementAttribute(OutputStreamDataOutputWithChar o, PositionIncrementAttribute a) throws IOException {
        if (a == null) return;
        o.writeInt(a.getPositionIncrement());
    }

    private void readPositionIncrementAttribute(InputStreamDataInputWithChar o, PositionIncrementAttribute a) throws IOException {
        if (a == null) return;
        a.setPositionIncrement(o.readInt());
    }

    private void writeOffsetAttribute(OutputStreamDataOutputWithChar o, OffsetAttribute a) throws IOException {
        if (a == null) return;
        o.writeInt(a.startOffset());
        o.writeInt(a.endOffset());
    }

    private void readOffsetAttribute(InputStreamDataInputWithChar in, OffsetAttribute a) throws IOException {
        if (a == null) return;
        a.setOffset(in.readInt(), in.readInt());
    }

    private void writePayloadAttribute(OutputStreamDataOutputWithChar o, PayloadAttribute a) throws IOException {
        if (a == null) return;
        writeBytesRef(o, a.getPayload());
    }

    private void readPayloadAttribute(InputStreamDataInputWithChar in, PayloadAttribute a) throws IOException {
        if (a == null) return;
        a.setPayload(readBytesRef(in, a.getPayload()));
    }

    private static class OutputStreamDataOutputWithChar extends OutputStreamDataOutput {
        public OutputStreamDataOutputWithChar(OutputStream os) {
            super(os);
        }

        void writeChar(char c) throws IOException {
            writeShort((short) (c & 0xFFFF));
        }
    }

    private static class InputStreamDataInputWithChar extends InputStreamDataInput {
        public InputStreamDataInputWithChar(InputStream is) {
            super(is);
        }

        char readChar() throws IOException {
            return (char) readShort();
        }
    }

    /**
     * This is a custom charset that can transparently convert a byte[] to a String and vice versa.
     * We do this because a binary representation will always be more efficient than a string representation.
     * e.g. the number 10 takes only 4 bits, but the string "10" takes up to 32.
     *
     * Usage:
     * <pre>
     *     // encode
     *     ByteArrayOutputStream stream = ...
     *     stream.write()...
     *     String asString = new String(stream.toByteArray(),new ByteStringCharset());
     *     // and decode again
     *     asString.getBytes(new ByteStringCharset())
     * </pre>
     */
    public static class ByteStringCharset extends Charset {
        public ByteStringCharset() {
            super("ByteStringCharset", null);
        }

        @Override
        public boolean contains(Charset cs) {
            return true;
        }

        @Override
        public CharsetDecoder newDecoder() {
            return new CharsetDecoder(this, 1, 1) {
                @Override
                protected CoderResult decodeLoop(ByteBuffer in, CharBuffer out) {
                    while (true) {
                        if (!in.hasRemaining()) return CoderResult.UNDERFLOW;
                        if (!out.hasRemaining()) return CoderResult.OVERFLOW;

                        byte a = in.get();
                        byte b = in.hasRemaining() ? in.get() : 0;
                        char r = this.encode(a, b);

                        out.put(r);
                    }
                }

                private char encode(byte a, byte b) {
                    char r = (char) (((a&0xFF) << 8) | (b & 0xFF));
                    return r;
                }
            };
        }

        @Override
        public CharsetEncoder newEncoder() {
            return new CharsetEncoder(this, 2, 2) {
                @Override
                protected CoderResult encodeLoop(CharBuffer in, ByteBuffer out) {
                    while (true) {
                        if (!in.hasRemaining()) return CoderResult.UNDERFLOW;
                        if (!out.hasRemaining()) return CoderResult.OVERFLOW;

                        char i = in.get();
                        byte first = (byte) (i >> 8);
                        byte second = (byte) i;
                        out.put(first);
                        if (!out.hasRemaining()) {
                            // undo our last operation.
                            in.position(in.position() - 1);
                            out.position(out.position() - 1);
                            return CoderResult.OVERFLOW;
                        }
                        out.put(second);
                    }
                }
            };
        }
    }
}
