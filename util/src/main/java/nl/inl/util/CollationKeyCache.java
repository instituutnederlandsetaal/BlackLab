package nl.inl.util;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import java.text.CollationKey;
import java.text.Collator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * A global cache for String to CollationKey mappings.
 * Since we typically index across multiple files and fields with the same collation settings,
 * we maintain a single cache per Collator configuration to maximize reuse.
 * 
 * The cache is tuned for heavy usage during indexing and initial index opening,
 * but sparse usage afterwards. The cache eviction time is set to 5 minutes of inactivity by default,
 * but can be adjusted as needed.
 */
public class CollationKeyCache {
    
    /**
     * Inner cache that holds String to CollationKey mappings for a specific Collator configuration.
     */
    public static class CollatorCache {
        /** We use thread-local collator instances to avoid synchronization issues, and to allow building keys in parallel */
        private final ThreadLocal<Collator> collator;
        private final LoadingCache<String, CollationKey> cache;
        
        private CollatorCache(Collator collator, long maxEntries) {
            this.collator = ThreadLocal.withInitial(() -> (Collator) collator.clone());
            this.cache = Caffeine.newBuilder()
                    .maximumSize(maxEntries)
                    .build(s -> this.collator.get().getCollationKey(s));
        }
        
        public CollationKey getCollationKey(String s) {
            return cache.get(s.intern());
        }
        
        public int compare(String a, String b) {
            return getCollationKey(a).compareTo(getCollationKey(b));
        }
        
        public long size() {
            return cache.estimatedSize();
        }

        public void clear() {
            cache.invalidateAll();
        }
        
        public CollationKey[] get(String[] strings) {
            return get(java.util.Arrays.asList(strings));
        }
        
        /**
         * This is a convenience method for List inputs. The returned array maintains
         * the same order as the input list.
         * 
         * @param strings the strings to get CollationKeys for
         * @return an array of CollationKeys in the same order as the input
         */
        public CollationKey[] get(List<String> strings) {
            CollationKey[] results = new CollationKey[strings.size()];

            // Process array in parallel using indices
            IntStream.range(0, strings.size())
                    .parallel()
                    .forEach(i -> results[i] = cache.get(strings.get(i)));
            return results;
        }
    }
    
    private static volatile long cacheExpirationMinutes = 5;
    private static volatile long maxIndividualCacheEntries = calculateMaxCacheSize();

    /** For a pair of String + collationkey */
    private static final int AVERAGE_BYTES_PER_ENTRY = 150;
    /** Percentage of max heap memory to use for the cache (default 5%) */
    private static final int MAX_CACHE_SIZE_PERCENTAGE_OF_HEAP = 5;
    
    private static final LoadingCache<Collator, CollatorCache> collatorCaches = Caffeine.newBuilder()
            .expireAfterAccess(cacheExpirationMinutes, TimeUnit.MINUTES)
            .weakKeys()  // Allow Collator keys to be garbage collected if no longer in use
            // .recordStats()  // Enable statistics
            .build(c -> new CollatorCache(c, maxIndividualCacheEntries));
    
    /**
     * Calculate a sensible maximum cache size based on available heap memory.
     * 
     * Uses a configurable percentage of max heap for the cache (default 5%).
     * Assumes each cache entry (String + CollationKey) takes approximately 150 bytes on average.
     * 
     * @return the maximum number of entries per CollatorCache
     */
    private static long calculateMaxCacheSize() {
        Runtime runtime = Runtime.getRuntime();
        long maxCacheSizeBytes = MAX_CACHE_SIZE_PERCENTAGE_OF_HEAP / 100 * (
            runtime.maxMemory() == Long.MAX_VALUE // If no xmx set, use current heap size
            ? runtime.totalMemory() 
            : runtime.maxMemory()
        );
        long maxEntries = maxCacheSizeBytes / AVERAGE_BYTES_PER_ENTRY;
        // Set a minimum of 10,000 entries, no upper limit (let heap size determine it)
        return Math.max(10_000, maxEntries);
    }
    
    /**
     * Get the CollatorCache for a specific Collator instance.
     * Creates a cache on first access.
     * 
     * Note: Collator instances with the same settings will share the same cache
     * as collators are compared using equals().
     * 
     * @param collator the Collator to get the cache for
     * @return a CollatorCache for the given Collator
     */
    public static CollatorCache forCollator(Collator collator) {
        return collatorCaches.get(collator);
    }
    
    public static void clearAll() {
        collatorCaches.invalidateAll();
    }
    
    public static long getCollatorCount() {
        return collatorCaches.estimatedSize();
    }
    
    public static long getMaxIndividualCacheEntries() {
        return maxIndividualCacheEntries;
    }
    
    public static CollationKey getCollationKey(Collator collator, String s) {
        return forCollator(collator).getCollationKey(s);
    }
    
    public static int compare(Collator collator, String a, String b) {
        return forCollator(collator).compare(a, b);
    }
}
