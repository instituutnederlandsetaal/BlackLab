package nl.inl.util;

import java.util.ArrayList;
import java.util.List;

import nl.inl.blacklab.exceptions.BlackLabException;

/**
 * Keeps a simple pool of free resource objects.
 *
 * This is useful when it's relatively expensive to create an object, and we
 * often need one for a short time.
 *
 * Note that this is a very basic resource pool.
 * 
 * @param <T> resource type to pool
 */
public abstract class SimpleResourcePool<T> {
    /**
     * The maximum number of unused resource objects in the free pool at any one
     * time. Once the pool is full, additional resource objects are dropped to be
     * garbage-collected instead of returned to the pool.
     */
    private final int maxFreePoolSize;

    /**
     * Switch to disable pooling.
     */
    private boolean neverPool;

    /** List of free resource objects */
    private final List<T> freePool;

    /**
     * Initializes the free pool.
     *
     * @param maxFreePoolSize size of the resource pool. If 0, disables pooling.
     */
    protected SimpleResourcePool(int maxFreePoolSize) {
        this.maxFreePoolSize = maxFreePoolSize;
        freePool = new ArrayList<>();
        neverPool = (maxFreePoolSize == 0);
    }

    /**
     * Cleanup the resources in the free pool.
     */
    public void close() {
        clear();
    }

    /**
     * Destroy all objects in the free pool.
     */
    public synchronized void clear() {
        for (T resource : freePool) {
            destroyResource(resource);
        }
    }

    /**
     * Create a new resource object.
     * 
     * @return the new resource.
     */
    public abstract T createResource();

    /**
     * Destroy a resource object.
     * 
     * @param resource the resource to destroy.
     */
    public void destroyResource(T resource) {
        // Default: do nothing
    }

    /**
     * Sets whether or not we want to do any pooling at all. If not, always creates
     * a new resource object and never returns it to the free pool.
     *
     * @param poolingEnabled if true, pool free resources.
     */
    public synchronized void setPoolingEnabled(boolean poolingEnabled) {
        this.neverPool = !poolingEnabled;
    }

    /**
     * Retrieves a resource object from the free pool, or creates a new one if none
     * are available
     *
     * @return the resource object
     */
    public synchronized T acquire() {
        try {
            if (neverPool || freePool.isEmpty()) {
                return createResource();
            }
            return freePool.remove(0);
        } catch (Exception e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    /**
     * Returns a resource object to the free pool.
     *
     */
    public synchronized void release(T resource) {
        if (neverPool)
            return;
        synchronized (this) {
            if (freePool.size() < maxFreePoolSize) {
                freePool.add(resource);
            } else {
                destroyResource(resource);
            }
        }
    }

}
