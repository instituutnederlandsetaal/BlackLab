package org.ivdnt.blacklab.proxy.helper;

import java.util.Locale;
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;

import com.ibm.icu.text.Collator;

public class TestSortValueUtil {

    /**
     * Test that some numbers encode to the expected value.
     * This method is implementation-dependent.
     */
    @Test
    public void intEncode() {
        Assert.assertEquals("0000000000", SortValueUtil.encode(0));
        Assert.assertEquals("0000000001", SortValueUtil.encode(1));
        Assert.assertEquals("0000000002", SortValueUtil.encode(2));
        Assert.assertEquals("0123456789", SortValueUtil.encode(123456789));
        Assert.assertEquals("2147483647", SortValueUtil.encode(Integer.MAX_VALUE));
        Assert.assertThrows(IllegalArgumentException.class, () -> SortValueUtil.encode(Integer.MIN_VALUE));
    }

    /**
     * Test that some encoded numbers are decoded to the expected value.
     * This method is implementation-dependent.
     */
    @Test
    public void intDecode() {
        Assert.assertEquals(0, SortValueUtil.decodeInt("0000000000"));
        Assert.assertEquals(1, SortValueUtil.decodeInt("0000000001"));
        Assert.assertEquals(2, SortValueUtil.decodeInt("0000000002"));
        Assert.assertEquals(123456789, SortValueUtil.decodeInt("0123456789"));
        Assert.assertEquals(Integer.MAX_VALUE, SortValueUtil.decodeInt("2147483647"));
    }

    /**
     * Test that some numbers encode to the expected value.
     * This method is implementation-dependent.
     */
    @Test
    public void longEncode() {
        Assert.assertEquals("0000000000000000000", SortValueUtil.encode(0L));
        Assert.assertEquals("0000000000000000001", SortValueUtil.encode(1L));
        Assert.assertEquals("0000000000000000002", SortValueUtil.encode(2L));
        Assert.assertEquals(Long.toString(Long.MAX_VALUE), SortValueUtil.encode(Long.MAX_VALUE));
        Assert.assertThrows(IllegalArgumentException.class, () -> SortValueUtil.encode(Long.MIN_VALUE));
    }

    /**
     * Test that some encoded numbers are decoded to the expected value.
     * This method is implementation-dependent.
     */
    @Test
    public void longDecode() {
        Assert.assertEquals(0L, SortValueUtil.decodeLong("0000000000000000000"));
        Assert.assertEquals(1L, SortValueUtil.decodeLong("0000000000000000001"));
        Assert.assertEquals(2L, SortValueUtil.decodeLong("0000000000000000002"));
        Assert.assertEquals(Long.MAX_VALUE, SortValueUtil.decodeLong(Long.toString(Long.MAX_VALUE)));
    }

    /**
     * Test that numbers encode+decode back to the correct value.
     *
     * Also tests that ASVs don't exceed max. length
     */
    @Test
    public void testNumbers() {
        Random r = new Random(23456);
        for (int i = 0; i < 1000; i++) {
            int x = Math.abs(r.nextInt());
            String encoded = SortValueUtil.encode(x);
            Assert.assertEquals("int", x, SortValueUtil.decodeInt(encoded));
        }
        for (int i = 0; i < 1000; i++) {
            long x = Math.abs(r.nextLong());
            String encoded = SortValueUtil.encode(x);
            Assert.assertEquals("long", x, SortValueUtil.decodeLong(SortValueUtil.encode(x)));
        }
    }

    /**
     * Test that encoding doesn't change comparison outcome
     */
    @Test
    public void testComparisonInt() {
        Random r = new Random(45678);
        Collator coll = Collator.getInstance(Locale.ENGLISH);
        coll.freeze();
        for (int i = 0; i < 1000; i++) {
            int a = Math.abs(r.nextInt());
            int b = Math.abs(r.nextInt());
            String ae = SortValueUtil.encode(a);
            String be = SortValueUtil.encode(b);
            Assert.assertEquals("int " + a + ", " + b + " => " + ae + ", " + be, Integer.compare(a, b), coll.compare(ae, be));
        }
        for (int i = 0; i < 1000; i++) {
            long a = Math.abs(r.nextLong());
            long b = Math.abs(r.nextLong());
            String ae = SortValueUtil.encode(a);
            String be = SortValueUtil.encode(b);
            Assert.assertEquals("long " + a + ", " + b + " => " + ae + ", " + be, Long.compare(a, b), coll.compare(ae, be));
        }
    }
}
