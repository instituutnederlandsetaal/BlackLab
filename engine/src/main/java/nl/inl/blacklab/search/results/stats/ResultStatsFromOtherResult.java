package nl.inl.blacklab.search.results.stats;

import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.search.results.Results;
import nl.inl.blacklab.search.results.docs.DocGroups;
import nl.inl.blacklab.search.results.docs.DocResults;
import nl.inl.blacklab.search.results.hitresults.HitGroups;
import nl.inl.blacklab.search.results.hitresults.HitResults;

/** A search result that tracks and records the number of results in another search.
 *
 * This is e.g. used for the running count of hits in a search. When the search is done,
 * the count is saved so that it can be used later without keeping the entire results
 * object in memory.
 */
public class ResultStatsFromOtherResult extends ResultsStats {

    public enum CountType {
        RESULTS, // number of results
        HITS,    // number of hits represented by results
        DOCS     // number of docs represented by results
    }

    private ResultsStats count;

    private boolean wasInterrupted = false;

    /** Type of results we're counting, to report in toString() */
    private final CountType type;

    public ResultStatsFromOtherResult(Results count, CountType type) {
        super();
        setResultsAwaiter(new ResultsAwaiter() {
            @Override
            public boolean processedAtLeast(long lowerBound) {
                update();
                try {
                    return ResultStatsFromOtherResult.this.count.processedAtLeast(lowerBound);
                } catch(InterruptedSearch e) {
                    wasInterrupted = true;
                    throw e;
                }
            }

            @Override
            public long allProcessed() {
                update();
                try {
                    return ResultStatsFromOtherResult.this.count.processedTotal();
                } catch(InterruptedSearch e) {
                    wasInterrupted = true;
                    throw e;
                }
            }

            @Override
            public long allCounted() {
                update();
                try {
                    return ResultStatsFromOtherResult.this.count.countedTotal();
                } catch(InterruptedSearch e) {
                    wasInterrupted = true;
                    throw e;
                }
            }
        });
        this.type = type;
        switch (type) {
        case RESULTS:
            this.count = count.resultsStats();
            break;
        case HITS:
            if (count instanceof HitResults) {
                this.count = count.resultsStats();
            } else if (count instanceof HitGroups) {
                long n = ((HitGroups) count).sumOfGroupSizes();
                this.count = new ResultsStatsSaved(n, n, MaxStats.NOT_EXCEEDED);
            } else if (count instanceof DocResults) {
                long n = ((DocResults) count).sumOfGroupSizes();
                this.count = new ResultsStatsSaved(n, n, MaxStats.NOT_EXCEEDED);
            } else if (count instanceof DocGroups) {
                throw new UnsupportedOperationException("Cannot get hits count from DocGroups");
            }
            break;
        case DOCS:
            if (count instanceof HitResults) {
                this.count = ((HitResults) count).docsStats();
            } else if (count instanceof HitGroups) {
                throw new UnsupportedOperationException("Cannot get docs count from HitGroups");
            } else if (count instanceof DocResults) {
                this.count = count.resultsStats();
            } else if (count instanceof DocGroups) {
                long n = ((DocGroups) count).sumOfGroupSizes();
                this.count = new ResultsStatsSaved(n, n, MaxStats.NOT_EXCEEDED);
            }
            break;
        }
        update();
    }

    private void update() {
        if (count.done()) {
            // We were monitoring the count from a results object that stores all the results.
            // In order to allow that to be garbage collected when possible, disengage from
            // the search object and save the totals.
            count = count.save();
        }
    }

    @Override
    public long processedSoFar() {
        update();
        try {
            return count.processedSoFar();
        } catch(InterruptedSearch e) {
            wasInterrupted = true;
            throw e;
        }
    }

    @Override
    public long countedSoFar() {
        update();
        try {
            return count.countedSoFar();
        } catch(InterruptedSearch e) {
            wasInterrupted = true;
            throw e;
        }
    }

    @Override
    public boolean done() {
        update();
        try {
            return count.done();
        } catch(InterruptedSearch e) {
            wasInterrupted = true;
            throw e;
        }
    }

    @Override
    public MaxStats maxStats() {
        update();
        try {
            return count.maxStats();
        } catch(InterruptedSearch e) {
            wasInterrupted = true;
            throw e;
        }
    }

    @Override
    public String toString() {
        return "ResultCount [count=" + count + ", type=" + type + ", wasInterrupted=" + wasInterrupted + "]";
    }

}
