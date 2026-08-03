package nl.inl.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Generic manager for caching and managing resources that may be expensive to instantiate.
 *
 * Instantiation and finalization are configurable via function arguments.
 * Resources are cached and managed until removed by age or count limits.
 *
 * @param <T> type of object being cached, e.g. a zip file handle
 * @param <P> type of parameter used to identify/create the object, e.g. a path to the zip file
 */
public class ObjectCache<T, P> {

    /** How to create a new object given its identity */
    private final Function<P, T> creator;

    /** How to finalize an object when removed from cache (default does nothing) */
    private final Consumer<T> finalizer;

    /** If object hasn't been accessed for this amount of time, remove it from the cache */
    private long maxAgeMs;

    /** If cache exceeds this size, remove older objects */
    private int maxCacheSize;

    /** The cache */
    private final Map<P, Handle> openHandles = new LinkedHashMap<>();

    public ObjectCache(Function<P, T> creator, int maxCacheSize, int maxAgeSec) {
        this(creator, (T obj) -> {
            // Default finalizer does nothing
        }, maxCacheSize, maxAgeSec);
    }

    public ObjectCache(Function<P, T> creator, Consumer<T> finalizer, int maxCacheSize, int maxAgeSec) {
        this.creator = creator;
        this.finalizer = finalizer;
        this.setMaxAgeSec(maxAgeSec);
        this.setMaxCacheSize(maxCacheSize);
    }

    public void setMaxAgeSec(long maxAgeSec) {
        this.maxAgeMs = maxAgeSec * 1000L;
    }

    public void setMaxCacheSize(int maxCacheSize) {
        this.maxCacheSize = maxCacheSize;
    }

    private class Handle implements Comparable<Handle> {
        public final P key;
        public final T object;
        public long lastUsed;
        public int acquisitionCount = 0;

        public Handle(P key, T object) {
            this.key = key;
            this.object = object;
            this.lastUsed = System.currentTimeMillis();
        }

        @Override
        public int compareTo(Handle o) {
            return Long.compare(lastUsed, o.lastUsed);
        }

        public void markUsed() {
            lastUsed = System.currentTimeMillis();
        }

        public long timeSinceLastUsed() {
            return System.currentTimeMillis() - lastUsed;
        }

        public void finalizeHandle() {
            finalizer.accept(object);
        }
    }

    /** Acquire a handle for the given parameter, creating and caching if needed. */
    public T acquire(P param) {
        synchronized (openHandles) {
            Handle h = openHandles.get(param);
            if (h == null) {
                h = new Handle(param, creator.apply(param));
                openHandles.put(param, h);
            }
            h.markUsed();
            h.acquisitionCount++;
            removeEntriesIfRequired();
            return h.object;
        }
    }

    /** Release a handle. Decrement acquisition count. */
    public void releaseObject(T object) {
        synchronized (openHandles) {
            for (Handle h : openHandles.values()) {
                if (h.object == object) {
                    if (h.acquisitionCount > 0) {
                        h.acquisitionCount--;
                    }
                    break;
                }
            }
            removeEntriesIfRequired();
        }
    }

    /** Close all cached handles that are not acquired. */
    public void closeAll() {
        synchronized (openHandles) {
            Iterator<Handle> it = openHandles.values().iterator();
            while (it.hasNext()) {
                Handle h = it.next();
                if (h.acquisitionCount == 0) {
                    h.finalizeHandle();
                    it.remove();
                }
            }
        }
    }

    /** Remove old or excess handles from cache, but only if not acquired. */
    private void removeEntriesIfRequired() {
        List<Handle> handles = new ArrayList<>(openHandles.values());
        for (Handle h: handles) {
            if (h.acquisitionCount == 0 && h.timeSinceLastUsed() > maxAgeMs) {
                openHandles.remove(h.key);
                h.finalizeHandle();
            }
        }
        // Remove least recently used handles if over max, but only those not acquired
        handles = new ArrayList<>(openHandles.values());
            handles.sort(Comparator.naturalOrder());
        int removable = openHandles.size() - maxCacheSize;
        Iterator<Handle> cacheIt = handles.iterator();
        while (removable > 0 && cacheIt.hasNext()) {
            Handle h = cacheIt.next();
            if (h.acquisitionCount == 0) {
                openHandles.remove(h.key);
                h.finalizeHandle();
                removable--;
            }
        }
    }
}
