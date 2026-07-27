package nl.inl.blacklab.index.annotated;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.lucene.util.BytesRef;
import org.junit.Assert;
import org.junit.Test;

public class TestPooledBytesRefList {

    @Test
    public void storesCopiesAndSupportsListUpdates() {
        PooledBytesRefList list = new PooledBytesRefList();
        byte[] source = "xxoriginalyy".getBytes(StandardCharsets.UTF_8);

        list.add(null);
        list.add(new BytesRef(source, 2, 8));
        source[2] = 'X';
        list.add(1, new BytesRef("inserted"));

        Assert.assertNull(list.get(0));
        Assert.assertEquals(new BytesRef("inserted"), list.get(1));
        Assert.assertEquals(new BytesRef("original"), list.get(2));
        Assert.assertEquals(new BytesRef("inserted"), list.set(1, new BytesRef("replacement")));
        Assert.assertEquals(new BytesRef("replacement"), list.get(1));
    }

    @Test
    public void iteratesAcrossPoolBlocksInOrder() {
        PooledBytesRefList list = new PooledBytesRefList();
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < 5_000; i++) {
            String value = "payload-" + i;
            expected.add(value);
            list.add(new BytesRef(value));
        }

        List<String> actual = new ArrayList<>();
        for (BytesRef payload: list)
            actual.add(payload.utf8ToString());

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void storesPayloadsLargerThanAPoolBlock() {
        PooledBytesRefList list = new PooledBytesRefList();
        byte[] source = new byte[40_002];
        Arrays.fill(source, (byte)7);
        source[0] = 1;
        source[source.length - 1] = 2;

        list.add(new BytesRef(source, 1, 40_000));
        source[1] = 3;

        BytesRef stored = list.get(0);
        Assert.assertEquals(40_000, stored.length);
        Assert.assertEquals(7, stored.bytes[stored.offset]);
        Assert.assertEquals(7, stored.bytes[stored.offset + stored.length - 1]);
    }
}
