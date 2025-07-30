package nl.inl.blacklab.search.results;

import java.util.NoSuchElementException;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import nl.inl.blacklab.Constants;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;

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
class HitsInternalNoLock32 extends HitsInternalAbstract {

    /**
     * Class to iterate over hits.
     * <p>
     * NOTE: contrary to expectation, implementing this class using iterators
     * over docs, starts and ends makes it slower.
     */
    private class HitIterator implements HitsInternal.Iterator {
        private int pos = 0;

        private final EphemeralHit hit = new EphemeralHit();

        public HitIterator() {
        }

        @Override
        public boolean hasNext() {
            // Since this iteration method is not thread-safe anyway, use the direct array to prevent repeatedly acquiring the read lock
            return docs.size() > pos;
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

    protected final IntList docs;
    protected final IntList starts;
    protected final IntList ends;
    protected final ObjectList<MatchInfo[]> matchInfos;

    HitsInternalNoLock32(AnnotatedField field, MatchInfoDefs matchInfoDefs, int initialCapacity) {
        super(field, matchInfoDefs);
        if (initialCapacity < 0) {
            // Use default initial capacities
            docs = new IntArrayList();
            starts = new IntArrayList();
            ends = new IntArrayList();
            matchInfos = new ObjectArrayList<>();
        } else {
            docs = new IntArrayList(initialCapacity);
            starts = new IntArrayList(initialCapacity);
            ends = new IntArrayList(initialCapacity);
            matchInfos = new ObjectArrayList<>(initialCapacity);
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
    HitsInternalNoLock32(AnnotatedField field, MatchInfoDefs matchInfoDefs, IntList docs, IntList starts, IntList ends, ObjectList<MatchInfo[]> matchInfos) {
        super(field, matchInfoDefs);
        if (docs == null || starts == null || ends == null)
            throw new NullPointerException();
        if (docs.size() != starts.size() || docs.size() != ends.size() || ((matchInfos != null && !matchInfos.isEmpty()) && matchInfos.size() != docs.size()))
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
                addAllNoLockSource(hil32);
            });
        } else if (hits instanceof HitsInternalNoLock hinl) {
            // No need to lock
            addAllNoLockSource(hinl);
        } else if (hits instanceof HitsInternalNoLock32 hinl32) {
            // No need to lock
            addAllNoLockSource(hinl32);
        }
    }

    private void addAllNoLockSource(HitsInternalNoLock hil) {
        assert HitsInternal.debugCheckAllReasonable(hil);
        docs.addAll(hil.docs);
        starts.addAll(hil.starts);
        ends.addAll(hil.ends);
        matchInfos.addAll(hil.matchInfos);
        assert matchInfos.isEmpty() || matchInfos.size() == docs.size() : "Wrong number of matchInfos";
    }

    private void addAllNoLockSource(HitsInternalNoLock32 hil) {
        assert HitsInternal.debugCheckAllReasonable(hil);
        docs.addAll(hil.docs);
        starts.addAll(hil.starts);
        ends.addAll(hil.ends);
        matchInfos.addAll(hil.matchInfos);
        assert matchInfos.isEmpty() || matchInfos.size() == docs.size() : "Wrong number of matchInfos";
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
        MatchInfo[] matchInfo = matchInfos.isEmpty() ? null : matchInfos.get((int) index);
        Hit hit = new HitImpl(docs.getInt((int) index), starts.getInt((int) index), ends.getInt((int) index), matchInfo);
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
        h.doc_ = docs.getInt((int)index);
        h.start_ = starts.getInt((int)index);
        h.end_ = ends.getInt((int)index);
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
    public MatchInfo[] matchInfos(long index) {
        return matchInfos.isEmpty() ? null : matchInfos.get((int) index);
    }

    @Override
    public MatchInfo matchInfo(long index, int matchInfoIndex) {
        if (matchInfos.isEmpty())
            return null;
        MatchInfo[] matchInfo = matchInfos.get((int) index);
        return matchInfoIndex < matchInfo.length ? matchInfo[matchInfoIndex] : null;
    }

    @Override
    public long sizeNoLock() {
        return docs.size();
    }

    /** Note: iterating does not lock the arrays, to do that, it should be performed in a {@link #withReadLock} callback. */
    @Override
    public Iterator iterator() {
        return new HitIterator();
    }

    @Override
    public HitsInternal sortedNoLock(HitProperty p) {
        return sort32(p);
    }

}
