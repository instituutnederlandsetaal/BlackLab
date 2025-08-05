package nl.inl.blacklab.search.lucene.optimize;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.NfaTwoWay;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryFiSeq;
import nl.inl.util.LuceneUtil;

/**
 * Tries to optimize the query using "forward index matching" (also called NFA
 * matching because it uses a nondeterministic finite automaton).
 *
 * Looks for adjacent clauses that may benefit from this. One of the clauses would
 * be resolved normally using Lucene's reverse index, yielding a list of matches.
 * We would then use the forward index to see if these are actual matches by checking
 * if the other clause actually occurs next to the match found using the reverse index.
 *
 * This works best when one clause has few matches while the other has very many.
 * The first clause would be matched traditionally using Lucene's reverse index, while
 * the second, much more frequent clause would be matched using the forward index.
 *
 * If such a situation is found, we convert the clause to be matched using the forward
 * index to an NFA (nondeterministic finite automaton) and use the other as the "anchor"
 * (because a forward index matching operations always needs a starting position).
 * Together they are combined in a FISEQ (forward index sequence) operation which, like
 * explained above, will first find infrequent matches using Lucene, then decide using
 * the forward index if they are actual matches or not.
 *
 * Checking this for all adjacent clauses (repeatedly) can be a costly operation, so
 * you might want to disable this if you have high query volume and your indexes are not
 * very large.
 */
public class ClauseCombinerNfa extends ClauseCombiner {

    protected static final Logger logger = LogManager.getLogger(ClauseCombinerNfa.class);

    private static final int FORWARD_PRIORITY = 10_000_000;

    // NOTE: BACKWARD_PRIORITY has slightly different value to prevent FindBugs "same code for two branches" warning
    /**
     * NOTE: this used to be twice the value of FORWARD_PRIORITY, in an attempt to
     * prevent building NFAs in different directions, resulting in a suboptimal
     * result, but that actually causes good optimization opportunities to be
     * skipped altogether.
     */
    private static final int BACKWARD_PRIORITY = 10_000_001;

    /**
     * The maximum value of nfaFactor, meaning "make as many NFAs as possible".
     */
    public static final long MAX_NFA_MATCHING = Long.MAX_VALUE;

    /**
     * The minimum value of nfaFactor, meaning "make no NFAs".
     */
    public static final long NO_NFA_MATCHING = 0;

    /**
     * The default value of nfaThreshold.
     */
    public static long defaultForwardIndexMatchingThreshold = 900; //DISABLE: NO_NFA_MATCHING;

    /**
     * Indicates how expensive fetching a lot of term positions from Lucene is; Used
     * to calculate the cost of "regular" matching.
     *
     * Higher values means "regular" (reverse) matching is considered relatively cheaper.
     */
    private static final long TERM_FREQ_DIVIDER = 500;

    /**
     * What we multiply our calculated cost ratio by to get an integer in a
     * reasonable range.
     */
    private static final long COST_RATIO_CONSTANT_FACTOR = 1000;

    /**
     * Should we try forward index matching at all or skip it altogether?
     */
    private static boolean enableForwardIndexmatching = true;

    /**
     * The ratio of estimated numbers of hits that we use to decide whether or not
     * to try NFA-matching with two clauses / subsequences. The lower the number,
     * the more we use NFA-matching.
     *
     * (we compare this to the absolute "combinability factor"; see below)
     */
    private static long nfaThreshold = defaultForwardIndexMatchingThreshold;

    /**
     * Don't NFA optimization if there's too few unique terms?
     * (disable for testing)
     */
    private static boolean onlyUseNfaForManyUniqueTerms = true;

    public static void setDefaultForwardIndexMatchingThreshold(long threshold) {
        ClauseCombinerNfa.defaultForwardIndexMatchingThreshold = threshold;
    }

    public static void setOnlyUseNfaForManyUniqueTerms(boolean onlyUseNfaForManyUniqueTerms) {
        ClauseCombinerNfa.onlyUseNfaForManyUniqueTerms = onlyUseNfaForManyUniqueTerms;
    }

    public static void setNfaThreshold(long nfaThreshold) {
        ClauseCombinerNfa.nfaThreshold = nfaThreshold;
    }

    public static long getNfaThreshold() {
        return ClauseCombinerNfa.nfaThreshold;
    }

    public static void setForwardIndexMatchingEnabled(boolean doNfaMatching) {
        enableForwardIndexmatching = doNfaMatching;
    }

    private static boolean isForwardIndexMatchingEnabled() {
        return enableForwardIndexmatching && nfaThreshold > NO_NFA_MATCHING;
    }

    /**
     * Determines the best direction for NFA and calculates a measure for how desirable NFA matching in this direction is.
     *
     * Forward NFA matching means: the left clause is resolved conventionally (using Lucene reverse index); the right clause
     * is then resolved with NFA matching using the forward index.
     *
     * Backward NFA matching means the opposite: the right clause is resolved conventionally, after which the left clause is
     * resolved with NFA matching using the forward index.
     *
     * Returns a number that indicates NFA matching desirability and direction. 0 means not possible/not desirable.
     * Positive numbers mean forward NFA matching is possible/preferred; the higher, the more desirable it is.
     * Negative numbers mean backward NFA matching is possible/preferred; the more negative, the more desirable it is.
     *
     * @param left left clause
     * @param right right clause
     * @param reader index
     * @return the "combinability factor"
     */
    private static long getFactor(BLSpanQuery left, BLSpanQuery right, IndexReader reader) {
        if (!isForwardIndexMatchingEnabled())
            return 0;

        // Estimate the performance cost of matching the whole sequence using reverse matching.
        // (this number is a very rough estimation of the expected number of results)
        long numLeft = Math.max(1, left.reverseMatchingCost(reader));
        long numRight = Math.max(1, right.reverseMatchingCost(reader));
        long seqReverseCost = Math.min(numLeft, numRight) + (numLeft + numRight) / TERM_FREQ_DIVIDER;

        // Estimate the performance cost of matching either clause using forward matching.
        // (this number doesn't really mean anything in isolation, only in comparison to other NFA matching costs)
        int fiCostLeft = left.forwardMatchingCost();
        int fiCostRight = right.forwardMatchingCost();

        // Calculate the ratio of NFA matching versus conventional (reverse) matching the whole sequence,
        // for both directions. The lower the number, the better the NFA approach is.
        long costNfaToReverseForward = COST_RATIO_CONSTANT_FACTOR * numLeft * fiCostRight / seqReverseCost;
        long costNfaToReverseBackward = COST_RATIO_CONSTANT_FACTOR * numRight * fiCostLeft / seqReverseCost;

        // Is forward and backward matching even possible?
        boolean backwardPossible = left.canMakeNfa() && !right.matchesEmptySequence();
        boolean forwardPossible = right.canMakeNfa() && !left.matchesEmptySequence();

        //fp1 bp1 rf242624 rb2568 fil5 fir1 nl27114064 nr57411
        //factor == -2569, abs(factor) > nfaThreshold (2000)
        boolean traceOptimization = BlackLab.config().getLog().getTrace().isOptimization();
        if (traceOptimization) {
            logger.debug(String.format("(CCNFA: fp%d bp%d rf%d rb%d fil%d fir%d nl%d nr%d)",
                    forwardPossible ? 1 : 0,
                    backwardPossible ? 1 : 0,
                    costNfaToReverseForward,
                    costNfaToReverseBackward,
                    fiCostLeft,
                    fiCostRight,
                    numLeft,
                    numRight));
        }

        // Figure out whether forward or backward is best and return the appropriate number.
        // (the number returned is the ratio of NFA to reverse matching calculated above for the
        //  direction chosen, with the number negative if backward NFA matching was chosen. 1 is added/subtracted
        //  so we don't have a +0/-0 issue and 0 can be reserved to mean "not possible/desirable at all")
        if (forwardPossible || backwardPossible) {
            // Possible.
            if (forwardPossible && backwardPossible) {
                // Both possible; choose the best
                if (costNfaToReverseBackward >= costNfaToReverseForward) {
                    // Forward
                    return costNfaToReverseForward + 1;
                }
                // Backward
                return -costNfaToReverseBackward - 1;
            }
            // One possibility; which one?
            if (forwardPossible)
                return costNfaToReverseForward + 1;
            return -costNfaToReverseBackward - 1;
        }
        return 0; // not possible
    }

    @Override
    public int priority(BLSpanQuery left, BLSpanQuery right, IndexReader reader) {
        boolean traceOptimization = BlackLab.config().getLog().getTrace().isOptimization();
        if (!isForwardIndexMatchingEnabled()) {
            if (traceOptimization)
                logger.debug("(CCNFA: nfa matching switched off)");
            return CANNOT_COMBINE;
        }

        long factor = getFactor(left, right, reader);
        if (traceOptimization)
            logger.debug("(CCNFA: factor == " + factor + ")");
        if (factor == 0) {
            if (traceOptimization)
                logger.debug("(CCNFA: cannot combine)");
            return CANNOT_COMBINE;
        }
        long absFactor = Math.abs(factor);
        if (absFactor > nfaThreshold) {
            if (traceOptimization)
                logger.debug("(CCNFA: abs(factor) > nfaThreshold (" + nfaThreshold + "))");
            return CANNOT_COMBINE;
        }

        if (onlyUseNfaForManyUniqueTerms) {
            long maxTermsRight = LuceneUtil.getMaxTermsPerLeafReader(reader, right.getRealField());
            long maxTermsLeft = LuceneUtil.getMaxTermsPerLeafReader(reader, left.getRealField());
            if (traceOptimization)
                logger.debug("(CCNFA: maxTermsLeft=" + maxTermsLeft + ", maxTermsRight=" + maxTermsRight + ")");
            if (factor > 0 && maxTermsRight < 10_000 ||
                factor < 0 && maxTermsLeft < 10_000) {

                // Don't make NFA for fields with very few unique terms (e.g. PoS), because it won't be faster.
                return CANNOT_COMBINE;
            }
        }

        return factor > 0 ? FORWARD_PRIORITY - (int) (10_000 / absFactor)
                : BACKWARD_PRIORITY - (int) (10_000 / absFactor);
    }

    @Override
    public BLSpanQuery combine(BLSpanQuery left, BLSpanQuery right, IndexReader reader) {
        // Could we make an NFA out of this clause?
        long factor = getFactor(left, right, reader);
        if (factor == 0)
            throw new UnsupportedOperationException("Cannot combine " + left + " and " + right);
        if (factor > 0) {
            // Forward (i.e. left is anchor, right is NFA)
            if (left instanceof SpanQueryFiSeq && ((SpanQueryFiSeq) left).getDirection() == SpanQueryFiSeq.DIR_TO_RIGHT) {
                // Existing forward FISEQ; add NFA to it (re-use fiAccessor so properties get same index).
                return ((SpanQueryFiSeq) left).appendNfa(right);
            }
            // New FISEQ.
            ForwardIndexAccessor fiAccessor = right.getAnnotatedField().forwardIndexAccessor();
            NfaTwoWay nfaTwoWay = right.getNfaTwoWay(fiAccessor, SpanQueryFiSeq.DIR_TO_RIGHT);
            return new SpanQueryFiSeq(left, SpanQueryFiSeq.END_OF_ANCHOR, nfaTwoWay, right, SpanQueryFiSeq.DIR_TO_RIGHT, fiAccessor);
        }

        // Backward (i.e. right is anchor, left is NFA)
        if (right instanceof SpanQueryFiSeq && ((SpanQueryFiSeq) right).getDirection() == SpanQueryFiSeq.DIR_TO_LEFT) {
            // Existing backward FISEQ; add NFA to it (re-use fiAccessor so properties get same index).
            return ((SpanQueryFiSeq) right).appendNfa(left);
        }
        // New FISEQ.
        ForwardIndexAccessor fiAccessor = left.getAnnotatedField().forwardIndexAccessor();
        NfaTwoWay nfaTwoWay = left.getNfaTwoWay(fiAccessor, SpanQueryFiSeq.DIR_TO_LEFT);
        return new SpanQueryFiSeq(right, SpanQueryFiSeq.START_OF_ANCHOR, nfaTwoWay, left, SpanQueryFiSeq.DIR_TO_LEFT, fiAccessor);
    }

    @Override
    public String toString() {
        return "CCNFA";
    }

}
