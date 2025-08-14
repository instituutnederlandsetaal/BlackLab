package nl.inl.blacklab.search.results.stats;

/** The number of hits to process and count.
 *
 * In BlackLab, you can set a maximum number of hits to process.
 * This is how many hits are actually retrieved. Hits after this
 * are effectively ignored when paging through, sorting or grouping
 * hits.
 *
 * There's a separate maximum number of hits to count. After it hits
 * the maximum to process, BlackLab will continue counting but not
 * processing (storing) any more hits. People can still see the total
 * number of hits, even if they can't see all the hits themselves.
 *
 * Be careful not to rely on grouped results when the maximum hits
 * to process was exceeded.
 */
public interface MaxStats {

    static MaxStats get(boolean hitsProcessedExceededMaximum, boolean hitsCountedExceededMaximum) {
        return new MaxStatsStatic(hitsProcessedExceededMaximum, hitsCountedExceededMaximum);
    }

    MaxStats NOT_EXCEEDED = new MaxStats() {
        @Override
        public boolean hitsProcessedExceededMaximum() {
            return false;
        }

        @Override
        public boolean hitsCountedExceededMaximum() {
            return false;
        }
    };

    boolean hitsProcessedExceededMaximum();

    boolean hitsCountedExceededMaximum();
}
