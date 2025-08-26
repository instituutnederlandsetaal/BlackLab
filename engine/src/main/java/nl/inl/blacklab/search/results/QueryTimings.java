package nl.inl.blacklab.search.results;

import java.util.LinkedHashMap;
import java.util.Map;

import nl.inl.util.Timer;

/** Allows us to record several stages in the process of resolving a query.
 *
 * For example, the time it takes to rewrite the query, create the weight,
 * retrieve hits, sort hits, etc.
 */
public class QueryTimings {

    private final Timer totalTimer = new Timer();

    private final Timer timer = new Timer();

    private final Map<String, Long> timings = new LinkedHashMap<>();

    private long unlabeled;

    /**
     * Add a timing.
     *
     * If the name already existed, will disambiguate by adding a number.
     *
     * @param name stage name
     * @param time time it took
     */
    public void add(String name, long time) {
        String label = name;
        int count = 2;
        while (timings.containsKey(label)) {
            label = name + count;
            count++;
        }
        timings.put(label, time);
    }

    /**
     * Get the map of recorded timings.
     * @return the map of recorded timings
     */
    public Map<String, Long> map() {
        return timings;
    }

    /**
     * Summarize the recorded timings.
     *
     * @return an overview of the recorded timings
     */
    public String summarize() {
        if (timer.elapsed() > 0)
            record("BEFORE-SUMMARIZE");
        StringBuilder b = new StringBuilder();
//        for (Map.Entry<String, Long> e: timings.entrySet()) {
//            b.append(String.format("  %s: %d ms\n", e.getKey(), e.getValue()));
//        }
        b.append(String.format("  TOTAL: %d ms\n", totalTimer.elapsed()));
        return b.toString();
    }

    /**
     * Start timing a new stage.
     *
     * The name of the stage isn't needed here yet, but should be passed to {@link #record(String)}
     * when the stage finishes.
     */
    public QueryTimings start() {
        if (timer.elapsed() > 0) {
            unlabeled += timer.elapsed();
            timer.reset();
        }
        return this;
    }

    /**
     * Stop timing the current stage and record the time.
     *
     * This will also record any time that passed between the previous call to {@link #record(String)} and
     * the current call to {@link #start()} as "BEFORE-<name>", so we can check if we've missing any significant
     * amount of time between the stages we're timing.
     *
     * @param name the name of the stage, e.g. "sort"
     */
    public void record(String name) {
        if (unlabeled > 0) {
            add("BEFORE-" + name, unlabeled);
            unlabeled = 0;
        }
        add(name, timer.elapsed());
        timer.reset();
    }

    /**
     * Check if no timings have been recorded yet.
     *
     * @return true if no timings have been recorded
     */
    public boolean isEmpty() {
        return timings.isEmpty();
    }

    /**
     * Remove all recorded timings and reset the timer.
     */
    public void clear() {
        timings.clear();
        timer.reset();
        totalTimer.reset();
    }
}
