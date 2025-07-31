package nl.inl.blacklab.search.results;

import java.util.Arrays;
import java.util.Objects;

import nl.inl.blacklab.search.lucene.MatchInfo;

/**
 * A mutable implementation of Hit, to be used for short-lived
 * instances used while e.g. iterating through a list of hits.
 */
public class EphemeralHit implements Hit {
    int doc_ = -1;
    int start_ = -1;
    int end_ = -1;
    MatchInfo[] matchInfo = null;

    public Hit toHit() {
        return new HitImpl(doc_, start_, end_, matchInfo);
    }

    @Override
    public int doc() {
        return doc_;
    }

    @Override
    public int start() {
        return start_;
    }

    @Override
    public int end() {
        return end_;
    }

    @Override
    public MatchInfo[] matchInfos() { return matchInfo; }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null)
            return false;
        if (o instanceof EphemeralHit that) {
            return doc_ == that.doc_ && start_ == that.start_ && end_ == that.end_ && Arrays.equals(matchInfo,
                    that.matchInfo);
        } else if (o instanceof HitImpl hitImpl) {
            return doc_ == hitImpl.doc && start_ == hitImpl.start && end_ == hitImpl.end && Arrays.equals(matchInfo,
                    hitImpl.matchInfo);
        } else if (o instanceof Hit hit) {
            return doc_ == hit.doc() && start_ == hit.start() && end_ == hit.end() && Arrays.equals(matchInfo,
                    hit.matchInfos());
        }
        return false;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(doc_, start_, end_);
        result = 31 * result + Arrays.hashCode(matchInfo);
        return result;
    }
}
