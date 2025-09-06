package nl.inl.blacklab.search.results.hits;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.function.Consumer;

import org.apache.lucene.queries.spans.Spans;

import com.ibm.icu.text.CollationKey;

import it.unimi.dsi.fastutil.ints.IntArrays;
import nl.inl.blacklab.Constants;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValueString;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;

public abstract class HitsListAbstract extends HitsAbstract implements HitsMutable {

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
        assert doc != Spans.NO_MORE_DOCS : "Hit doc id must not equal NO_MORE_DOCS";
        assert start >= 0 : "Hit start must be non-negative, is " + start;
        assert end >= 0 : "Hit end must be non-negative, is " + start;
        assert start <= end : "Hit start " + start + " > end " + end;
        assert start != Spans.NO_MORE_POSITIONS : "Hit start must not equal NO_MORE_POSITIONS";
        assert end != Spans.NO_MORE_POSITIONS : "Hit end must not equal NO_MORE_POSITIONS";
        return true;
    }

    /** Field these hits came from */
    final AnnotatedField field;

    /** Match info definitions for these hits */
    MatchInfoDefs matchInfoDefs;

    /** Lock (for the classes that do locking; otherwise null) */
    ReadWriteLock lock;

    HitsListAbstract(AnnotatedField field, MatchInfoDefs matchInfoDefs) {
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
    public long countDocs() {
        if (lock != null) {
            lock.readLock().lock();
            try {
                return countDocsNoLock();
            } finally {
                lock.readLock().unlock();
            }
        } else {
            return countDocsNoLock();
        }
    }

    abstract long countDocsNoLock();

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
                return super.sublist(first, length);
            } finally {
                lock.readLock().unlock();
            }
        } else {
            return super.sublist(first, length);
        }
    }

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
            IntArrays.parallelQuickSort(indices, (a, b) -> sortValues[a].compareTo(sortValues[b]));
        } else {
            IntArrays.parallelQuickSort(indices, sortBy::compare);
        }

        HitsMutable r = HitsMutable.create(field(), matchInfoDefs(), size, false, false);
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
    public MatchInfoDefs matchInfoDefs() {
        return matchInfoDefs;
    }

    @Override
    public void setMatchInfoDefs(MatchInfoDefs matchInfoDefs) {
        this.matchInfoDefs = matchInfoDefs;
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
    public void addAllConvertDocBase(Hits segmentHits, int docBase) {
        if (this.lock != null) {
            this.lock.writeLock().lock();
            try {
                addAllConvertDocBaseNoLock(segmentHits, docBase);
            } finally {
                this.lock.writeLock().unlock();
            }
        } else {
            addAllConvertDocBaseNoLock(segmentHits, docBase);
        }
    }

    public abstract void addAllConvertDocBaseNoLock(Hits segmentHits, int docBase);
}
