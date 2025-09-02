package nl.inl.blacklab.search.results.hitresults;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropContext;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.results.hits.EphemeralHit;
import nl.inl.blacklab.search.results.hits.Hits;
import nl.inl.blacklab.search.results.hits.HitsMutable;
import nl.inl.blacklab.search.results.stats.ResultsStats;
import nl.inl.blacklab.search.results.stats.ResultsStatsPassive;
import nl.inl.util.ThreadAborter;

/**
 * A Hits object that filters another.
 */
public class HitResultsFiltered extends HitResultsWithHitsInternal implements ResultsAwaitable {

    /**
     * Minimum number of hits to fetch in an ensureHitsRead() block.
     *
     * This prevents locking again and again for a single hit when iterating.
     *
     * See {@link HitResultsFromQuery} and {@link HitResultsFiltered}.
     */
    protected static final int FETCH_HITS_MIN = 20;

    private final Lock ensureHitsReadLock = new ReentrantLock();

    /**
     * Document the previous hit was in, so we can count separate documents.
     */
    private int previousHitDoc = -1;

    private HitResults source;

    /** Mutable interface to our list of hits being fetched. */
    protected final HitsMutable results;

    private boolean done = false;

    private long indexInSource = -1;

    private final ResultsStatsPassive hitsStats;

    private final ResultsStatsPassive docsStats;

    /**
     * Filter hits.
     *
     * @param hitResults hits to filter
     * @param property property to filter by
     * @param value value to filter with
     */
    protected HitResultsFiltered(HitResults hitResults, HitProperty property, PropertyValue value) {
        super(hitResults.queryInfo(), HitsMutable.create(hitResults.field(), hitResults.getHits().matchInfoDefs(), -1, true, true));
        this.source = hitResults;
        results = (HitsMutable) hitsInternal;

        // NOTE: this class normally filter lazily, but fetching Contexts will trigger fetching all hits first.
        // We'd like to fix this, but fetching necessary context per hit might be slow. Might be mitigated by
        // implementing a ForwardIndex that stores documents linearly, making it just a single read.
        filterProperty = property.copyWith(PropContext.globalHits(hitResults.getHits(), new ConcurrentHashMap<>()));
        this.filterValue = value;
        hitsStats = new ResultsStatsPassive(new ResultsAwaiterHits(this));
        docsStats = new ResultsStatsPassive(new ResultsAwaiterDocs(this));
    }

    @Override
    public ResultsStats resultsStats() {
        return hitsStats;
    }

    @Override
    public ResultsStats docsStats() {
        return docsStats;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    /**
     * Ensure that we have read at least as many hits as specified in the parameter.
     *
     * @param number the minimum number of hits that will have been read when this
     *            method returns (unless there are fewer hits than this); if
     *            negative, reads all hits
     */
    @Override
    public boolean ensureResultsRead(long number) {
        if (number < 0)
            number = Long.MAX_VALUE; // read all hits
        try {
            // Prevent locking when not required
            if (done || results.size() >= number)
                return results.size() >= number;

            // At least one hit needs to be fetched.
            // Make sure we fetch at least FETCH_HITS_MIN while we're at it, to avoid too much locking.
            number = Math.max(number, results.size() + FETCH_HITS_MIN);

            while (!ensureHitsReadLock.tryLock(50, TimeUnit.MILLISECONDS)) {
                /*
                 * Another thread is already counting, we don't want to straight up block until it's done
                 * as it might be counting/retrieving all results, while we might only want trying to retrieve a small fraction
                 * So instead poll our own state, then if we're still missing results after that just count them ourselves
                 */
                if (done || results.size() >= number)
                    return results.size() >= number;
            }
            try {
                EphemeralHit hit = new EphemeralHit();
                Hits sourceHits = source.getHits();
                while (!done && results.size() < number) {
                    // Abort if asked
                    ThreadAborter.checkAbort();

                    // Advance to next hit
                    indexInSource++;
                    if (sourceHits.sizeAtLeast(indexInSource + 1)) {
                        sourceHits.getEphemeral(indexInSource, hit);
                        process(hit);
                    } else {
                        done = true;
                        hitsStats.setDone(source.resultsStats().maxStats());
                        docsStats.setDone(source.docsStats().maxStats());
                        dispose();
                        source = null; // allow this to be GC'ed
                    }
                }
            } finally {
                ensureHitsReadLock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // preserve interrupted status
            throw new InterruptedSearch(e);
        }
        return results.size() >= number;
    }

    /** Accept this hit and add it to the results. */
    protected void accept(EphemeralHit hit) {
        results.add(hit);
        hitsStats.increment(true);
        if (hit.doc() != previousHitDoc) {
            docsStats.increment(true);
            previousHitDoc = hit.doc();
        }
    }

    // FILTERING-RELATED

    private final HitProperty filterProperty;

    private final PropertyValue filterValue;

    private void process(EphemeralHit hit) {
        if (filterProperty.get(indexInSource).equals(filterValue)) {
            // Yes, keep this hit
            accept(hit);
        }
    }

    private void dispose() {
        filterProperty.disposeContext(); // we don't need the context information anymore, free memory
    }
}
