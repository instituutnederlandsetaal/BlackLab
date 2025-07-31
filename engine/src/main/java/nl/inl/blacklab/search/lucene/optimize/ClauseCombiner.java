package nl.inl.blacklab.search.lucene.optimize;

import java.util.HashSet;
import java.util.Set;

import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.search.lucene.BLSpanQuery;

public abstract class ClauseCombiner {

    public static final int CANNOT_COMBINE = Integer.MAX_VALUE;

    public abstract int priority(BLSpanQuery left, BLSpanQuery right, IndexReader reader);

    public abstract BLSpanQuery combine(BLSpanQuery left, BLSpanQuery right, IndexReader reader);

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
