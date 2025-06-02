package nl.inl.blacklab.search.lucene.optimize;

import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.search.lucene.BLSpanQuery;

/**
 * Some types of clauses try to "gobble up" adjacent tokens in order to improve
 * optimization.
 */
class ClauseCombinerInternalisation extends ClauseCombiner {

    private static final int PRIORITY = 20;

    enum Type {
        INTERNALIZE_RIGHT_NEIGHBOUR,
        INTERNALIZE_LEFT_NEIGHBOUR,
    }

    Type getType(BLSpanQuery left, BLSpanQuery right) {
        if (left.canInternalizeNeighbour(right, true))
            return Type.INTERNALIZE_RIGHT_NEIGHBOUR;
        if (right.canInternalizeNeighbour(left, false))
            return Type.INTERNALIZE_LEFT_NEIGHBOUR;
        return null;
    }

    @Override
    public int priority(BLSpanQuery left, BLSpanQuery right, IndexReader reader) {
        return getType(left, right) == null ? CANNOT_COMBINE : PRIORITY;
    }

    @Override
    public BLSpanQuery combine(BLSpanQuery left, BLSpanQuery right, IndexReader reader) {
        return switch (getType(left, right)) {
            case INTERNALIZE_LEFT_NEIGHBOUR -> right.internalizeNeighbour(left, false);
            case INTERNALIZE_RIGHT_NEIGHBOUR -> left.internalizeNeighbour(right, true);
            default -> throw new UnsupportedOperationException("Cannot combine " + left + " and " + right);
        };
    }

    @Override
    public String toString() {
        return "CCInternalisation";
    }
}
