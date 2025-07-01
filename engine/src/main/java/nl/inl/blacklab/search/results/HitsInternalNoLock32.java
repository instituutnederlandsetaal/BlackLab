package nl.inl.blacklab.search.results;

import java.text.CollationKey;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import nl.inl.blacklab.Constants;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.PropertyValueString;
import nl.inl.blacklab.search.lucene.MatchInfo;

/**
 * A HitsInternal implementation that does no locking and can handle up to {@link Constants#JAVA_MAX_ARRAY_SIZE} hits.
 * <p>
 * Maximum size is roughly (but not exactly) 2^31 hits.
 * <p>
 * This means it is safe to fill this object in one thread, then
 * use it from many threads as long as it is not modified anymore.
 * <p>
 * A test calling {@link #add(int, int, int, MatchInfo[])} millions of times came out to be about 40% faster than
 * {@link HitsInternalLock32}, and also about 40% faster than {@link HitsInternalNoLock}.
 * <p>
 * These tests are not representative of real-world usage, but on huge result sets this will
 * likely save a few seconds.
 */
class HitsInternalNoLock32 implements HitsInternalMutable {

    private class Iterator implements HitsInternal.Iterator {
        private int pos = 0;
        private final EphemeralHit hit = new EphemeralHit();

        public Iterator() {
        }

        @Override
        public boolean hasNext() {
            // Since this iteration method is not thread-safe anyway, use the direct array to prevent repeatedly acquiring the read lock
            return HitsInternalNoLock32.this.docs.size() > pos;
        }

        @Override
        public EphemeralHit next() {
            try {
                hit.doc = HitsInternalNoLock32.this.docs.getInt(pos);
                hit.start = HitsInternalNoLock32.this.starts.getInt(pos);
                hit.end = HitsInternalNoLock32.this.ends.getInt(pos);
                hit.matchInfo = HitsInternalNoLock32.this.matchInfos.isEmpty() ? null :
                        HitsInternalNoLock32.this.matchInfos.get(pos);
                ++pos;
                return hit;
            } catch (IndexOutOfBoundsException e) {
                throw new NoSuchElementException();
            }
        }
    }

    protected final IntList docs;
    protected final IntList starts;
    protected final IntList ends;
    protected final ObjectList<MatchInfo[]> matchInfos;

    HitsInternalNoLock32() {
        this(-1);
    }

    HitsInternalNoLock32(int initialCapacity) {
        if (initialCapacity < 0) {
            // Use default initial capacities
            this.docs = new IntArrayList();
            this.starts = new IntArrayList();
            this.ends = new IntArrayList();
            this.matchInfos = new ObjectArrayList<>();
        } else {
            this.docs = new IntArrayList(initialCapacity);
            this.starts = new IntArrayList(initialCapacity);
            this.ends = new IntArrayList(initialCapacity);
            this.matchInfos = new ObjectArrayList<>(initialCapacity);
        }
    }

    HitsInternalNoLock32(IntList docs, IntList starts, IntList ends, ObjectList<MatchInfo[]> matchInfos) {
        if (docs == null || starts == null || ends == null)
            throw new NullPointerException();
        if (docs.size() != starts.size() || docs.size() != ends.size() || (matchInfos != null && matchInfos.size() != docs.size()))
            throw new IllegalArgumentException("Passed differently sized hit component arrays to Hits object");

        this.docs = docs;
        this.starts = starts;
        this.ends = ends;
        this.matchInfos = matchInfos == null ? new ObjectArrayList<>() : matchInfos;
        assert HitsInternal.debugCheckAllReasonable(this);
    }

    @Override
    public void add(int doc, int start, int end, MatchInfo[] matchInfo) {
        assert HitsInternal.debugCheckReasonableHit(doc, start, end);
        docs.add(doc);
        starts.add(start);
        ends.add(end);
        if (matchInfo != null) {
            matchInfos.add(matchInfo);
        } else {
            // Either all hits have matchInfo, or none do.
            assert matchInfos.isEmpty() : "Cannot have some hits with matchInfo and some without";
        }
    }

    /** Add the hit to the end of this list, copying the values. The hit object itself is not retained. */
    @Override
    public void add(EphemeralHit hit) {
        assert HitsInternal.debugCheckReasonableHit(hit);
        docs.add(hit.doc);
        starts.add(hit.start);
        ends.add(hit.end);
        if (hit.matchInfo != null) {
            matchInfos.add(hit.matchInfo);
        } else {
            // Either all hits have matchInfo, or none do.
            assert matchInfos.isEmpty() : "Cannot have some hits with matchInfo and some without";
        }
    }

    /** Add the hit to the end of this list, copying the values. The hit object itself is not retained. */
    @Override
    public void add(Hit hit) {
        assert HitsInternal.debugCheckReasonableHit(hit);
        docs.add(hit.doc());
        starts.add(hit.start());
        ends.add(hit.end());
        if (hit.matchInfo() != null) {
            matchInfos.add(hit.matchInfo());
        } else {
            // Either all hits have matchInfo, or none do.
            assert matchInfos.isEmpty() : "Cannot have some hits with matchInfo and some without";
        }
    }

    public void addAll(HitsInternalNoLock32 hits) {
        assert HitsInternal.debugCheckAllReasonable(hits);
        docs.addAll(hits.docs);
        starts.addAll(hits.starts);
        ends.addAll(hits.ends);
        matchInfos.addAll(hits.matchInfos);
        assert matchInfos.isEmpty() || matchInfos.size() == docs.size() : "Wrong number of matchInfos";
    }

    public void addAll(HitsInternal hits) {
        hits.withReadLock(hr -> {
            for (EphemeralHit h: hr) {
                assert HitsInternal.debugCheckReasonableHit(h);
                docs.add(h.doc);
                starts.add(h.start);
                ends.add(h.end);
                if (h.matchInfo != null) {
                    matchInfos.add(h.matchInfo);
                } else {
                    // Either all hits have matchInfo, or none do.
                    assert matchInfos.isEmpty() : "Cannot have some hits with matchInfo and some without";
                }
            }
        });
    }

    /**
     * Clear the arrays.
     */
    public void clear() {
        docs.clear();
        starts.clear();
        ends.clear();
        matchInfos.clear();
    }

    @Override
    public void withReadLock(Consumer<HitsInternal> cons) {
        cons.accept(this);
    }

    @Override
    public HitImpl get(long index) {
        MatchInfo[] matchInfo = matchInfos.isEmpty() ? null : matchInfos.get((int) index);
        HitImpl hit = new HitImpl(docs.getInt((int) index), starts.getInt((int) index), ends.getInt((int) index),
                matchInfo);
        assert HitsInternal.debugCheckReasonableHit(hit);
        return hit;
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
        h.doc = docs.getInt((int)index);
        h.start = starts.getInt((int)index);
        h.end = ends.getInt((int)index);
        h.matchInfo = matchInfos.isEmpty() ? null : matchInfos.get((int) index);
        assert HitsInternal.debugCheckReasonableHit(h);
    }

    @Override
    public int doc(long index) {
        return docs.getInt((int)index);
    }

    @Override
    public int start(long index) {
        return starts.getInt((int)index);
    }

    @Override
    public int end(long index) {
        return ends.getInt((int)index);
    }

    @Override
    public MatchInfo[] matchInfo(long index) {
        return matchInfos.isEmpty() ? null : matchInfos.get((int) index);
    }

    @Override
    public long size() {
        return docs.size();
    }

    public IntIterator docsIterator() {
        return docs.intIterator();
    }

    /** Note: iterating does not lock the arrays, to do that, it should be performed in a {@link #withReadLock} callback. */
    @Override
    public HitsInternal.Iterator iterator() {
        return new Iterator();
    }

    @Override
    public HitsInternal sort(HitProperty p) {
        int[] indices = new int[docs.size()];
        for (int i = 0; i < indices.length; ++i)
            indices[i] = i;

        IntArrays.quickSort(indices, p::compare);

        // Sort the indices using the given HitProperty
        if (p.getValueType() == PropertyValueString.class) {
            // Collator.compare() is synchronized and therefore slow.
            // It is faster to calculate all the collationkeys first, then parallel sort them.
            CollationKey[] sortValues = new CollationKey[docs.size()];
            for (int i = 0; i < sortValues.length; ++i)
                sortValues[i] = PropertyValue.collator.getCollationKey(p.get(i).toString());
            IntArrays.parallelQuickSort(indices, (a, b) -> sortValues[a].compareTo(sortValues[b]));
        } else {
            IntArrays.parallelQuickSort(indices, p::compare);
        }

        HitsInternalMutable r = HitsInternal.create(docs.size(), false, false);
        if (matchInfos.isEmpty()) {
            for (int index: indices) {
                r.add(docs.getInt(index), starts.getInt(index), ends.getInt(index), null);
            }
        } else {
            for (int index: indices) {
                r.add(docs.getInt(index), starts.getInt(index), ends.getInt(index), matchInfos.get(index));
            }
        }
        return r;
    }
}
