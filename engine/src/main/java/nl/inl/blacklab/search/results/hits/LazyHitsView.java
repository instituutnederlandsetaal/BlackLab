package nl.inl.blacklab.search.results.hits;

import java.util.Iterator;

import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.results.hitresults.Concordances;
import nl.inl.blacklab.search.results.hitresults.ContextSize;
import nl.inl.blacklab.search.results.hitresults.Kwics;

/** A wrapper around a list of hits, ensuring that enough have been fetched. */
public class LazyHitsView extends HitsAbstract {

    @FunctionalInterface
    public interface EnsureRead {
        /**
         * Ensure that we have read at least as many results as specified in the parameter.
         *
         * @param number the minimum number of results that will have been read when this
         *               method returns (unless there are fewer hits than this); if
         *               negative, reads all hits
         * @return true if the requested number of results were read, false if there are fewer results
         */
        boolean atLeast(long number);
    }

    /** Our internal list of hits being fetched. */
    protected final Hits hits;

    /**
     * Method to call to ensure enough results have been read.
     */
    protected final EnsureRead ensureRead;

    public LazyHitsView(Hits hits, EnsureRead ensureRead) {
        this.hits = hits;
        this.ensureRead = ensureRead;
    }

    @Override
    public HitsContext context() {
        return hits.context();
    }

    @Override
    public long size() {
        ensureRead.atLeast(-1);
        return hits.size();
    }

    @Override
    public long sizeSoFar() {
        return hits.size();
    }

    @Override
    public boolean sizeAtLeast(long minSize) {
        return ensureRead.atLeast(minSize);
    }

    @Override
    public void getEphemeral(long index, EphemeralHit hit) {
        ensureRead.atLeast(index + 1);
        hits.getEphemeral(index, hit);
    }

    @Override
    public Iterator<EphemeralHit> iterator() {
        ensureRead.atLeast(-1);
        return hits.iterator();
    }

    /**
     * Get Lucene document id for the specified hit
     *
     * @param index hit index
     * @return document id
     */
    @Override
    public int doc(long index) {
        ensureRead.atLeast(index + 1);
        return hits.doc(index);
    }

    /**
     * Get start position for the specified hit
     *
     * @param index hit index
     * @return document id
     */
    @Override
    public int start(long index) {
        ensureRead.atLeast(index + 1);
        return hits.start(index);
    }

    /**
     * Get end position for the specified hit
     *
     * @param index hit index
     * @return document id
     */
    @Override
    public int end(long index) {
        ensureRead.atLeast(index + 1);
        return hits.end(index);
    }

    @Override
    public MatchInfo[] matchInfos(long hitIndex) {
        ensureRead.atLeast(hitIndex + 1);
        return hits.matchInfos(hitIndex);
    }

    @Override
    public MatchInfo matchInfo(long hitIndex, int matchInfoIndex) {
        ensureRead.atLeast(hitIndex + 1);
        return hits.matchInfo(hitIndex, matchInfoIndex);
    }

    @Override
    public Hits sublist(long first, long length) {
        ensureRead.atLeast(first + length);
        return hits.sublist(first, length);
    }

    @Override
    public Hits sorted(HitProperty sortBy) {
        ensureRead.atLeast(-1);
        return hits.sorted(sortBy);
    }

    @Override
    public Hits getStatic() {
        ensureRead.atLeast(-1);
        return hits.getStatic();
    }

    @Override
    public Hits filteredByDocId(int docId) {
        ensureRead.atLeast(-1);
        return hits.filteredByDocId(docId);
    }

    @Override
    public Concordances concordances(ContextSize contextSize, ConcordanceType type) {
        ensureRead.atLeast(-1);
        return hits.concordances(contextSize, type);
    }

    @Override
    public Kwics kwics(ContextSize contextSize) {
        ensureRead.atLeast(-1);
        return hits.kwics(contextSize);
    }

    @Override
    public Concordances concordances(ContextSize contextSize) {
        ensureRead.atLeast(-1);
        return hits.concordances(contextSize);
    }
}
