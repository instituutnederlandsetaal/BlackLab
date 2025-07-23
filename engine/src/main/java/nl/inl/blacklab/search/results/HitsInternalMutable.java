package nl.inl.blacklab.search.results;

import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;

/**
 * A mutable list of simple hits, used internally.
 * <p>
 * Contrary to {@link Hits}, this only contains doc, start and end
 * for each hit, so no captured groups information, and no other
 * bookkeeping (hit/doc retrieved/counted stats, etc.).
 */
public interface HitsInternalMutable extends HitsInternal {

    void add(int doc, int start, int end, MatchInfo[] matchInfo);

    void add(EphemeralHit hit);

    void add(Hit hit);

    void addAll(HitsInternal hits);

    /**
     * Remove all hits.
     */
    void clear();

    void setMatchInfoDefs(MatchInfoDefs matchInfoDefs);

}
