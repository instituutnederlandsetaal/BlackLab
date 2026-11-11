package nl.inl.blacklab.server.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.results.SearchResult;
import nl.inl.blacklab.searches.Search;
import nl.inl.blacklab.server.config.BLSConfig;

public class TestBlsCache {

    private BLSConfig config;
    private BlsCache cache;

    @Before
    public void setUp() {
        config = new BLSConfig();
        config.getCache().setMaxNumberOfJobs(2);
        config.getCache().setTargetFreeMemMegs(0);
        config.getCache().setMinFreeMemForSearchMegs(0);
        config.getPerformance().setMaxConcurrentSearches(1);
        cache = new BlsCache(config, null);
    }

    @After
    public void tearDown() {
        cache.cleanup();
    }

    private BlsCacheEntry<SearchResult> addEntry(long worthiness, boolean done, boolean started) {
        Search<SearchResult> search = mock(Search.class);
        BlsCacheEntry<SearchResult> entry = mock(BlsCacheEntry.class);
        when(entry.search()).thenReturn(search);
        when(entry.worthiness()).thenReturn(worthiness);
        when(entry.isDone()).thenReturn(done);
        when(entry.wasStarted()).thenReturn(started);
        when(entry.isRunning()).thenReturn(started && !done);
        cache.searches.put(search, entry);
        return entry;
    }

    @Test
    public void testEvictsLeastWorthyFinishedEntriesOverLimit() {
        synchronized (cache) {
            BlsCacheEntry<?> mostWorthy = addEntry(30, true, true);
            BlsCacheEntry<?> middle = addEntry(20, true, true);
            BlsCacheEntry<?> leastWorthy = addEntry(10, true, true);

            cache.updateCache();

            assertEquals(2, cache.searches.size());
            assertTrue(cache.containsKey(mostWorthy.search()));
            assertTrue(cache.containsKey(middle.search()));
            assertFalse(cache.containsKey(leastWorthy.search()));
            verify(leastWorthy, never()).cancel(true);
        }
    }

    @Test
    public void testDoesNotEvictRunningOrQueuedEntriesToMeetLimit() {
        synchronized (cache) {
            config.getCache().setMaxNumberOfJobs(1);
            BlsCacheEntry<?> running = addEntry(30, false, true);
            BlsCacheEntry<?> queued = addEntry(20, false, false);
            BlsCacheEntry<?> finished = addEntry(10, true, true);

            cache.updateCache();

            assertEquals(2, cache.searches.size());
            assertTrue(cache.containsKey(running.search()));
            assertTrue(cache.containsKey(queued.search()));
            assertFalse(cache.containsKey(finished.search()));
            verify(running, never()).cancel(true);
            verify(queued, never()).cancel(true);
        }
    }

    @Test
    public void testNegativeLimitAllowsUnlimitedEntries() {
        synchronized (cache) {
            config.getCache().setMaxNumberOfJobs(-1);
            addEntry(30, true, true);
            addEntry(20, true, true);
            addEntry(10, true, true);

            cache.updateCache();

            assertEquals(3, cache.searches.size());
        }
    }

    @Test
    public void testExpiredEntriesStillEvictedBelowLimit() {
        synchronized (cache) {
            BlsCacheEntry<?> expired = addEntry(10, true, true);
            when(expired.timeUnusedMs()).thenReturn(config.getCache().getMaxJobAgeSec() * 1000L + 1);

            cache.updateCache();

            assertTrue(cache.searches.isEmpty());
        }
    }

    @Test
    public void testIndexRemovalHoldsCacheLock() {
        BlsCacheEntry<SearchResult> entry;
        BlackLabIndex index = mock(BlackLabIndex.class);
        Search<SearchResult> search = mock(Search.class);
        when(search.index()).thenReturn(index);
        synchronized (cache) {
            entry = addEntry(10, true, true);
            when(entry.search()).thenAnswer(invocation -> {
                assertTrue(Thread.holdsLock(cache));
                return search;
            });
        }

        cache.removeSearchesForIndex(index);

        synchronized (cache) {
            assertTrue(cache.searches.isEmpty());
        }
        verify(entry).cancel(true);
    }

    @Test
    public void testStatusAndContentsHoldCacheLock() {
        synchronized (cache) {
            BlsCacheEntry<?> entry = addEntry(10, true, true);
            when(entry.wasStarted()).thenAnswer(invocation -> {
                assertTrue(Thread.holdsLock(cache));
                return true;
            });
            when(entry.getInfo(false)).thenAnswer(invocation -> {
                assertTrue(Thread.holdsLock(cache));
                return Map.of();
            });
        }

        assertEquals(1, cache.getStatus().get("numberOfSearches"));
        assertEquals(1, cache.getContents(false).size());
    }
}
