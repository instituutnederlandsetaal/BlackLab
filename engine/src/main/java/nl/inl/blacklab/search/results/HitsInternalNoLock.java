package nl.inl.blacklab.search.results;

import java.text.CollationKey;
import java.util.function.Consumer;

import it.unimi.dsi.fastutil.BigArrays;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntBigArrayBigList;
import it.unimi.dsi.fastutil.ints.IntBigList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.longs.LongBigArrays;
import it.unimi.dsi.fastutil.objects.ObjectBigArrayBigList;
import it.unimi.dsi.fastutil.objects.ObjectBigArrays;
import it.unimi.dsi.fastutil.objects.ObjectBigList;
import nl.inl.blacklab.Constants;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.PropertyValueString;
import nl.inl.blacklab.search.lucene.MatchInfo;

/**
 * A HitsInternal implementation that does no locking and can handle huge result sets.
 * <p>
 * This means it is safe to fill this object in one thread, then
 * use it from many threads as long as it is not modified anymore.
 * <p>
 * A test calling {@link #add(int, int, int, MatchInfo[])} millions of times came out to be about 11% faster than
 * {@link HitsInternalLock}. That is not representative of real-world usage of course, but on huge
 * resultsets this will likely save a few seconds.
 */
class HitsInternalNoLock implements HitsInternalMutable {

    /**
     * Class to iterate over hits.
     * <p>
     * NOTE: contrary to expectation, implementing this class using iterators
     * over docs, starts and ends makes it slower.
     */
    private class HitIterator implements Iterator {
        private int pos = 0;

        private final EphemeralHit hit = new EphemeralHit();

        public HitIterator() {
        }

        @Override
        public boolean hasNext() {
            return HitsInternalNoLock.this.docs.size64() > pos;
        }

        @Override
        public EphemeralHit next() {
            hit.doc = HitsInternalNoLock.this.docs.getInt(pos);
            hit.start = HitsInternalNoLock.this.starts.getInt(pos);
            hit.end = HitsInternalNoLock.this.ends.getInt(pos);
            hit.matchInfo = HitsInternalNoLock.this.matchInfos.isEmpty() ? null :
                    HitsInternalNoLock.this.matchInfos.get(pos);
            ++this.pos;
            return hit;
        }
    }

    protected final IntBigList docs;
    protected final IntBigList starts;
    protected final IntBigList ends;
    protected final ObjectBigList<MatchInfo[]> matchInfos;

    HitsInternalNoLock(long initialCapacity) {
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

    /**
     * Add the hit to the end of this list, copying the values. The hit object itself is not retained.
     */
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

    /**
     * Add the hit to the end of this list, copying the values. The hit object itself is not retained.
     */
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

    public void addAll(HitsInternalNoLock hits) {
        assert HitsInternal.debugCheckAllReasonable(hits);
        docs.addAll(hits.docs);
        starts.addAll(hits.starts);
        ends.addAll(hits.ends);
        matchInfos.addAll(hits.matchInfos);
    }

    public void addAll(HitsInternal hits) {
        hits.withReadLock(hr -> {
            for (EphemeralHit h : hits) {
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

    public void withReadLock(Consumer<HitsInternal> cons) {
        cons.accept(this);
    }

    public Hit get(long index) {
        MatchInfo[] matchInfo = matchInfos.isEmpty() ? null : matchInfos.get(index);
        return new HitImpl(docs.getInt((int) index), starts.getInt((int) index), ends.getInt((int) index), matchInfo);
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
    public void getEphemeral(long index, EphemeralHit h) {
        h.doc = docs.getInt(index);
        h.start = starts.getInt(index);
        h.end = ends.getInt(index);
        h.matchInfo = matchInfos.isEmpty() ? null : matchInfos.get(index);
        assert HitsInternal.debugCheckReasonableHit(h);
    }

    public int doc(long index) {
        return this.docs.getInt(index);
    }

    public int start(long index) {
        return this.starts.getInt(index);
    }

    public int end(long index) {
        return this.ends.getInt(index);
    }

    @Override
    public MatchInfo[] matchInfo(long index) { return this.matchInfos.isEmpty() ? null : this.matchInfos.get(index); }

    public long size() {
        return docs.size64();
    }

    /**
     * Expert use: get the internal docs array.
     * The array is not locked, so care should be taken when reading it.
     * Best to wrap usage of this function and the returned in a withReadLock call.
     * <p>
     * NOTE JN: only used in HitsList constructor; eliminate entirely?
     *
     * @return list of document ids
     */
    public IntIterator docsIterator() {
        return docs.intIterator();
    }

    /**
     * Note: iterating does not lock the arrays, to do that, it should be performed in a {@link #withReadLock} callback.
     */
    @Override
    public Iterator iterator() {
        return new HitIterator();
    }

    public HitsInternal sort(HitProperty p) {
        HitsInternalMutable r;
        long size = docs.size64();
        if (size > Constants.JAVA_MAX_ARRAY_SIZE) {
            // Fill an indices BigArray with 0 ... size
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
            r = HitsInternal.create(size, true, false);
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
        } else {
            // We can use regular arrays Collections classes, faster
            int[] indices = new int[(int) size];
            for (int i = 0; i < indices.length; ++i)
                indices[i] = i;

            IntArrays.quickSort(indices, p::compare);

            r = HitsInternal.create(size, false, false);
            if (matchInfos.isEmpty()) {
                for (int index: indices) {
                    r.add(docs.getInt(index), starts.getInt(index), ends.getInt(index), null);
                }
            } else {
                for (int index: indices) {
                    r.add(docs.getInt(index), starts.getInt(index), ends.getInt(index), matchInfos.get(index));
                }
            }
        }
        return r;
    }
}
