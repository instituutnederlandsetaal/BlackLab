package nl.inl.blacklab.search.results.hits;

import java.util.Arrays;

import nl.inl.blacklab.search.lucene.MatchInfo;

/**
 * Interface for a hit. Normally, hits are iterated over in a Lucene Spans object,
 * but in some places, it makes sense to place hits in separate objects: when
 * caching or sorting hits, or just for convenience in client code.
 */
public interface Hit extends Comparable<Hit> {
    
    /**
     * Create a hit.
     * 
     * @param doc Lucene document id
     * @param start position of first word of the hit
     * @param end first word position after the hit
     * @param matchInfo extra information such as capture groups / relations
     * @return the hit
     */
    static Hit create(int doc, int start, int end, MatchInfo[] matchInfo) {
        return new HitImpl(doc, start, end, matchInfo);
    }
    
    @Override
    default int compareTo(Hit o) {
        if (this == o)
            return 0;
        if (doc() == o.doc()) {
            if (start() == o.start()) {
                if (end() == o.end()) {
                    // Hits are identical in terms of doc, start and end.
                    // Compare their MatchInfos.
                    return Arrays.compare(matchInfos(), o.matchInfos());
                }
                return end() - o.end();
            }
            return start() - o.start();
        }
        return doc() - o.doc();
    }

    /**
     * Get the document this hit occurs in.
     * @return Lucene document id
     */
    int doc();

    /**
     * Get the start of this hit.
     * @return position of first word of the hit
     */
    int start();

    /**
     * Get the end of this hit.
     * @return position of first word after the hit
     */
    int end();

    /**
     * Get extra information for this hit, such as captured groups and relations.
     *
     * Only available if the query captured such information.
     *
     * CAUTION: the returned array may vary in length depending on the segment the hit came from.
     * If the first BLSpans captures fewer match infos than subsequent ones (e.g. because certain
     * terms don't occur in that index segment, so an entire subclause was optimized away), some
     * match infos weren't registered yet, so the array allocated is shorter. Use { @link #matchInfos(int)} to
     * guard against this.
     *
     * @return extra information for this hit, or null if none available
     */
    MatchInfo[] matchInfos();

    default MatchInfo matchInfos(int groupIndex) {
        if (groupIndex < 0)
            throw new IndexOutOfBoundsException("Group index must be non-negative: " + groupIndex);
        MatchInfo[] matchInfo = matchInfos();
        if (matchInfo == null || groupIndex >= matchInfo.length) {
            // matchInfo[] can be shorted for some hits than others, in rare cases where the first BLSpans
            // captures fewer match infos than subsequent ones (e.g. because certain terms don't occur in that
            // index segment, so an entire subclause was optimized away).
            return null;
        }
        return matchInfo[groupIndex];
    }
}
