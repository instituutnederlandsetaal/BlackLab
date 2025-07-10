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

    Hit toHit() {
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
    public MatchInfo[] matchInfo() { return matchInfo; }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        EphemeralHit that = (EphemeralHit) o;
        return doc_ == that.doc_ && start_ == that.start_ && end_ == that.end_ && Arrays.equals(matchInfo,
                that.matchInfo);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(doc_, start_, end_);
        result = 31 * result + Arrays.hashCode(matchInfo);
        return result;
    }
}
