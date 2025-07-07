package nl.inl.blacklab.search.results;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;

/**
 * A Hits object that filters another.
 */
public class HitsFiltered extends HitsMutable {

    private final Lock ensureHitsReadLock = new ReentrantLock();

    /**
     * Document the previous hit was in, so we can count separate documents.
     */
    private int previousHitDoc = -1;

    private Hits source;

    private final HitProperty filterProperty;

    private final PropertyValue filterValue;

    private boolean doneFiltering = false;

    private int indexInSource = -1;

    private final boolean ascendingLuceneDocIds;

    /**
     * Filter hits.
     *
     * @param hits hits to filter
     * @param property property to filter by
     * @param value value to filter with
     */
    protected HitsFiltered(Hits hits, HitProperty property, PropertyValue value) {
        super(hits.queryInfo());
        this.source = hits;
        ascendingLuceneDocIds = source.hasAscendingLuceneDocIds();

        // NOTE: this class normally filter lazily, but fetching Contexts will trigger fetching all hits first.
        // We'd like to fix this, but fetching necessary context per hit might be slow. Might be mitigated by
        // implementing a ForwardIndex that stores documents linearly, making it just a single read.
        filterProperty = property.copyWith(hits);
        this.filterValue = value;
    }

    @Override
    public String toString() {
        return "HitsFilter#" + hitsObjId;
    }

    /**
     * Ensure that we have read at least as many hits as specified in the parameter.
     *
     * @param number the minimum number of hits that will have been read when this
     *            method returns (unless there are fewer hits than this); if
     *            negative, reads all hits
     */
    @Override
    protected void ensureResultsRead(long number) {
        try {
            // Prevent locking when not required
            if (doneFiltering || number >= 0 && hitsInternalMutable.size() > number)
                return;

            // At least one hit needs to be fetched.
            // Make sure we fetch at least FETCH_HITS_MIN while we're at it, to avoid too much locking.
            if (number >= 0 && number - hitsInternalMutable.size() < FETCH_HITS_MIN)
                number = hitsInternalMutable.size() + FETCH_HITS_MIN;

            while (!ensureHitsReadLock.tryLock()) {
                /*
                 * Another thread is already counting, we don't want to straight up block until it's done
                 * as it might be counting/retrieving all results, while we might only want trying to retrieve a small fraction
                 * So instead poll our own state, then if we're still missing results after that just count them ourselves
                 */
                Thread.sleep(50);
                if (doneFiltering || number >= 0 && hitsInternalMutable.size() >= number)
                    return;
            }
            try {
                boolean readAllHits = number < 0;
                EphemeralHit hit = new EphemeralHit();
                while (!doneFiltering && (readAllHits || hitsInternalMutable.size() < number)) {
                 // Abort if asked
                    threadAborter.checkAbort();

                    // Advance to next hit
                    indexInSource++;
                    if (source.hitsStats().processedAtLeast((long)indexInSource + 1)) {
                        source.getEphemeral(indexInSource, hit);
                        if (filterProperty.get(indexInSource).equals(filterValue)) {
                            // Yes, keep this hit
                            hitsInternalMutable.add(hit);
                            hitsCounted++;
                            if (hit.doc() != previousHitDoc) {
                                docsCounted++;
                                docsRetrieved++;
                                previousHitDoc = hit.doc();
                            }
                        }
                    } else {
                        doneFiltering = true;
                        filterProperty.disposeContext(); // we don't need the context information anymore, free memory
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
    }

    @Override
    public boolean doneProcessingAndCounting() {
        return doneFiltering;
    }

    @Override
    public MaxStats maxStats() {
        return MaxStats.NOT_EXCEEDED;
    }

    @Override
    public boolean hasAscendingLuceneDocIds() {
        return ascendingLuceneDocIds;
    }
}
