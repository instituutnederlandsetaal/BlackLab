package nl.inl.blacklab.search.results.hits;

import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;

/** A single hit. Used because HitProperty requires a Hits instance. */
public class HitsSingle extends HitsAbstract {

    private final AnnotatedField field;

    private final MatchInfoDefs matchInfoDefs;

    EphemeralHit hit = new EphemeralHit();

    public HitsSingle(AnnotatedField field, MatchInfoDefs matchInfoDefs) {
        this.field = field;
        this.matchInfoDefs = matchInfoDefs;
    }

    public HitsSingle(AnnotatedField field, MatchInfoDefs matchInfoDefs, int doc, int matchStart, int matchEnd) {
        this(field, matchInfoDefs);
        hit.doc_ = doc;
        hit.start_ = matchStart;
        hit.end_ = matchEnd;
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

    /** Set the hit to return.
     *
     * Note that this will not make a copy; the EphemeralHit you pass in will be used directly.
     * Only suitable for very temporary use.
     *
     * @param hit the hit
     */
    public void set(EphemeralHit hit) {
        this.hit = hit;
    }

    /**
     * Set the hit to return.
     *
     * @param doc document id
     * @param start start position
     * @param end end position
     * @param matchInfo match info
     */
    public void set(int doc, int start, int end, MatchInfo[] matchInfo) {
        hit.doc_ = doc;
        hit.start_ = start;
        hit.end_ = end;
        hit.matchInfos_ = matchInfo;
    }
}
