package nl.inl.blacklab.search.results.stats;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

/**
 * Count docs by keeping track of ids we've seen.
 *
 * Takes more memory and is slower than just counting, but
 * is required if we're iterating through hits where docs aren't
 * clumped together (e.g. after sorting on hit context). Ideally we
 * never do this though.
 */
class ResultsStatsDocSet extends ResultsStats {

    IntSet docsProcessed = new IntOpenHashSet();

    IntSet docsCounted = new IntOpenHashSet();

    private long docsProcessedSaved;

    private long docsCountedSaved;

    private final ResultsStats hitsStats;

    ResultsStatsDocSet(ResultsAwaiter awaiter, ResultsStats hitsStats) {
        super(awaiter);
        this.hitsStats = hitsStats;
    }

    @Override
    public long processedSoFar() {
        checkDone();
        return docsProcessed == null ? docsProcessedSaved : docsProcessed.size();
    }

    @Override
    public long countedSoFar() {
        checkDone();
        return docsCounted == null ? docsCountedSaved : docsCounted.size();
    }

    private boolean checkDone() {
        boolean done = hitsStats.done();
        if (done && docsProcessed != null)
            setDone();
        return done;
    }

    @Override
    public boolean done() {
        return checkDone();
    }

    @Override
    public MaxStats maxStats() {
        return hitsStats.maxStats();
    }

    @Override
    public String toString() {
        return "ResultsStatsSet{" +
                "docsProcessed=" + processedSoFar() +
                ", docsCounted=" + countedSoFar() +
                '}';
    }

    public void add(int doc, boolean processed) {
        if (processed)
            docsProcessed.add(doc);
        docsCounted.add(doc);
    }

    public void setDone() {
        this.docsProcessedSaved = processedSoFar();
        this.docsCountedSaved = countedSoFar();
        docsProcessed = null;
        docsCounted = null;
    }
}
