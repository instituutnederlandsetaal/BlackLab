package nl.inl.blacklab.search.indexmetadata;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * LRU cache for {@link TruncatableFreqList} instances, used to avoid
 * re-reading annotation and metadata field values from the Lucene index on
 * every request.
 * <p>
 * The cache is keyed by field/annotation name. Each {@link nl.inl.blacklab.search.BlackLabIndex}
 * instance holds its own {@code FreqListCache}, so there is no need to include
 * the index name in the key.
 * <p>
 * Only one entry per field is kept: when a new list with more values is stored,
 * any existing entry for the same field (which would be a subset) is replaced.
 * When a requested {@code limitValues} can be satisfied by truncating a cached
 * entry, the truncated version is returned without a re-read.
 * <p>
 * To avoid excessive memory use, entries larger than {@link #maxValuesPerEntry}
 * are never stored, and the cache is evicted down to {@link #maxEntries} using
 * an LRU policy.
 */
public class FreqListCache {

    private static final Logger logger = LogManager.getLogger(FreqListCache.class);

    /** Default maximum number of cache entries (one per field/annotation). */
    public static final int DEFAULT_MAX_ENTRIES = 200;

    /**
     * Default maximum number of values in a single cached entry.
     * Entries larger than this will not be stored, to prevent memory issues
     * with very large annotation/field value lists.
     */
    public static final int DEFAULT_MAX_VALUES_PER_ENTRY = 10_000;

    private final int maxEntries;
    private final int maxValuesPerEntry;

    /**
     * LRU map: key is the field/annotation name, value is the (possibly truncated)
     * {@link TruncatableFreqList}.
     */
    private final Map<String, TruncatableFreqList> cache;

    public FreqListCache() {
        this(DEFAULT_MAX_ENTRIES, DEFAULT_MAX_VALUES_PER_ENTRY);
    }

    public FreqListCache(int maxEntries, int maxValuesPerEntry) {
        this.maxEntries = maxEntries;
        this.maxValuesPerEntry = maxValuesPerEntry;
        // Access-ordered LinkedHashMap for LRU behaviour.
        cache = new LinkedHashMap<>(maxEntries, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, TruncatableFreqList> eldest) {
                return size() > maxEntries;
            }
        };
    }

    /**
     * Look up cached values for a field/annotation.
     *
     * @param fieldName   field or annotation name
     * @param limitValues maximum number of values requested
     * @return a {@link TruncatableFreqList} (possibly truncated) if the cache can
     *         satisfy the request, or {@code null} on a cache miss
     */
    public synchronized TruncatableFreqList get(String fieldName, long limitValues) {
        TruncatableFreqList cached = cache.get(fieldName);
        if (cached == null)
            return null;
        if (!cached.canTruncateTo(limitValues))
            return null; // cached entry doesn't have enough data
        return cached.truncated(limitValues);
    }

    /**
     * Store (or update) a cached entry for a field/annotation.
     * <p>
     * If the given list is larger than {@link #maxValuesPerEntry} it will not be
     * stored. If an existing entry already covers the same or more values, the
     * existing entry is kept. When the new entry supersedes (is larger than) an
     * existing entry, the old entry is replaced.
     *
     * @param fieldName field or annotation name
     * @param freqList  the value list to cache
     */
    public synchronized void put(String fieldName, TruncatableFreqList freqList) {
        if (freqList == null || freqList.size() == 0)
            return;
        if (freqList.size() > maxValuesPerEntry) {
            logger.debug("FreqListCache: not caching {} – too large ({} values)", fieldName, freqList.size());
            return;
        }
        TruncatableFreqList existing = cache.get(fieldName);
        if (existing != null) {
            // Keep the existing entry if it is strictly better:
            //   - it is not truncated (complete), while the new one is, OR
            //   - both are truncated but the existing one has at least as many values.
            boolean existingIsComplete = !existing.isTruncated();
            boolean newIsComplete = !freqList.isTruncated();
            if (existingIsComplete) {
                // Existing is complete; only replace if new is also complete and larger (shouldn't
                // normally happen, but be safe).
                if (!newIsComplete || freqList.size() <= existing.size())
                    return;
            } else if (!newIsComplete && freqList.size() <= existing.size()) {
                // Both truncated; existing has at least as many values – keep it.
                return;
            }
            cache.put(fieldName, freqList);
            logger.debug("FreqListCache: replaced entry for {} with larger list ({} > {} values)",
                    fieldName, freqList.size(), existing.size());
        } else {
            cache.put(fieldName, freqList);
            logger.debug("FreqListCache: stored entry for {} ({} values, truncated={})",
                    fieldName, freqList.size(), freqList.isTruncated());
        }
    }

    /** Clear the entire cache. */
    public synchronized void clear() {
        cache.clear();
    }

    /** Returns the current number of entries in the cache (for testing/monitoring). */
    public synchronized int size() {
        return cache.size();
    }
}
