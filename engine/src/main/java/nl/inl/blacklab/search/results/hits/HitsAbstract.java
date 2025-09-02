package nl.inl.blacklab.search.results.hits;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropContext;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.results.hitresults.Concordances;
import nl.inl.blacklab.search.results.hitresults.ContextSize;
import nl.inl.blacklab.search.results.hitresults.HitGroups;
import nl.inl.blacklab.search.results.hitresults.Kwics;

public abstract class HitsAbstract implements Hits {

    @Override
    public long countDocs() {
        Set<Integer> docs = new HashSet<>();
        for (long hitIndex = 0; hitIndex < size(); hitIndex++) {
            docs.add(doc(hitIndex));
        }
        return docs.size();
    }

    @Override
    public boolean hasMatchInfo() {
        return matchInfoDefs().currentSize() > 0;
    }

    @Override
    public boolean isEmpty() {
        return !sizeAtLeast(1);
    }

    @Override
    public boolean sizeAtLeast(long minSize) {
        return size() >= minSize;
    }

    @Override
    public Hit get(long index) {
        EphemeralHit hit = new EphemeralHit();
        getEphemeral(index, hit);
        return hit.toHit();
    }

    @Override
    public Hits sublist(long start, long length) {
        if (start < 0)
            throw new IndexOutOfBoundsException("Window start must be non-negative, but was " + start);
        if (length < 0)
            throw new IllegalArgumentException("Window size must be non-negative, but was " + length);
        if (start > size() || length == 0)
            return Hits.empty(field(), matchInfoDefs());
        long end = start + length;
        if (end > size())
            end = size();
        HitsMutable window = HitsMutable.create(field(), matchInfoDefs(), end - start, false, false);
        EphemeralHit h = new EphemeralHit();
        for (long i = start; i < end; ++i) {;
            getEphemeral(i, h);
            window.add(h);
        }
        return window;
    }

    @Override
    public Iterator<EphemeralHit> iterator() {
        return new Iterator<>() {
            private long pos = 0;

            private final EphemeralHit hit = new EphemeralHit();

            @Override
            public boolean hasNext() {
                // Since this iteration method is not thread-safe anyway, use the direct array to prevent repeatedly acquiring the read lock
                return size() > pos;
            }

            @Override
            public EphemeralHit next() {
                if (!hasNext())
                    throw new NoSuchElementException();
                getEphemeral(pos, hit);
                ++pos;
                return hit;
            }
        };
    }

    public Concordances concordances(ContextSize contextSize, ConcordanceType type) {
        if (contextSize == null)
            contextSize = index().defaultContextSize();
        if (type == null)
            type = ConcordanceType.FORWARD_INDEX;
        return new Concordances(getStatic(), type, contextSize);
    }

    @Override
    public Kwics kwics(ContextSize contextSize) {
        if (contextSize == null)
            contextSize = index().defaultContextSize();
        return new Kwics(getStatic(), contextSize);
    }

    @Override
    public Hits filteredByDocId(int docId) {
        HitsMutable hitsInDoc = HitsMutable.create(field(), matchInfoDefs(), -1, size(), false);
        for (EphemeralHit h: this) {
            if (h.doc() == docId)
                hitsInDoc.add(h);
        }
        return hitsInDoc;
    }

    static Map<PropertyValue, Group> groupHits(Hits hits, HitProperty groupBy,
            long maxResultsToStorePerGroup, Map<PropertyValue, Group> groups, LeafReaderContext lrc) {
        // temporary copy used in grouping (don't keep reference to hits)
        // NOTE: we pass toGlobal = true because segment hits must be grouped by global identity (so we can merge them)
        hits = hits.getStatic(); // get most efficient (non-locking list) version of hits if possible
        groupBy = groupBy.copyWith(PropContext.segmentToGlobal(hits, lrc));

        int hitIndex = 0;
        for (EphemeralHit hit: hits) {
            PropertyValue identity = groupBy.get(hitIndex);
            if (lrc != null) {
                // This is a segment hit. Convert doc id to global.
                hit.convertDocIdToGlobal(lrc.docBase);
            }
            Group group = groups.get(identity);
            if (group == null) {
                if (groups.size() >= HitGroups.MAX_NUMBER_OF_GROUPS)
                    throw new UnsupportedOperationException(
                            "Cannot handle more than " + HitGroups.MAX_NUMBER_OF_GROUPS + " groups");
                HitsMutable hitsInGroup = HitsMutable.create(hits.field(), hits.matchInfoDefs(),
                        -1,
                        hits.size(), false);
                group = new Group(hitsInGroup, 0);
                groups.put(identity, group);
            }
            if (maxResultsToStorePerGroup < 0 || group.storedHits.size() < maxResultsToStorePerGroup) {
                group.storedHits.add(hit);
            }
            group.totalNumberOfHits++;
            ++hitIndex;
        }
        groupBy.disposeContext(); // we don't need the context information anymore, free memory
        return groups;
    }
}
