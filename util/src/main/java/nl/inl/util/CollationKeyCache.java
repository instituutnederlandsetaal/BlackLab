package nl.inl.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;

import java.text.CollationKey;
import java.text.Collator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A global cache for String to CollationKey mappings.
 * 
 * This cache is organized hierarchically: the top level caches different Collator instances,
 * and each Collator has its own cache of String to CollationKey mappings.
 *
 * The cache is designed for heavy usage during indexing and initial index opening,
 * but sparse usage afterwards. Therefore, Collator-specific caches expire after a 
 * configurable duration of inactivity.
 * 
 * Thread-safety: This class is fully thread-safe and optimized for high-concurrency workloads.
 * 
 * Note: The cache uses Collator instances as keys, relying on their equals() method which
 * correctly compares settings. Although Collator.hashCode() has poor distribution (all instances
 * return the same hash), Caffeine handles this gracefully with negligible performance impact
 * since we typically only cache a small number of different Collator configurations.
 */
public class CollationKeyCache {
    
    /**
     * Inner cache that holds String to CollationKey mappings for a specific Collator configuration.
     */
    public static class CollatorCache {
        private final ThreadLocal<Collator> collator;
        private final LoadingCache<String, CollationKey> cache;
        
        // Thread-local storage for Collator clones, one per thread
        // This is reused across all calls to populateParallel() for this CollatorCache instance
        private final ThreadLocal<Collator> threadLocalCollator;
        
        private CollatorCache(Collator collator, long maxSize) {
            this.collator = ThreadLocal.withInitial(() -> (Collator) collator.clone());
            this.cache = Caffeine.newBuilder()
                    .maximumSize(maxSize)
                    .recordStats()  // Enable statistics for monitoring
                    .build(s -> this.collator.get().getCollationKey(s));
            this.threadLocalCollator = ThreadLocal.withInitial(() -> (Collator) collator.clone());
        }
        
        /**
         * Get the CollationKey for a given string, using the cache.
         * 
         * @param s the string to get the CollationKey for
         * @return the CollationKey for the string
         */
        public CollationKey getCollationKey(String s) {
            // Use computeIfAbsent for atomic check-and-create
            return cache.get(s.intern());
        }
        
        /**
         * Compare two strings using this Collator's collation rules.
         * 
         * This method uses cached CollationKeys for efficient comparison,
         * avoiding the synchronized overhead of Collator.compare().
         * 
         * @param a first string to compare
         * @param b second string to compare
         * @return negative if a < b, zero if a == b, positive if a > b
         */
        public int compare(String a, String b) {
            return getCollationKey(a).compareTo(getCollationKey(b));
        }
        
        /**
         * Get the current size of this cache.
         * 
         * @return the number of entries in the cache
         */
        public long size() {
            return cache.estimatedSize();
        }
        
        /**
         * Get cache statistics (hit rate, miss rate, eviction count, etc.).
         * 
         * @return cache statistics
         */
        public CacheStats stats() {
            return cache.stats();
        }
        
        /**
         * Clear all entries from this cache.
         */
        public void clear() {
            cache.invalidateAll();
        }
        
        public CollationKey[] get(String[] strings) {
            return get(java.util.Arrays.asList(strings));
        }
        
        /**
         * Populate the cache with CollationKeys for the given strings in parallel,
         * and return an array of the results.
         * 
         * This is a convenience method for List inputs. The returned array maintains
         * the same order as the input list.
         * 
         * @param strings the strings to get CollationKeys for
         * @return an array of CollationKeys in the same order as the input
         */
        public CollationKey[] get(List<String> strings) {
            CollationKey[] results = new CollationKey[strings.size()];

            // Process array in parallel using indices
            java.util.stream.IntStream.range(0, strings.size())
                    .parallel()
                    .forEach(i -> results[i] = cache.get(strings.get(i)));
            return results;
        }
    }
    
    // Top-level cache: maps Collator instances to their respective caches
    // Note: Collator.equals() works correctly but hashCode() has poor distribution.
    // This is acceptable since we typically only have a few different Collator configurations.
    // Configurable expiration time and maximum size
    private static volatile long cacheExpirationMinutes = 5;
    private static volatile long maxCacheSize = calculateMaxCacheSize();
    
    private static final Cache<Collator, CollatorCache> collatorCaches = Caffeine.newBuilder()
            .expireAfterAccess(cacheExpirationMinutes, TimeUnit.MINUTES)
            .recordStats()  // Enable statistics
            .build();
    
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
        long maxMemory = Math.max(runtime.maxMemory(), runtime.totalMemory());

        // Use 5% of max heap for the cache (increased from 1% to support larger datasets)
        long memoryForCache = maxMemory / 20;

        // More accurate estimate: ~150 bytes per entry
        // String (24 byte header + array) + CollationKey (varies, ~50-100 bytes) + HashMap overhead (~32 bytes)
        long maxEntries = memoryForCache / 150;

        // Set a minimum of 10,000 entries, no upper limit (let heap size determine it)
        return Math.max(10_000, maxEntries);
    }
    
    /**
     * Get the CollatorCache for a specific Collator instance.
     * 
     * This method returns a cache that is specific to the given Collator.
     * The cache will be created on first access and will expire after the configured
     * duration of inactivity.
     * 
     * Note: Collator instances with the same settings will share the same cache
     * thanks to Collator.equals() correctly comparing configurations.
     * 
     * @param collator the Collator to get the cache for
     * @return a CollatorCache for the given Collator
     */
    public static CollatorCache forCollator(Collator collator) {
        return collatorCaches.get(collator, key -> new CollatorCache(collator, maxCacheSize));
    }
    
    /**
     * Clear all caches (for all Collators).
     */
    public static void clearAll() {
        collatorCaches.invalidateAll();
    }
    
    /**
     * Get the number of Collator instances currently cached.
     * 
     * @return the number of cached Collator instances
     */
    public static long getCollatorCount() {
        return collatorCaches.estimatedSize();
    }
    
    /**
     * Get cache statistics for the top-level cache.
     * 
     * @return cache statistics
     */
    public static CacheStats getStats() {
        return collatorCaches.stats();
    }
    
    /**
     * Get the maximum size configured for each CollatorCache.
     * 
     * @return the maximum number of entries per CollatorCache
     */
    public static long getMaxCacheSize() {
        return maxCacheSize;
    }
    
    /**
     * Convenience method to get a CollationKey using the cache.
     * 
     * This is a shorthand for: CollationKeyCache.forCollator(collator).getCollationKey(s)
     * 
     * @param collator the Collator to use
     * @param s the string to get the CollationKey for
     * @return the CollationKey for the string
     */
    public static CollationKey getCollationKey(Collator collator, String s) {
        return forCollator(collator).getCollationKey(s);
    }
    
    /**
     * Convenience method to compare two strings using cached CollationKeys.
     * Calls collator.getCollationKey(a).compare(collator.getCollationKey(b)) under the hood, but uses cached CollationKeys for efficiency.
     */
    public static int compare(Collator collator, String a, String b) {
        return forCollator(collator).compare(a, b);
    }
}
