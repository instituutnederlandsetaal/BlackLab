package nl.inl.blacklab.search.results.hits;

import java.util.concurrent.locks.ReentrantReadWriteLock;

import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;

/**
 * A HitsInternal implementation that locks and can handle huge result sets.
 */
class HitsListLock extends HitsListNoLock {

    HitsListLock(AnnotatedField field, MatchInfoDefs matchInfoDefs, long initialCapacity) {
        super(field, matchInfoDefs, initialCapacity);
        lock = new ReentrantReadWriteLock();
    }

    @Override
    public void add(int doc, int start, int end, MatchInfo[] matchInfo) {
        assert HitsListAbstract.debugCheckReasonableHit(doc, start, end);
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
        assert HitsListAbstract.debugCheckReasonableHit(hit);
        this.lock.writeLock().lock();
        try {
            // Don't call super method, this is faster (hot code)
            docs.add(hit.doc_);
            starts.add(hit.start_);
            ends.add(hit.end_);
            if (hit.matchInfos_ != null)
                matchInfos.add(hit.matchInfos_);
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    /** Add the hit to the end of this list, copying the values. The hit object itself is not retained. */
    @Override
    public void add(Hit hit) {
        assert HitsListAbstract.debugCheckReasonableHit(hit);
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
    public void getEphemeral(long index, EphemeralHit h) {
        lock.readLock().lock();
        try {
            // Don't call super method, this is faster (hot code)
            h.doc_ = docs.getInt(index);
            h.start_ = starts.getInt(index);
            h.end_ = ends.getInt(index);
            h.matchInfos_ = matchInfos.isEmpty() ? null : matchInfos.get((int) index);
            assert HitsListAbstract.debugCheckReasonableHit(h);
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

    @Override
    public Hits nonlocking() {
        return new HitsListNoLock(field(), matchInfoDefs(), docs, starts, ends, matchInfos);
    }
}
