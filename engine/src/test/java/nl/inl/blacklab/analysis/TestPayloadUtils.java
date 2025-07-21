package nl.inl.blacklab.analysis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.store.ByteArrayDataInput;
import org.apache.lucene.util.BytesRef;
import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.search.indexmetadata.RelationsStrategy;
import nl.inl.blacklab.search.lucene.RelationInfo;

public class TestPayloadUtils {

    RelationsStrategy.PayloadCodec payloadCodec = RelationsStrategy.forNewIndex().getPayloadCodec();

    @Test
    public void testIsPrimaryValueIndicator() {
        // Various payloads to test with
        List<BytesRef> testPayloads = new ArrayList<>();
        testPayloads.add(null);
        testPayloads.addAll(List.of(
                new BytesRef(new byte[0]),
                new BytesRef(new byte[] { 0, 0, 0 }),
                new BytesRef(new byte[] { PayloadUtils.BYTE_PRIMARY }),
                new BytesRef(new byte[] { PayloadUtils.BYTE_SECONDARY })
        ));

        int i = 0;
        for (BytesRef payload: testPayloads) {
            testIsPrimaryValueIndicatorOnPayload(i, payload, true);
            testIsPrimaryValueIndicatorOnPayload(i, payload, false);
            i++;
        }
    }

    private void testIsPrimaryValueIndicatorOnPayload(int i, BytesRef payload, boolean isPrimary) {
        String title = "test " + i + ", primary = " + isPrimary + ": ";

        // Make sure we can determine whether the value is primary or not later
        BytesRef payloadPlus = PayloadUtils.addIsPrimary(isPrimary, payload);
        byte[] bytes = PayloadUtils.getBytes(payloadPlus);

        // Test that we can correctly determine it
        Assert.assertEquals(title + "check", isPrimary, PayloadUtils.isPrimaryValue(payloadPlus));
        Assert.assertEquals(title + "check 2", isPrimary, PayloadUtils.isPrimaryValue(bytes));

        // Test that stripping the indicator (if any) returns the original payload
        BytesRef compareTo = payload != null && payload.length == 0 ? null : payload; // empty payload will decode to null
        Assert.assertEquals(title + "equals", compareTo, PayloadUtils.stripIsPrimaryValue(payloadPlus));

        // Test that the length is as expected
        int indicatorLength = PayloadUtils.getPrimaryValueIndicatorLength(payloadPlus);
        Assert.assertEquals(title + "length", payload == null ? 0 : payload.length,
                (payloadPlus == null ? 0 : payloadPlus.length) - indicatorLength);
        indicatorLength = PayloadUtils.getPrimaryValueIndicatorLength(bytes);
        Assert.assertEquals(title + "length 2", payload == null ? 0 : payload.length,
                (payloadPlus == null ? 0 : payloadPlus.length) - indicatorLength);
    }

    @Test
    public void testInlineTagPayload() throws IOException {
        int[] starts = { 0, 10, 20 };
        int[] ends  = { 0, 11, 30 };
        for (int i = 0; i < starts.length; i++) {
            // Integrated index type: writes start and end position
            BytesRef b = payloadCodec.inlineTagPayload(starts[i], ends[i], 0, true);
            RelationInfo r = RelationInfo.create();
            payloadCodec.deserialize(starts[i], new ByteArrayDataInput(b.bytes), r);
            Assert.assertEquals(starts[i], r.getSpanStart());
            Assert.assertEquals(starts[i], r.getSourceStart());
            Assert.assertEquals(starts[i], r.getSourceEnd());
            Assert.assertEquals(ends[i], r.getSpanEnd());
            Assert.assertEquals(ends[i], r.getTargetEnd());
            Assert.assertEquals(ends[i], r.getTargetStart());
        }
    }

}
