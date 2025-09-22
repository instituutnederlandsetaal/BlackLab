package nl.inl.blacklab.search.results.hits;

import java.util.Arrays;
import java.util.Objects;

import nl.inl.blacklab.search.lucene.MatchInfo;

/**
 * A mutable implementation of Hit, to be used for short-lived
 * instances used while e.g. iterating through a list of hits.
 */
public class EphemeralHit implements Hit {
    public int doc_ = -1;
    public int start_ = -1;
    public int end_ = -1;
    public MatchInfo[] matchInfos_ = null;

    /** Create a permanent copy of this hit that can be stored. */
    public PermanentHit solidify() {
        return new PermanentHit(doc_, start_, end_, matchInfos_);
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
    public MatchInfo[] matchInfos() { return matchInfos_; }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null)
            return false;
        if (o instanceof EphemeralHit that) {
            return doc_ == that.doc_ && start_ == that.start_ && end_ == that.end_ && Arrays.equals(matchInfos_,
                    that.matchInfos_);
        } else if (o instanceof PermanentHit permanentHit) {
            return doc_ == permanentHit.doc && start_ == permanentHit.start && end_ == permanentHit.end && Arrays.equals(matchInfos_,
                    permanentHit.matchInfo);
        } else if (o instanceof Hit hit) {
            return doc_ == hit.doc() && start_ == hit.start() && end_ == hit.end() && Arrays.equals(matchInfos_,
                    hit.matchInfos());
        }
        return false;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(doc_, start_, end_);
        result = 31 * result + Arrays.hashCode(matchInfos_);
        return result;
    }

    /**
     * Convert a hit to a global hit by adding the document base.
     *
     * Each segment has a document base, which is the lowest document ID in that segment.
     * Adding the document base converts the document ID of the hit to a global document ID.
     *
     * @param docBase the document base to add
     */
    public void convertDocIdToGlobal(int docBase) {
        if (doc_ != -1)
            doc_ += docBase;
    }

    public void set(int doc, int start, int end, MatchInfo[] matchInfos) {
        this.doc_ = doc;
        this.start_ = start;
        this.end_ = end;
        this.matchInfos_ = matchInfos;
    }
}
