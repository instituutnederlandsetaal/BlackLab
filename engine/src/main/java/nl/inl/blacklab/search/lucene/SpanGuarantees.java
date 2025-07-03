package nl.inl.blacklab.search.lucene;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.apache.lucene.search.DocIdSetIterator;

/**
 * Guarantee methods for SpanQuery/Spans objects.
 *
 * These methods that assert facts about the hits produced by a SpanQuery or Spans object,
 * that can help with optimization.
 */
public interface SpanGuarantees {
    /**
     * Doesn't guarantee anything.
     */
    SpanGuarantees NONE = new SpanGuarantees() {
        @Override
        public boolean okayToInvertForOptimization() {
            return false;
        }

        @Override
        public boolean isSingleTokenNot() {
            return false;
        }

        @Override
        public boolean hitsAllSameLength() {
            return false;
        }

        @Override
        public int hitsLengthMin() {
            return 0;
        }

        @Override
        public int hitsLengthMax() {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean hitsEndPointSorted() {
            return false;
        }

        @Override
        public boolean hitsStartPointSorted() {
            return false;
        }

        @Override
        public boolean hitsHaveUniqueStart() {
            return false;
        }

        @Override
        public boolean hitsHaveUniqueEnd() {
            return false;
        }

        @Override
        public boolean isSingleAnyToken() {
            return false;
        }
    };

    /**
     * Guarantees that the hits are sorted by start point and are unique.
     */
    SpanGuarantees UNIQUE = new SpanGuaranteesAdapter() {
        @Override
        public boolean hitsHaveUniqueStartEnd() {
            return true;
        }

        @Override
        public boolean hitsHaveUniqueStart() {
            return true;
        }

        @Override
        public boolean hitsHaveUniqueEnd() {
            return true;
        }
    };

    /**
     * Only guarantees that the hits are sorted by start point.
     *
     * (normally true for regular Lucene queries, not always true in BlackLab)
     */
    SpanGuarantees SORTED = new SpanGuaranteesAdapter(NONE) {
        @Override
        public boolean hitsStartPointSorted() {
            return true;
        }
    };

    /**
     * Guarantees that the hits are sorted by start point and are unique.
     */
    SpanGuarantees SORTED_UNIQUE = new SpanGuaranteesAdapter(UNIQUE) {
        @Override
        public boolean hitsStartPointSorted() {
            return true;
        }
    };

    /**
     * Only guarantees that the hits are sorted by start point.
     *
     * (normally true for regular Lucene queries, not always true in BlackLab)
     */
    SpanGuarantees END_SORTED = new SpanGuaranteesAdapter(NONE) {
        @Override
        public boolean hitsEndPointSorted() {
            return true;
        }
    };

    /**
     * Only guarantees that the hits are sorted by start point.
     *
     * (normally true for regular Lucene queries, not always true in BlackLab)
     */
    SpanGuarantees END_SORTED_UNIQUE = new SpanGuaranteesAdapter(UNIQUE) {
        @Override
        public boolean hitsEndPointSorted() {
            return true;
        }
    };

    /**
     * Guarantees for a regular term query.
     */
    SpanGuarantees TERM = new SpanGuarantees() {
        @Override
        public boolean okayToInvertForOptimization() {
            return false;
        }

        @Override
        public boolean isSingleTokenNot() {
            return false;
        }

        @Override
        public boolean hitsAllSameLength() {
            return true;
        }

        @Override
        public int hitsLengthMin() {
            return 1;
        }

        @Override
        public int hitsLengthMax() {
            return 1;
        }

        @Override
        public boolean hitsEndPointSorted() {
            return true;
        }

        @Override
        public boolean hitsStartPointSorted() {
            return true;
        }

        @Override
        public boolean hitsHaveUniqueStart() {
            return true;
        }

        @Override
        public boolean hitsHaveUniqueEnd() {
            return true;
        }

        @Override
        public boolean hitsHaveUniqueStartEnd() {
            return true;
        }

        @Override
        public boolean hitsCanOverlap() {
            return false;
        }

        @Override
        public boolean isSingleAnyToken() {
            return false;
        }
    };

    static <T extends DocIdSetIterator> SpanGuarantees from(DocIdSetIterator in) {
        if (in instanceof SpanGuaranteeGiver)
            return ((SpanGuaranteeGiver)in).guarantees();
        return NONE;
    }

    static <T extends SpanGuaranteeGiver> List<SpanGuarantees> from(T[] clauses) {
        Stream<T> stream = Arrays.stream(clauses);
        return from(stream);
    }

    static <T extends SpanGuaranteeGiver> List<SpanGuarantees> from(List<T> clauses) {
        Stream<T> stream = clauses.stream();
        return from(stream);
    }

    static <T extends SpanGuaranteeGiver> List<SpanGuarantees> from(Stream<T> stream) {
        return stream.map(T::guarantees).toList();
    }

    /**
     * Is it okay to invert this query for optimization?
     * <p>
     * Heuristic used to determine when to optimize a query by inverting one or more
     * of its subqueries.
     *
     * @return true if it is, false if not
     */
    boolean okayToInvertForOptimization();

    /**
     * Is this query only a negative clause, producing all tokens that don't satisfy
     * certain conditions?
     * <p>
     * Used for optimization decisions, i.e. in BLSpanOrQuery.rewrite().
     *
     * @return true if it's negative-only, false if not
     */
    boolean isSingleTokenNot();

    /**
     * Are all our hits single tokens?
     *
     * @return true if they are, false if not
     */
    default boolean producesSingleTokens() {
        return hitsAllSameLength() && hitsLengthMin() == 1;
    }

    /**
     * Do our hits have constant length?
     *
     * @return true if they do, false if not
     */
    boolean hitsAllSameLength();

    /**
     * How long could our shortest hit be?
     *
     * @return length of the shortest hit possible
     */
    int hitsLengthMin();

    /**
     * How long could our longest hit be?
     *
     * @return length of the longest hit possible, or Integer.MAX_VALUE if unlimited
     */
    int hitsLengthMax();

    /**
     * When hit B follows hit A, is it guaranteed that B.end &gt;= A.end? Also, if
     * A.end == B.end, is B.start &gt; A.start?
     *
     * @return true if this is guaranteed, false if not
     */
    boolean hitsEndPointSorted();

    /**
     * When hit B follows hit A, is it guaranteed that B.start &gt;= A.start? Also,
     * if A.start == B.start, is B.end &gt; A.end?
     *
     * @return true if this is guaranteed, false if not
     */
    boolean hitsStartPointSorted();

    /**
     * Is it guaranteed that no two hits have the same start position?
     *
     * @return true if this is guaranteed, false if not
     */
    boolean hitsHaveUniqueStart();

    /**
     * Is it guaranteed that no two hits have the same end position?
     *
     * @return true if this is guaranteed, false if not
     */
    boolean hitsHaveUniqueEnd();

    /**
     * Is it guaranteed that no two hits are completely identical?
     * <p>
     * Two hits are considered completely identical if they have the
     * same start and end position AND the same match info (if any).
     * If there is no match info, this method always returns the same as {@link #hitsHaveUniqueStartEnd()}.
     *
     * @return true if this is guaranteed, false if not
     */
    default boolean hitsHaveUniqueStartEndAndInfo() {
        // Subclass may add additional guarantee
        return hitsHaveUniqueStartEnd();
    }

    /**
     * Is it guaranteed that no two hits have identical start and end?
     *
     * If this returns true, {@link #hitsHaveUniqueStartEndAndInfo()} must also return true.
     *
     * @return true if this is guaranteed, false if not
     */
    default boolean hitsHaveUniqueStartEnd() {
        return hitsHaveUniqueStart() || hitsHaveUniqueEnd();
    }

    /**
     * Can two hits overlap?
     *
     * @return true if they can, false if not
     */
    default boolean hitsCanOverlap() {
        // Subclasses may know more and therefore be able to guarantee non-overlapping in more cases
        boolean hitsAreDiscrete = hitsAllSameLength() && hitsLengthMax() <= 1 && hitsHaveUniqueStart();
        return !hitsAreDiscrete;
    }

    /**
     * Is this query a single "any token", e.g. one that matches all individual tokens?
     * @return true if it is, false if not
     */
    boolean isSingleAnyToken();
}
