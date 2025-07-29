package nl.inl.blacklab.search.results;

import java.util.concurrent.locks.ReentrantReadWriteLock;

import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import nl.inl.blacklab.Constants;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;

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

    HitsInternalLock32(AnnotatedField field, MatchInfoDefs matchInfoDefs, int initialCapacity) {
        super(field, matchInfoDefs, initialCapacity);
        lock = new ReentrantReadWriteLock();
    }

    HitsInternalLock32(AnnotatedField field, MatchInfoDefs matchInfoDefs, IntList docs, IntList starts, IntList ends, ObjectList<MatchInfo[]> matchInfos) {
        super(field, matchInfoDefs, docs, starts, ends, matchInfos);
        lock = new ReentrantReadWriteLock();
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
            if (hit.matchInfos() != null)
                matchInfos.add(hit.matchInfos());
        } finally {
            this.lock.writeLock().unlock();
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
            // Don't call super method, this is faster (hot code)
            return this.ends.getInt((int)index);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public MatchInfo[] matchInfos(long index) {
        lock.readLock().lock();
        try {
            // Don't call super method, this is faster (hot code)
            return this.matchInfos.isEmpty() ? null : this.matchInfos.get((int)index);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public MatchInfo matchInfo(long index, int matchInfoIndex) {
        lock.readLock().lock();
        try {
            // Don't call super method, this is faster (hot code)
            if (matchInfos.isEmpty())
                return null;
            MatchInfo[] matchInfo = matchInfos.get((int) index);
            return matchInfoIndex < matchInfo.length ? matchInfo[matchInfoIndex] : null;
        } finally {
            lock.readLock().unlock();
        }
    }
}
