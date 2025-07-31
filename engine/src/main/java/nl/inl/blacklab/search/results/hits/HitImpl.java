package nl.inl.blacklab.search.results.hits;

import java.util.Arrays;
import java.util.Objects;

import nl.inl.blacklab.search.lucene.MatchInfo;

/**
 * Class for a hit. Normally, hits are iterated over in a Lucene Spans object,
 * but in some places, it makes sense to place hits in separate objects: when
 * caching or sorting hits, or just for convenience in client code.
 */
public final class HitImpl implements Hit {

    /** The Lucene doc this hits occurs in */
    final int doc;

    /**
     * End of this hit's span (in word positions).
     *
     * Note that this actually points to the first word not in the hit (just like
     * Spans).
     */
    final int end;

    /** Start of this hit's span (in word positions) */
    final int start;

    /** Match info for this hit, or null if none */
    final MatchInfo[] matchInfo;

    /**
     * Construct a hit object
     *
     * @param doc the document
     * @param start start of the hit (word positions)
     * @param end end of the hit (word positions)
     */
    HitImpl(int doc, int start, int end, MatchInfo[] matchInfo) {
        this.doc = doc;
        this.start = start;
        this.end = end;
        this.matchInfo = matchInfo;
    }

    @Override
    public int doc() {
        return doc;
    }

    @Override
    public int end() {
        return end;
    }

    @Override
    public int start() {
        return start;
    }

    @Override
    public MatchInfo[] matchInfos() {
        return matchInfo;
    }

    @Override
    public String toString() {
        return String.format("doc %d, words %d-%d", doc(), start(), end());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null)
            return false;
        if (o instanceof EphemeralHit that) {
            return doc == that.doc_ && start == that.start_ && end == that.end_ && Arrays.equals(matchInfo,
                    that.matchInfo);
        } else if (o instanceof HitImpl hit) {
            return doc == hit.doc && start == hit.start && end == hit.end && Arrays.equals(matchInfo,
                    hit.matchInfo);
        } else if (o instanceof Hit hit) {
            return doc == hit.doc() && start == hit.start() && end == hit.end() && Arrays.equals(matchInfo,
                    hit.matchInfos());
        }
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(doc, start, end);
        result = 31 * result + Arrays.hashCode(matchInfo);
        return result;
    }
    
}
