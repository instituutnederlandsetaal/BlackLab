package nl.inl.blacklab.search.results;

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
