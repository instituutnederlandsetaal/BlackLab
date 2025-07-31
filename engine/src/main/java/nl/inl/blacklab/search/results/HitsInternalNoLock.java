package nl.inl.blacklab.search.results;

import java.text.CollationKey;
import java.util.Iterator;
import java.util.NoSuchElementException;

import it.unimi.dsi.fastutil.BigArrays;
import it.unimi.dsi.fastutil.ints.IntBigArrayBigList;
import it.unimi.dsi.fastutil.ints.IntBigList;
import it.unimi.dsi.fastutil.longs.LongBigArrays;
import it.unimi.dsi.fastutil.objects.ObjectBigArrayBigList;
import it.unimi.dsi.fastutil.objects.ObjectBigArrays;
import it.unimi.dsi.fastutil.objects.ObjectBigList;
import nl.inl.blacklab.Constants;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.PropertyValueString;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;

/**
 * A HitsInternal implementation that does no locking and can handle huge result sets.
 * <p>
 * This means it is safe to fill this object in one thread, then
 * use it from many threads as long as it is not modified anymore.
 * <p>
 * A test calling {@link #add(int, int, int, MatchInfo[])} millions of times came out to be about 11% faster than
 * {@link HitsInternalLock}. That is not representative of real-world usage of course, but on huge
 * resultsets this will likely save a few seconds.
 * <p>
 * These tests are not representative of real-world usage, but on huge result sets this will
 * likely save a few seconds.
 */
class HitsInternalNoLock extends HitsInternalAbstract {

    /**
     * Class to iterate over hits.
     * <p>
     * NOTE: contrary to expectation, implementing this class using iterators
     * over docs, starts and ends makes it slower.
     */
    private class HitIterator implements Iterator<EphemeralHit> {
        private long pos = 0;

        private final EphemeralHit hit = new EphemeralHit();

        public HitIterator() {
        }

        @Override
        public boolean hasNext() {
            // Since this iteration method is not thread-safe anyway, use the direct array to prevent repeatedly acquiring the read lock
            return docs.size64() > pos;
        }

        @Override
        public EphemeralHit next() {
            try {
                hit.doc_ = docs.getInt(pos);
                hit.start_ = starts.getInt(pos);
                hit.end_ = ends.getInt(pos);
                hit.matchInfo = matchInfos.isEmpty() ? null : matchInfos.get(pos);
                ++pos;
                return hit;
            } catch (IndexOutOfBoundsException e) {
                throw new NoSuchElementException();
            }
        }
    }

    protected final IntBigList docs;
    protected final IntBigList starts;
    protected final IntBigList ends;
    protected final ObjectBigList<MatchInfo[]> matchInfos;

    HitsInternalNoLock(AnnotatedField field, MatchInfoDefs matchInfoDefs, long initialCapacity) {
        super(field, matchInfoDefs);
        if (initialCapacity < 0) {
            // Use default initial capacities
            docs = new IntBigArrayBigList();
            starts = new IntBigArrayBigList();
            ends = new IntBigArrayBigList();
            matchInfos = new ObjectBigArrayBigList<>();
        } else {
            docs = new IntBigArrayBigList(initialCapacity);
            starts = new IntBigArrayBigList(initialCapacity);
            ends = new IntBigArrayBigList(initialCapacity);
            matchInfos = new ObjectBigArrayBigList<>(initialCapacity);
        }
    }

    /**
     * Create a HitsInternalNoLock with these lists.
     *
     * The lists are referenced, not copied. Used by HitsInternal.nonlocking().
     *
     * @param field          field
     * @param matchInfoDefs  match info definitions
     * @param docs          document ids
     * @param starts        hit start positions
     * @param ends          hit end positions
     * @param matchInfos    match info for each hit, or empty if no match info
     */
    HitsInternalNoLock(AnnotatedField field, MatchInfoDefs matchInfoDefs, IntBigList docs, IntBigList starts, IntBigList ends, ObjectBigList<MatchInfo[]> matchInfos) {
        super(field, matchInfoDefs);
        if (docs == null || starts == null || ends == null)
            throw new NullPointerException();
        if (docs.size64() != starts.size64() || docs.size64() != ends.size64() || ((matchInfos != null && !matchInfos.isEmpty()) && matchInfos.size64() != docs.size64()))
            throw new IllegalArgumentException("Passed differently sized hit component arrays to Hits object");
        this.docs = docs;
        this.starts = starts;
        this.ends = ends;
        this.matchInfos = matchInfos;
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
        docs.add(hit.doc_);
        starts.add(hit.start_);
        ends.add(hit.end_);
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
        if (hit.matchInfos() != null) {
            matchInfos.add(hit.matchInfos());
        } else {
            // Either all hits have matchInfo, or none do.
            assert matchInfos.isEmpty() : "Cannot have some hits with matchInfo and some without";
        }
    }

    @Override
    public void addAllNoLock(HitsInternal hits) {
        if (hits instanceof HitsInternalLock hil) {
            // We have to lock this.
            hil.withReadLock(hil2 -> {
                addAllNoLockSource(hil);
            });
        } else if (hits instanceof HitsInternalLock32 hil32) {
            // We have to lock this.
            hits.withReadLock(hil32_2 -> {
                addAllNoLock32(hil32);
            });
        } else if (hits instanceof HitsInternalNoLock hinl) {
            // No need to lock
            addAllNoLockSource(hinl);
        } else if (hits instanceof HitsInternalNoLock32 hinl32) {
            // No need to lock
            addAllNoLock32(hinl32);
        }
    }

    private void addAllNoLockSource(HitsInternalNoLock hil) {
        assert HitsInternal.debugCheckAllReasonable(hil);
        docs.addAll(hil.docs);
        starts.addAll(hil.starts);
        ends.addAll(hil.ends);
        matchInfos.addAll(hil.matchInfos);
        assert matchInfos.isEmpty() || matchInfos.size64() == docs.size64() : "Wrong number of matchInfos";
    }

    private void addAllNoLock32(HitsInternalNoLock32 hil) {
        assert HitsInternal.debugCheckAllReasonable(hil);
        docs.addAll(hil.docs);
        starts.addAll(hil.starts);
        ends.addAll(hil.ends);
        matchInfos.addAll(hil.matchInfos);
        assert matchInfos.isEmpty() || matchInfos.size64() == docs.size64() : "Wrong number of matchInfos";
    }

    /**
     * Clear the arrays.
     */
    @Override
    public void clearNoLock() {
        docs.clear();
        starts.clear();
        ends.clear();
        matchInfos.clear();
    }

    @Override
    public Hit get(long index) {
        MatchInfo[] matchInfo = matchInfos.isEmpty() ? null : matchInfos.get(index);
        Hit hit = new HitImpl(docs.getInt(index), starts.getInt(index), ends.getInt(index), matchInfo);
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
        h.doc_ = docs.getInt(index);
        h.start_ = starts.getInt(index);
        h.end_ = ends.getInt(index);
        h.matchInfo = matchInfos.isEmpty() ? null : matchInfos.get(index);
        assert HitsInternal.debugCheckReasonableHit(h);
    }

    @Override
    public int doc(long index) {
        return docs.getInt(index);
    }

    @Override
    public int start(long index) {
        return starts.getInt(index);
    }

    @Override
    public int end(long index) {
        return ends.getInt(index);
    }

    @Override
    public MatchInfo[] matchInfos(long index) { return matchInfos.isEmpty() ? null : matchInfos.get(index); }

    @Override
    public MatchInfo matchInfo(long index, int matchInfoIndex) {
        if (matchInfos.isEmpty())
            return null;
        MatchInfo[] matchInfo = matchInfos.get((int) index);
        return matchInfoIndex < matchInfo.length ? matchInfo[matchInfoIndex] : null;
    }

    @Override
    public long sizeNoLock() {
        return docs.size64();
    }

    /** Note: iterating does not lock the arrays, to do that, it should be performed in a {@link #withReadLock} callback. */
    @Override
    public Iterator<EphemeralHit> iterator() {
        return new HitIterator();
    }

    @Override
    HitsInternal sortedNoLock(HitProperty p) {
        p = p.copyWith(this);
        HitsInternalMutable r;
        if (docs.size64() > Constants.JAVA_MAX_ARRAY_SIZE) {
            r = sort64(p);
        } else {
            // We can use regular arrays Collections classes, faster
            r = sort32(p);
        }
        return r;
    }

    private HitsInternalMutable sort64(HitProperty p) {
        // Fill an indices BigArray with 0 ... size
        long size = docs.size64();
        long[][] indices = LongBigArrays.newBigArray(size);
        long hitIndex = 0;
        for (final long[] segment : indices) {
            for (int displacement = 0; displacement < segment.length; displacement++) {
                segment[displacement] = hitIndex;
                hitIndex++;
            }
        }

        // Sort the indices using the given HitProperty
        if (p.getValueType() == PropertyValueString.class) {
            // Collator.compare() is synchronized and therefore slow.
            // It is faster to calculate all the collationkeys first, then parallel sort them.
            CollationKey[][] sortValues = (CollationKey[][])ObjectBigArrays.newBigArray(size);
            hitIndex = 0;
            for (final CollationKey[] segment: sortValues) {
                for (int displacement = 0; displacement < segment.length; displacement++) {
                    segment[displacement] = PropertyValue.collator.getCollationKey(p.get(hitIndex).toString());
                    hitIndex++;
                }
            }
            LongBigArrays.parallelQuickSort(indices, (a, b) -> {
                CollationKey o1 = BigArrays.get(sortValues, a);
                CollationKey o2 = BigArrays.get(sortValues, b);
                return o1.compareTo(o2);
            });
        } else {
            LongBigArrays.parallelQuickSort(indices, p);
        }

        // Now use the sorted indices to fill a new HitsInternal with the actual hits
        HitsInternalMutable r = HitsInternal.create(field, matchInfoDefs, size(), true, false);
        for (final long[] segment: indices) {
            if (matchInfos.isEmpty()) {
                for (long l: segment) {
                    r.add(docs.getInt(l), starts.getInt(l), ends.getInt(l), null);
                }
            } else {
                for (long l: segment) {
                    r.add(docs.getInt(l), starts.getInt(l), ends.getInt(l), matchInfos.get(l));
                }
            }
        }
        return r;
    }
}
