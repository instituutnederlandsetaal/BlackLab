package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;

import org.apache.lucene.payloads.PayloadSpanCollector;
import org.apache.lucene.search.spans.FilterSpans;

import nl.inl.blacklab.analysis.PayloadUtils;
import nl.inl.blacklab.exceptions.InvalidIndex;

/**
 * Gets spans for a certain XML element.
 */
class SpansTagsExternal extends BLFilterSpans<BLSpans> {

    private int end = -1; // -1: not nexted yet. -2: payload not read yet.

    /** If true, we have to skip the primary value indicator in the payload (see PayloadUtils) */
    private final boolean payloadIndicatesPrimaryValues;

    /**
     * Construct SpansTags.
     *
     * NOTE: start tag payloads contain the location of the end tag. To work with these,
     * we also need to know if there's "is primary value" indicators in (some of) the payloads,
     * so we can skip these. See {@link PayloadUtils}.
     *
     * @param startTags the positions of our start tags
     * @param payloadIndicatesPrimaryValues whether or not there's "is primary value" indicators in the payloads
     */
    public SpansTagsExternal(BLSpans startTags, boolean payloadIndicatesPrimaryValues) {
        super(startTags);
        this.payloadIndicatesPrimaryValues = payloadIndicatesPrimaryValues;
    }

    @Override
    protected FilterSpans.AcceptStatus accept(BLSpans candidate) throws IOException {
        return FilterSpans.AcceptStatus.YES;
    }

    @Override
    public int nextDoc() throws IOException {
        assert docID() != NO_MORE_DOCS;
        end = -1; // not nexted yet
        return super.nextDoc();
    }

    @Override
    public int advance(int target) throws IOException {
        assert target >= 0 && target > docID();
        end = -1; // not nexted yet
        return super.advance(target);
    }

    @Override
    public int nextStartPosition() throws IOException {
        assert startPosition() != NO_MORE_POSITIONS;
        end = -2; // payload not read yet
        return super.nextStartPosition();
    }

    private final PayloadSpanCollector collector = new PayloadSpanCollector();

    @Override
    public int endPosition() {
        if (startPos == NO_MORE_POSITIONS)
            return NO_MORE_POSITIONS;
        try {
            if (end == -2) {
                collector.reset();

                // NOTE: tags is a BLSpanTermQuery, a leaf, so we know there can only be one payload
                //   each start tag gets a payload, so there should always be one
                in.collect(collector);
                Iterator<byte[]> iterator = collector.getPayloads().iterator();
                byte[] payload = iterator.hasNext() ? iterator.next() : SpansRelations.EMPTY_PAYLOAD;
                ByteBuffer bb = ByteBuffer.wrap(payload);
                if (payloadIndicatesPrimaryValues)
                    bb.position(PayloadUtils.getPrimaryValueIndicatorLength(payload)); // skip indicator
                end = bb.getInt();
            }
            return end;
        } catch (IOException e) {
            throw new InvalidIndex("Error getting payload");
        }
    }

    @Override
    public String toString() {
        return "TAGS_EXT(" + in + ')';
    }
}
