package nl.inl.blacklab.resultproperty;

import java.util.Map;

import org.apache.lucene.index.LeafReaderContext;

import com.ibm.icu.text.CollationKey;

import nl.inl.blacklab.search.results.hits.Hits;

public record PropContext(Hits hits, LeafReaderContext lrc, boolean toGlobal, Map<String, CollationKey> collationCache) {
    public static final PropContext NO_CHANGE = new PropContext(null, null, false, null);

    public static PropContext globalHits(Hits hits) {
        return globalHits(hits, null);
    }

    public static PropContext globalHits(Hits hits, Map<String, CollationKey> collationCache) {
        return new PropContext(hits, null, false, collationCache);
    }

    public static PropContext segmentHits(Hits hits, LeafReaderContext lrc) {
        return segmentHits(hits, lrc, null);
    }

    public static PropContext segmentHits(Hits hits, LeafReaderContext lrc, Map<String, CollationKey> collationCache) {
        return new PropContext(hits, lrc, false, collationCache);
    }

    public static PropContext segmentToGlobal(Hits hits, LeafReaderContext lrc) {
        return segmentToGlobal(hits, lrc, null);
    }

    public static PropContext segmentToGlobal(Hits hits, LeafReaderContext lrc, Map<String, CollationKey> collationCache) {
        return new PropContext(hits, lrc, true, collationCache);
    }

    /**
     * If we have segment hits and intend to produce global property values,
     * this method will adjust the doc id to be global.
     *
     * This is different from globalDocIdForHit below: that always returns a
     * global doc id, while this method will return a segment-local doc id
     * if toGlobal is false.
     *
     * @param index hit index
     * @return (possibly) adjusted doc id
     */
    int resultDocIdForHit(long index) {
        return hits.doc(index) + (toGlobal ? lrc.docBase : 0);
    }

    /**
     * Get a global doc id for a segment hit.
     * (because DocProperty only works with global doc ids right now)
     *
     * @param index hit index
     * @return (possibly) adjusted doc id
     */
    int globalDocIdForHit(long index) {
        return hits.doc(index) + (lrc != null ? lrc.docBase : 0);
    }

    public PropContext adjustedWith(PropContext contextChanges) {
        if (contextChanges == null || contextChanges == PropContext.NO_CHANGE)
            return this;
        Hits hits = contextChanges.hits == null ? this.hits : contextChanges.hits;
        LeafReaderContext lrc = contextChanges.lrc == null ? this.lrc : contextChanges.lrc;
        boolean toGlobal = lrc != null && (contextChanges.toGlobal || this.toGlobal);
        Map<String, CollationKey> collationCache = contextChanges.collationCache == null ? this.collationCache :
                contextChanges.collationCache;
        return new PropContext(hits, lrc, toGlobal, collationCache);
    }
}
