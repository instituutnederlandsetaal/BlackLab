package nl.inl.blacklab.search.results.hitresults;

import java.util.Iterator;

import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.hits.EphemeralHit;
import nl.inl.blacklab.search.results.hits.Hits;
import nl.inl.blacklab.search.results.hits.HitsAbstract;

/**
 * A collection of matches being fetched as they are needed.
 *
 * Should be thread-safe and most methods are safe w.r.t. hits having been fetched.
 */
public abstract class HitResultsWithHitsInternal extends HitResultsAbstract {

    /** Our internal list of hits being fetched. */
    protected final Hits hitsInternal;

    /** A view to the complete set of hits.
     *  Will wait until enough hits have been fetched (if applicable).
     *  For example, if you call sorted() on this, it will wait until
     *  all hits have been fetched first.
     */
    Hits hitsView;

    /**
     * Construct a Hits object from a hits array.
     *
     * @param queryInfo query info for corresponding query
     * @param hits hits to use for this object. Used as-is, not copied.
     */
    protected HitResultsWithHitsInternal(QueryInfo queryInfo, Hits hits) {
        super(queryInfo);
        if (hits == null)
            throw new IllegalArgumentException("HitsAbstract must be constructed with valid hits object (got null)");
        this.hitsInternal = hits;
        hitsView = new LazyHitsView();
    }

    @Override
    public long numberOfResultObjects() {
        return this.hitsInternal.size();
    }

    @Override
    public Hits getHits() {
        return hitsView;
    }

    private class LazyHitsView extends HitsAbstract {
        @Override
        public AnnotatedField field() {
            return queryInfo().field();
        }

        @Override
        public MatchInfoDefs matchInfoDefs() {
            return hitsInternal.matchInfoDefs();
        }

        @Override
        public long size() {
            ensureResultsRead(-1);
            return hitsInternal.size();
        }

        @Override
        public boolean sizeAtLeast(long minSize) {
            return ensureResultsRead(minSize);
        }

        @Override
        public void getEphemeral(long index, EphemeralHit hit) {
            ensureResultsRead(index + 1);
            hitsInternal.getEphemeral(index, hit);
        }

        @Override
        public Iterator<EphemeralHit> iterator() {
            ensureResultsRead(-1);
            return hitsInternal.iterator();
        }

        /**
         * Get Lucene document id for the specified hit
         * @param index hit index
         * @return document id
         */
        @Override
        public int doc(long index) {
            ensureResultsRead(index + 1);
            return hitsInternal.doc(index);
        }

        /**
         * Get start position for the specified hit
         * @param index hit index
         * @return document id
         */
        @Override
        public int start(long index) {
            ensureResultsRead(index + 1);
            return hitsInternal.start(index);
        }

        /**
         * Get end position for the specified hit
         * @param index hit index
         * @return document id
         */
        @Override
        public int end(long index) {
            ensureResultsRead(index + 1);
            return hitsInternal.end(index);
        }

        @Override
        public MatchInfo[] matchInfos(long hitIndex) {
            ensureResultsRead(hitIndex + 1);
            return hitsInternal.matchInfos(hitIndex);
        }

        @Override
        public MatchInfo matchInfo(long hitIndex, int matchInfoIndex) {
            ensureResultsRead(hitIndex + 1);
            return hitsInternal.matchInfo(hitIndex, matchInfoIndex);
        }

        @Override
        public Hits sublist(long first, long length) {
            ensureResultsRead(first + length);
            return hitsInternal.sublist(first, length);
        }

        @Override
        public Hits sorted(HitProperty sortBy) {
            ensureResultsRead(-1);
            return hitsInternal.sorted(sortBy);
        }

        @Override
        public Hits getStatic() {
            ensureResultsRead(-1);
            return hitsInternal.getStatic();
        }

        @Override
        public Hits filteredByDocId(int docId) {
            ensureResultsRead(-1);
            return hitsInternal.filteredByDocId(docId);
        }

        @Override
        public Concordances concordances(ContextSize contextSize, ConcordanceType type) {
            ensureResultsRead(-1);
            return hitsInternal.concordances(contextSize, type);
        }

        @Override
        public Kwics kwics(ContextSize contextSize) {
            ensureResultsRead(-1);
            return hitsInternal.kwics(contextSize);
        }

        @Override
        public Concordances concordances(ContextSize contextSize) {
            ensureResultsRead(-1);
            return hitsInternal.concordances(contextSize);
        }
    }
}
