package nl.inl.blacklab.search.results.stats;

/** The number of hits to process and count.
 * <p>
 * In BlackLab, you can set a maximum number of hits to process.
 * This is how many hits are actually retrieved. Hits after this
 * are effectively ignored when paging through, sorting or grouping
 * hits.
 * <p>
 * There's a separate maximum number of hits to count. After it hits
 * the maximum to process, BlackLab will continue counting but not
 * processing (storing) any more hits. People can still see the total
 * number of hits, even if they can't see all the hits themselves.
 * <p>
 * Be careful not to rely on grouped results when the maximum hits
 * to process was exceeded.
 */
public interface MaxStats {

    static MaxStats get(boolean tooManyToProcess, boolean tooManyToCount) {
        return new MaxStatsStatic(tooManyToProcess, tooManyToCount);
    }

    MaxStats NOT_EXCEEDED = new MaxStats() {
        @Override
        public boolean isTooManyToProcess() {
            return false;
        }

        @Override
        public boolean isTooManyToCount() {
            return false;
        }
    };

    boolean isTooManyToProcess();

    boolean isTooManyToCount();
}
