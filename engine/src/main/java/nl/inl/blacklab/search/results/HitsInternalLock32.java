package nl.inl.blacklab.search.results;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import nl.inl.blacklab.Constants;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.lucene.MatchInfo;

/**
 * A HitsInternal implementation that locks and can handle up to {@link Constants#JAVA_MAX_ARRAY_SIZE} hits.
 * <p>
 * Maximum size is roughly (but not exactly) 2^31 hits.
 * <p>
 * A test calling {@link #add(int, int, int, MatchInfo[])} millions of times came out to be about
 * 19% faster than {@link HitsInternalLock}. Iteration is about 10x faster.
 * <p>
 * Those percentages are not representative of real-world usage of course, but on
 * huge resultsets this will likely save a few seconds.
 */
class HitsInternalLock32 extends HitsInternalNoLock32 {

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    HitsInternalLock32(int initialCapacity) {
        super(initialCapacity);
    }

    HitsInternalLock32(IntList docs, IntList starts, IntList ends, ObjectList<MatchInfo[]> matchInfos) {
        super(docs, starts, ends, matchInfos);
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

    /** Add the hit to the end of this list, copying the values. The hit object itself is not retained. */
    @Override
    public void add(EphemeralHit hit) {
        assert HitsInternal.debugCheckReasonableHit(hit);
        this.lock.writeLock().lock();
        try {
            // Don't call super method, this is faster (hot code)
            docs.add(hit.doc_);
            starts.add(hit.start_);
            ends.add(hit.end_);
            if (hit.matchInfo != null)
                matchInfos.add(hit.matchInfo);
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    /** Add the hit to the end of this list, copying the values. The hit object itself is not retained. */
    @Override
    public void add(Hit hit) {
        assert HitsInternal.debugCheckReasonableHit(hit);
        this.lock.writeLock().lock();
        try {
            // Don't call super method, this is faster (hot code)
            docs.add(hit.doc());
            starts.add(hit.start());
            ends.add(hit.end());
            if (hit.matchInfo() != null)
                matchInfos.add(hit.matchInfo());
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    public void addAll(HitsInternalLock32 hits) {
        this.lock.writeLock().lock();
        try {
            super.addAll(hits);
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
    public HitImpl get(long index) {
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
            // Don't call super method, this is faster (hot code)
            h.doc_ = docs.getInt((int)index);
            h.start_ = starts.getInt((int)index);
            h.end_ = ends.getInt((int)index);
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
            return this.docs.getInt((int)index);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int start(long index) {
        lock.readLock().lock();
        try {
            // Don't call super method, this is faster (hot code)
            return this.starts.getInt((int)index);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int end(long index) {
        lock.readLock().lock();
        try {
            return this.ends.getInt((int)index);
        } finally {
            // Don't call super method, this is faster (hot code)
            lock.readLock().unlock();
        }
    }

    @Override
    public MatchInfo[] matchInfo(long index) {
        lock.readLock().lock();
        try {
            return this.matchInfos.isEmpty() ? null : this.matchInfos.get((int)index);
        } finally {
            // Don't call super method, this is faster (hot code)
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
