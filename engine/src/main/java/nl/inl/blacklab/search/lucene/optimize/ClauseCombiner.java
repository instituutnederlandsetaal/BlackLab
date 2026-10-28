package nl.inl.blacklab.search.lucene.optimize;

import java.util.HashSet;
import java.util.Set;

import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.search.lucene.BLSpanQuery;

public abstract class ClauseCombiner {

    public static final int CANNOT_COMBINE = Integer.MAX_VALUE;

    /**
     * Determine the priority of combining the two clauses. Lower numbers are higher priority.
     *
     * Returns CANNOT_COMBINE if they can't be combined.
     *
     * @param left left clause
     * @param right right clause
     * @param reader index reader
     * @return priority of combining the two clauses
     */
    public abstract int priority(BLSpanQuery left, BLSpanQuery right, IndexReader reader);

    /**
     * Combine the two clause's using our optimization strategy. Return null if we can't combine them.
     *
     * @param left left clause
     * @param right right clause
     * @param reader index reader
     * @return combined clause
     * @throws UnsupportedOperationException if the clauses can't be combined
     */
    public abstract BLSpanQuery combine(BLSpanQuery left, BLSpanQuery right, IndexReader reader);

    /**
     * Check if the two clauses can be combined using our optimization strategy.
     *
     * @param left left clause
     * @param right right clause
     * @param reader index reader
     * @return true if they can be combined, false if not
     */
    public boolean canCombine(BLSpanQuery left, BLSpanQuery right, IndexReader reader) {
        return priority(left, right, reader) != CANNOT_COMBINE;
    }

    public static Set<ClauseCombiner> all() {
        Set<ClauseCombiner> all = new HashSet<>();
        all.add(new ClauseCombinerRepetition());
        all.add(new ClauseCombinerInternalisation());
        all.add(new ClauseCombinerAnyExpansion());
        all.add(new ClauseCombinerDefaultValue());
        all.add(new ClauseCombinerNot());
        all.add(new ClauseCombinerNfa());
        return all;
    }
    
    @Override
    public abstract String toString();
}
