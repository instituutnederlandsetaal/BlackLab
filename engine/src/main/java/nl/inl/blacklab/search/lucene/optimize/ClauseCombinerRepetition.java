package nl.inl.blacklab.search.lucene.optimize;

import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryRepetition;

/**
 * Recognize adjacent identical clauses and combine them.
 */
class ClauseCombinerRepetition extends ClauseCombiner {

    private static final int PRIORITY = 10;

    @Override
    public int priority(BLSpanQuery left, BLSpanQuery right, IndexReader reader) {
        if (left.equals(right))
            return PRIORITY;
        BLSpanQuery leftCl = left instanceof SpanQueryRepetition ? ((SpanQueryRepetition) left).getClause() : left;
        BLSpanQuery rightCl = right instanceof SpanQueryRepetition ? ((SpanQueryRepetition) right).getClause() : right;
        return leftCl.equals(rightCl) ? PRIORITY : CANNOT_COMBINE;
    }

    @Override
    public BLSpanQuery combine(BLSpanQuery left, BLSpanQuery right, IndexReader reader) {
        if (!canCombine(left, right, reader))
            throw new UnsupportedOperationException("Cannot combine " + left + " and " + right);
        BLSpanQuery leftCl = left;
        int leftMin = 1, leftMax = 1;
        int rightMin = 1, rightMax = 1;
        if (left instanceof SpanQueryRepetition l) {
            leftCl = l.getClause();
            leftMin = l.getMinRep();
            leftMax = l.getMaxRep();
        }
        if (right instanceof SpanQueryRepetition r) {
            rightMin = r.getMinRep();
            rightMax = r.getMaxRep();
        }
        return new SpanQueryRepetition(leftCl, leftMin + rightMin, BLSpanQuery.addMaxValues(leftMax, rightMax));
    }

    @Override
    public String toString() {
        return "CCRepetition";
    }
}
