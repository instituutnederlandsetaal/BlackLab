package nl.inl.blacklab.search.results.hits;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.Consumer;

import org.apache.lucene.queries.spans.Spans;
import org.apache.lucene.search.DocIdSetIterator;

import com.ibm.icu.text.CollationKey;

import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntComparator;
import nl.inl.blacklab.Constants;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValueString;

/** Abstract base class for the HitsList* classes. Takes care of (some) optional locking. */
public abstract class HitsListAbstract extends HitsAbstract implements HitsMutable {

    protected static final String ERR_WRONG_NUMBER_OF_MATCH_INFOS = "Wrong number of matchInfos";

    static boolean debugCheckAllReasonable(Hits hits) {
        for (EphemeralHit hit: hits) {
            assert debugCheckReasonableHit(hit);
        }
        return true;
    }

    static boolean debugCheckReasonableHit(Hit h) {
        return debugCheckReasonableHit(h.doc(), h.start(), h.end());
    }

    static boolean debugCheckReasonableHit(int doc, int start, int end) {
        assert doc >= 0 : "Hit doc id must be non-negative, is " + doc;
        assert doc != DocIdSetIterator.NO_MORE_DOCS : "Hit doc id must not equal NO_MORE_DOCS";
        assert start >= 0 : "Hit start must be non-negative, is " + start;
        assert end >= 0 : "Hit end must be non-negative, is " + start;
        assert start <= end : "Hit start " + start + " > end " + end;
        assert start != Spans.NO_MORE_POSITIONS : "Hit start must not equal NO_MORE_POSITIONS";
        assert end != Spans.NO_MORE_POSITIONS : "Hit end must not equal NO_MORE_POSITIONS";
        return true;
    }

    final HitsContext context;

    /** Lock (for the classes that do locking; otherwise null) */
    ReadWriteLock lock;

    HitsListAbstract(Hits.HitsContext context) {
        this.context = context;
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
    public void addAll(Hits hits) {
        if (this.lock != null) {
            this.lock.writeLock().lock();
            try {
                addAllNoLock(hits);
            } finally {
                this.lock.writeLock().unlock();
            }
        } else {
            addAllNoLock(hits);
        }
    }

    void addAllNoLock(Hits hits) {
        // Fallback: just add hits one by one.
        // (overrides implement a more efficient version)
        for (EphemeralHit hit: hits) {
            add(hit);
        }
    }

    @Override
    public boolean sizeAtLeast(long minSize) {
        return size() >= minSize;
    }

    @Override
    public long sizeSoFar() {
        return size();
    }

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
    public int countDocs(long startIndex, long endIndex) {
        if (lock != null) {
            lock.readLock().lock();
            try {
                return countDocsNoLock(startIndex, endIndex);
            } finally {
                lock.readLock().unlock();
            }
        } else {
            return countDocsNoLock(startIndex, endIndex);
        }
    }

    abstract int countDocsNoLock(long startIndex, long endIndex);

    @Override
    public Hits sorted(HitProperty sortBy) {
        if (lock != null) {
            lock.readLock().lock();
            try {
                return sortedNoLock(sortBy);
            } finally {
                lock.readLock().unlock();
            }
        } else {
            return sortedNoLock(sortBy);
        }
    }

    abstract Hits sortedNoLock(HitProperty p);

    @Override
    public Hits sublist(long first, long length) {
        if (lock != null) {
            lock.readLock().lock();
            try {
                return sublistNoLock(first, length);
            } finally {
                lock.readLock().unlock();
            }
        } else {
            return sublistNoLock(first, length);
        }
    }

    private Hits sublistNoLock(long start, long length) {
        if (start < 0)
            throw new IndexOutOfBoundsException("Window start must be non-negative, but was " + start);
        if (length < 0)
            throw new IllegalArgumentException("Window size must be non-negative, but was " + length);
        if (start > size() || length == 0)
            return Hits.empty(context());
        long end = start + length;
        if (end > size())
            end = size();
        HitsMutable window = HitsMutable.create(context(), end - start, end - start, false);
        fillWindow(window, start, end);
        return window;
    }

    protected abstract void fillWindow(HitsMutable window, long start, long end);

    /** Sort a list of hits less than 2 billion long. */
    HitsMutable sort32(HitProperty sortBy) {
        if (size() > Constants.JAVA_MAX_ARRAY_SIZE)
            throw new IllegalArgumentException("This method cannot sort more than " + Constants.JAVA_MAX_ARRAY_SIZE + " hits at once");
        assert sortBy.getContext().hits() == this : "HitProperty context hits object is not this hits object";
        int size = (int)size();
        int[] indices = new int[size];
        for (int i = 0; i < indices.length; ++i)
            indices[i] = i;

        // Sort the indices using the given HitProperty
        if (sortBy.getValueType() == PropertyValueString.class) {
            // Collator.compare() is synchronized and therefore slow.
            // It is faster to calculate all the collationkeys first, then parallel sort them.
            CollationKey[] sortValues = new CollationKey[size];
            for (int i = 0; i < sortValues.length; ++i) {
                sortValues[i] = sortBy.getCollationKey(i);
            }
            IntComparator cmp = sortBy.isReverse() ?
                    (a, b) -> sortValues[b].compareTo(sortValues[a]) :
                    (a, b) -> sortValues[a].compareTo(sortValues[b]);
            IntArrays.parallelQuickSort(indices, cmp);
        } else {
            IntArrays.parallelQuickSort(indices, sortBy::compare);
        }

        HitsMutable r = HitsMutable.create(context(),
                size, false, false);
        for (int index: indices) {
            EphemeralHit hit = new EphemeralHit();
            getEphemeral(index, hit);
            r.add(hit);
        }
        return r;
    }

    @Override
    public HitsContext context() {
        return context;
    }

    public void withReadLock(Consumer<Hits> cons) {
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
    public Hits getStatic() {
        return lock != null ? nonlocking() : this;
    }

    /**
     * Return a non-locking version of this HitsInternal.
     *
     * CAUTION: this will use the same lists as this HitsInternal,
     * it just won't use any locking. Make sure no locking is required
     * anymore (for example, because all the hits have been added).
     */
    Hits nonlocking() {
        return this;
    }

    @Override
    public void addAllConvertDocBase(Hits segmentHits) {
        if (this.lock != null) {
            this.lock.writeLock().lock();
            try {
                addAllConvertDocBaseNoLock(segmentHits);
            } finally {
                this.lock.writeLock().unlock();
            }
        } else {
            addAllConvertDocBaseNoLock(segmentHits);
        }
    }

    public abstract void addAllConvertDocBaseNoLock(Hits segmentHits);

}
