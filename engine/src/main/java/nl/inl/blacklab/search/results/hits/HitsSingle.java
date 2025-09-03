package nl.inl.blacklab.search.results.hits;

import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;

/** A single hit. Used because HitProperty requires a Hits instance. */
public class HitsSingle extends HitsAbstract {

    public HitsSingle(AnnotatedField field, MatchInfoDefs matchInfoDefs) {
        this.field = field;
        this.matchInfoDefs = matchInfoDefs;
    }

    private final AnnotatedField field;

    private final MatchInfoDefs matchInfoDefs;

    EphemeralHit hit = new EphemeralHit();

    @Override
    public AnnotatedField field() {
        return field;
    }

    @Override
    public MatchInfoDefs matchInfoDefs() {
        return matchInfoDefs;
    }

    @Override
    public long size() {
        return 1;
    }

    @Override
    public void getEphemeral(long index, EphemeralHit hit) {
        if (index != 0)
            throw new IndexOutOfBoundsException("Index: " + index);
        hit.doc_ = this.hit.doc();
        hit.start_ = this.hit.start();
        hit.end_ = this.hit.end();
        hit.matchInfos_ = this.hit.matchInfos();
    }

    @Override
    public int doc(long index) {
        if (index != 0)
            throw new IndexOutOfBoundsException("Index: " + index);
        return hit.doc();
    }

    @Override
    public int start(long index) {
        if (index != 0)
            throw new IndexOutOfBoundsException("Index: " + index);
        return hit.start();
    }

    @Override
    public int end(long index) {
        if (index != 0)
            throw new IndexOutOfBoundsException("Index: " + index);
        return hit.end();
    }

    @Override
    public MatchInfo[] matchInfos(long hitIndex) {
        if (hitIndex != 0)
            throw new IndexOutOfBoundsException("Index: " + hitIndex);
        return hit.matchInfos();
    }

    @Override
    public MatchInfo matchInfo(long hitIndex, int matchInfoIndex) {
        if (hitIndex != 0)
            throw new IndexOutOfBoundsException("Index: " + hitIndex);
        return MatchInfo.get(hit.matchInfos(), matchInfoIndex);
    }

    @Override
    public Hits getStatic() {
        return this;
    }

    public void set(int doc, int start, int end, MatchInfo[] matchInfo) {
        hit.doc_ = doc;
        hit.start_ = start;
        hit.end_ = end;
        hit.matchInfos_ = matchInfo;
    }
}
