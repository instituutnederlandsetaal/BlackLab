package nl.inl.blacklab.search.results.hits.fetch;

import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;
import nl.inl.blacklab.search.results.hits.EphemeralHit;
import nl.inl.blacklab.search.results.hits.Hits;
import nl.inl.blacklab.search.results.hits.HitsAbstract;
import nl.inl.blacklab.search.results.hits.HitsMutable;
import nl.inl.util.ThreadAborter;

/** 
 * Lazy Hits interface that filters another.
 * <p>
 * Not thread-safe.
 */
public class HitsFilterHits extends HitsAbstract {

    private Hits source;

    private long indexInSource = -1;

    private final HitFilter filter;

    private HitsMutable hits;

    HitsFilterHits(Hits source, HitFilter filter) {
        this.source = source;
        this.filter = filter;
        hits = HitsMutable.create(source.field(), source.matchInfoDefs(), -1, true, false);
    }

    private boolean ensureSeen(long number) {
        if (number == 0)
            return true;
        if (number < 0)
            number = Long.MAX_VALUE;
        EphemeralHit hit = new EphemeralHit();
        while (hits.size() < number && source != null) {
            indexInSource++;
            if (!source.sizeAtLeast(indexInSource + 1)) {
                source = null;
                break; // no more source hits
            }
            if (filter.accept(indexInSource)) {
                source.getEphemeral(indexInSource, hit);
                hits.add(hit);
            }

            try {
                // Do this at the end so interruptions don't happen halfway through a loop and lead to invalid states
                ThreadAborter.checkAbort();
            } catch (InterruptedException e) {
                throw new InterruptedSearch(e);
            }
        }
        return hits.size() >= number;
    }

    @Override
    public AnnotatedField field() {
        return hits.field();
    }

    @Override
    public MatchInfoDefs matchInfoDefs() {
        return hits.matchInfoDefs();
    }

    @Override
    public long size() {
        ensureSeen(-1);
        return hits.size();
    }

    @Override
    public long sizeSoFar() {
        return hits.size();
    }

    @Override
    public void getEphemeral(long index, EphemeralHit hit) {
        ensureSeen(index + 1);
        hits.getEphemeral(index, hit);
    }

    @Override
    public int doc(long index) {
        ensureSeen(index + 1);
        return hits.doc(index);
    }

    @Override
    public int start(long index) {
        ensureSeen(index + 1);
        return hits.start(index);
    }

    @Override
    public int end(long index) {
        ensureSeen(index + 1);
        return hits.end(index);
    }

    @Override
    public MatchInfo[] matchInfos(long hitIndex) {
        ensureSeen(hitIndex + 1);
        return hits.matchInfos(hitIndex);
    }

    @Override
    public MatchInfo matchInfo(long hitIndex, int matchInfoIndex) {
        ensureSeen(hitIndex + 1);
        return hits.matchInfo(hitIndex, matchInfoIndex);
    }

    @Override
    public Hits getStatic() {
        ensureSeen(-1);
        return hits.getStatic();
    }
}
