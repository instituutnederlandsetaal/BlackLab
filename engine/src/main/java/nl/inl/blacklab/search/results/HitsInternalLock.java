package nl.inl.blacklab.search.results;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.lucene.MatchInfo;

/**
 * A HitsInternal implementation that locks and can handle huge result sets.
 */
class HitsInternalLock extends HitsInternalNoLock {

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    HitsInternalLock(long initialCapacity) {
        super(initialCapacity);
    }

    @Override
    public void add(int doc, int start, int end, MatchInfo[] matchInfo) {
        assert HitsInternal.debugCheckReasonableHit(doc, start, end);
        this.lock.writeLock().lock();
        try {
            // Don't call super method, this is faster (hot code)
            docs.add(doc);
            starts.add(start);
            ends.add(end);
            if (matchInfo != null)
                matchInfos.add(matchInfo);
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    /**
     * Add the hit to the end of this list, copying the values. The hit object itself is not retained.
     */
    @Override
    public void add(EphemeralHit hit) {
        assert HitsInternal.debugCheckReasonableHit(hit);
        this.lock.writeLock().lock();
        try {
            // Don't call super method, this is faster (hot code)
            docs.add(hit.doc);
            starts.add(hit.start);
            ends.add(hit.end);
            matchInfos.add(hit.matchInfo);
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    /**
     * Add the hit to the end of this list, copying the values. The hit object itself is not retained.
     */
    @Override
    public void add(Hit hit) {
        assert HitsInternal.debugCheckReasonableHit(hit);
        this.lock.writeLock().lock();
        try {
            // Don't call super method, this is faster (hot code)
            docs.add(hit.doc());
            starts.add(hit.start());
            ends.add(hit.end());
            matchInfos.add(hit.matchInfo());
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    public void addAll(HitsInternalLock hits) {
        this.lock.writeLock().lock();
        try {
            hits.lock.readLock().lock();
            try {
                super.addAll(hits);
            } finally {
                hits.lock.readLock().unlock();
            }
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    @Override
    public void addAll(HitsInternal hits) {
        this.lock.writeLock().lock();
        try {
            super.addAll(hits);
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    /**
     * Clear the arrays.
     */
    @Override
    public void clear() {
        lock.writeLock().lock();
        try {
            super.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void withReadLock(Consumer<HitsInternal> cons) {
        lock.readLock().lock();
        try {
            super.withReadLock(cons);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Hit get(long index) {
        lock.readLock().lock();
        try {
            // Don't call super method, this is faster (hot code)
            MatchInfo[] matchInfo = matchInfos.isEmpty() ? null : matchInfos.get((int) index);
            HitImpl hit = new HitImpl(docs.getInt((int) index), starts.getInt((int) index), ends.getInt((int) index),
                    matchInfo);
            assert HitsInternal.debugCheckReasonableHit(hit);
            return hit;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Copy values into the ephemeral hit, for use in a hot loop or somesuch.
     * The intent of this function is to allow retrieving many hits without needing to allocate so many short lived objects.
     * Example:
     *
     * <pre>
     * EphemeralHitImpl h = new EphemeralHitImpl();
     * int size = hits.size();
     * for (int i = 0; i < size; ++i) {
     *     hits.getEphemeral(i, h);
     *     // use h now
     * }
     * </pre>
     */
    @Override
    public void getEphemeral(long index, EphemeralHit h) {
        lock.readLock().lock();
        try {
            h.doc = docs.getInt(index);
            h.start = starts.getInt(index);
            h.end = ends.getInt(index);
            h.matchInfo = matchInfos.isEmpty() ? null : matchInfos.get((int) index);
            assert HitsInternal.debugCheckReasonableHit(h);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int doc(long index) {
        lock.readLock().lock();
        try {
            // Don't call super method, this is faster (hot code)
            return this.docs.getInt(index);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int start(long index) {
        lock.readLock().lock();
        try {
            // Don't call super method, this is faster (hot code)
            return this.starts.getInt(index);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int end(long index) {
        lock.readLock().lock();
        try {
            // Don't call super method, this is faster (hot code)
            return this.ends.getInt(index);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public long size() {
        lock.readLock().lock();
        try {
            return super.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public HitsInternal sort(HitProperty p) {
        this.lock.readLock().lock();
        try {
            return super.sort(p);
        } finally {
            this.lock.readLock().unlock();
        }
    }
}
