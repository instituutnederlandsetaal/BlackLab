package nl.inl.blacklab.search.results;

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
    private final int doc;

    /**
     * End of this hit's span (in word positions).
     *
     * Note that this actually points to the first word not in the hit (just like
     * Spans).
     */
    private final int end;

    /** Start of this hit's span (in word positions) */
    private final int start;

    /** Match info for this hit, or null if none */
    private final MatchInfo[] matchInfo;

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
        if (o == null || getClass() != o.getClass())
            return false;
        HitImpl hit = (HitImpl) o;
        return doc == hit.doc && end == hit.end && start == hit.start && Arrays.equals(matchInfo, hit.matchInfo);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(doc, end, start);
        result = 31 * result + Arrays.hashCode(matchInfo);
        return result;
    }
    
}
