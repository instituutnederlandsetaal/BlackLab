package nl.inl.blacklab.index.annotated;

import java.io.IOException;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.util.BytesRef;
import org.eclipse.collections.api.list.primitive.IntList;

import nl.inl.blacklab.search.indexmetadata.SourceRangeEncoding;

/** Emits one constant term with an eight-byte start/end payload for each real token. */
final class TokenStreamSourceRanges extends TokenStream {

    private final IntList starts;

    private final IntList ends;

    private final byte[] payloadBytes = new byte[SourceRangeEncoding.PAYLOAD_LENGTH];

    private final BytesRef payload = new BytesRef(payloadBytes);

    private final CharTermAttribute termAttribute = addAttribute(CharTermAttribute.class);

    private final PositionIncrementAttribute positionIncrementAttribute =
            addAttribute(PositionIncrementAttribute.class);

    private final PayloadAttribute payloadAttribute = addAttribute(PayloadAttribute.class);

    private int index;

    TokenStreamSourceRanges(IntList starts, IntList ends) {
        if (starts.size() != ends.size())
            throw new IllegalArgumentException("Source range start/end counts differ");
        this.starts = starts;
        this.ends = ends;
    }

    @Override
    public void reset() throws IOException {
        super.reset();
        index = 0;
    }

    @Override
    public boolean incrementToken() {
        if (index >= starts.size())
            return false;
        clearAttributes();
        termAttribute.append(SourceRangeEncoding.TERM);
        positionIncrementAttribute.setPositionIncrement(1);
        writeInt(starts.get(index), 0);
        writeInt(ends.get(index), Integer.BYTES);
        payloadAttribute.setPayload(payload);
        index++;
        return true;
    }

    private void writeInt(int value, int offset) {
        payloadBytes[offset] = (byte) (value >>> 24);
        payloadBytes[offset + 1] = (byte) (value >>> 16);
        payloadBytes[offset + 2] = (byte) (value >>> 8);
        payloadBytes[offset + 3] = (byte) value;
    }
}
