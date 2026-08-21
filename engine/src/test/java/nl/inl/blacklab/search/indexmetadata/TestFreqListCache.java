package nl.inl.blacklab.search.indexmetadata;

import org.junit.Assert;
import org.junit.Test;

public class TestFreqListCache {

    private static TruncatableFreqList listOf(String... values) {
        TruncatableFreqList list = new TruncatableFreqList(values.length);
        for (String v : values) {
            list.add(v, 1L);
        }
        return list;
    }

    private static TruncatableFreqList truncatedListOf(int limit, String... values) {
        TruncatableFreqList list = new TruncatableFreqList(limit);
        for (String v : values) {
            list.add(v, 1L);
        }
        return list;
    }

    @Test
    public void testGetMissOnEmptyCache() {
        FreqListCache cache = new FreqListCache();
        Assert.assertNull(cache.get("field1", 10));
    }

    @Test
    public void testPutAndGetExact() {
        FreqListCache cache = new FreqListCache();
        TruncatableFreqList list = listOf("a", "b", "c");
        cache.put("field1", list);

        TruncatableFreqList result = cache.get("field1", 3);
        Assert.assertNotNull(result);
        Assert.assertEquals(3, result.size());
    }

    @Test
    public void testGetTruncated() {
        FreqListCache cache = new FreqListCache();
        TruncatableFreqList list = listOf("a", "b", "c", "d", "e");
        cache.put("field1", list);

        TruncatableFreqList result = cache.get("field1", 3);
        Assert.assertNotNull(result);
        Assert.assertEquals(3, result.size());
        Assert.assertTrue(result.isTruncated());
    }

    @Test
    public void testGetMissWhenRequestedLimitTooLarge() {
        FreqListCache cache = new FreqListCache();
        // Store a truncated list of 3 values (truncated at limit 3)
        TruncatableFreqList list = truncatedListOf(3, "a", "b", "c", "d", "e");
        cache.put("field1", list);

        // Asking for 5 when we only have 3 truncated values – cache miss
        TruncatableFreqList result = cache.get("field1", 5);
        Assert.assertNull(result);
    }

    @Test
    public void testLargerEntryReplacesSmallerTruncated() {
        FreqListCache cache = new FreqListCache();
        // First, store a small truncated entry
        TruncatableFreqList small = truncatedListOf(3, "a", "b", "c", "d");
        cache.put("field1", small);
        Assert.assertEquals(1, cache.size());

        // Now store a larger entry
        TruncatableFreqList large = listOf("a", "b", "c", "d", "e");
        cache.put("field1", large);
        // Still one entry per field
        Assert.assertEquals(1, cache.size());

        // We can now serve up to 5 values
        Assert.assertNotNull(cache.get("field1", 5));
    }

    @Test
    public void testSmallerEntryDoesNotReplaceExistingLarger() {
        FreqListCache cache = new FreqListCache();
        TruncatableFreqList large = listOf("a", "b", "c", "d", "e");
        cache.put("field1", large);

        TruncatableFreqList small = truncatedListOf(2, "a", "b", "c");
        cache.put("field1", small);

        // Should still be able to serve 5 (large list not overwritten)
        Assert.assertNotNull(cache.get("field1", 5));
    }

    @Test
    public void testLruEviction() {
        // maxEntries = 2
        FreqListCache cache = new FreqListCache(2, 1_000);
        cache.put("field1", listOf("a"));
        cache.put("field2", listOf("b"));
        // Access field1 to make it recently used
        cache.get("field1", 1);
        // Adding field3 should evict the LRU entry (field2)
        cache.put("field3", listOf("c"));

        Assert.assertEquals(2, cache.size());
        Assert.assertNotNull(cache.get("field1", 1));
        Assert.assertNotNull(cache.get("field3", 1));
        Assert.assertNull(cache.get("field2", 1));
    }

    @Test
    public void testTooLargeEntryNotCached() {
        // maxValuesPerEntry = 3
        FreqListCache cache = new FreqListCache(100, 3);
        TruncatableFreqList large = listOf("a", "b", "c", "d");
        cache.put("field1", large);
        Assert.assertEquals(0, cache.size());
    }

    @Test
    public void testClear() {
        FreqListCache cache = new FreqListCache();
        cache.put("field1", listOf("a"));
        cache.put("field2", listOf("b"));
        cache.clear();
        Assert.assertEquals(0, cache.size());
    }
}
