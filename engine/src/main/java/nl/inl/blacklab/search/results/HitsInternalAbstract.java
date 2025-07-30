package nl.inl.blacklab.search.results;

import java.text.CollationKey;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.Consumer;

import it.unimi.dsi.fastutil.ints.IntArrays;
import nl.inl.blacklab.Constants;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.PropertyValueString;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;

public abstract class HitsInternalAbstract implements HitsInternalMutable {

    final AnnotatedField field;

    MatchInfoDefs matchInfoDefs;

    /** Lock (for the classes that do locking; otherwise null) */
    ReadWriteLock lock;

    HitsInternalAbstract(AnnotatedField field, MatchInfoDefs matchInfoDefs) {
        this.field = field;
        this.matchInfoDefs = matchInfoDefs == null ? MatchInfoDefs.EMPTY : matchInfoDefs;
    }

    @Override
    public void clear() {
        if (lock != null) {
            lock.writeLock().lock();
            try {
                clearNoLock();
            } finally {
                lock.writeLock().unlock();
            }
        } else {
            clearNoLock();
        }
    }

    abstract void clearNoLock();

    @Override
    public void addAll(HitsInternal hits) {
        this.lock.writeLock().lock();
        try {
            addAllNoLock(hits);
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    abstract void addAllNoLock(HitsInternal hits);

    @Override
    public long size() {
        if (lock != null) {
            lock.readLock().lock();
            try {
                return sizeNoLock();
            } finally {
                lock.readLock().unlock();
            }
        } else {
            return sizeNoLock();
        }
    }

    abstract long sizeNoLock();

    @Override
    public HitsInternal sorted(HitProperty p) {
        if (lock != null) {
            lock.readLock().lock();
            try {
                return sortedNoLock(p);
            } finally {
                lock.readLock().unlock();
            }
        } else {
            return sortedNoLock(p);
        }
    }

    abstract HitsInternal sortedNoLock(HitProperty p);

    @Override
    public HitsSimple sublist(long first, long windowSize) {
        if (lock != null) {
            lock.readLock().lock();
            try {
                return sublistNoLock(first, windowSize);
            } finally {
                lock.readLock().unlock();
            }
        } else {
            return sublistNoLock(first, windowSize);
        }
    }

    public HitsSimple sublistNoLock(long start, long windowSize) {
        long end = start + windowSize;
        if (end > size())
            end = size();
        if (start < 0 || end < 0 || start > end)
            throw new IndexOutOfBoundsException("Window start " + start + " with size " + windowSize + " is out of bounds (size: " + size() + ")");
        HitsInternalMutable window = HitsInternal.create(field, matchInfoDefs, end - start, false, false);
        EphemeralHit h = new EphemeralHit();
        for (long i = start; i < end; ++i) {;
            getEphemeral(i, h);
            window.add(h);
        }
        return window;
    }

    public HitsInternalMutable sort32(HitProperty p) {
        if (size() > Constants.JAVA_MAX_ARRAY_SIZE)
            throw new IllegalArgumentException("This method cannot sort more than " + Constants.JAVA_MAX_ARRAY_SIZE + " hits at once");
        p = p.copyWith(this);
        int size = (int)size();
        int[] indices = new int[size];
        for (int i = 0; i < indices.length; ++i)
            indices[i] = i;

        IntArrays.quickSort(indices, p::compare);

        // Sort the indices using the given HitProperty
        if (p.getValueType() == PropertyValueString.class) {
            // Collator.compare() is synchronized and therefore slow.
            // It is faster to calculate all the collationkeys first, then parallel sort them.
            CollationKey[] sortValues = new CollationKey[size];
            for (int i = 0; i < sortValues.length; ++i)
                sortValues[i] = PropertyValue.collator.getCollationKey(p.get(i).toString());
            IntArrays.parallelQuickSort(indices, (a, b) -> sortValues[a].compareTo(sortValues[b]));
        } else {
            IntArrays.parallelQuickSort(indices, p::compare);
        }

        HitsInternalMutable r = HitsInternal.create(field(), matchInfoDefs(), size, false, false);
        for (int index: indices) {
            EphemeralHit hit = new EphemeralHit();
            getEphemeral(index, hit);
            r.add(hit);
        }
        return r;
    }

    @Override
    public AnnotatedField field() {
        return field;
    }

    @Override
    public BlackLabIndex index() {
        return field.index();
    }

    @Override
    public MatchInfoDefs matchInfoDefs() {
        return matchInfoDefs;
    }

    @Override
    public void setMatchInfoDefs(MatchInfoDefs matchInfoDefs) {
        this.matchInfoDefs = matchInfoDefs;
    }

    @Override
    public void withReadLock(Consumer<HitsInternal> cons) {
        if (lock != null) {
            lock.readLock().lock();
            try {
                cons.accept(this);
            } finally {
                lock.readLock().unlock();
            }
        } else {
            cons.accept(this);
        }
    }

    @Override
    public HitsSimple getFinishedHits() {
        return lock != null ? nonlocking() : this;
    }

}
