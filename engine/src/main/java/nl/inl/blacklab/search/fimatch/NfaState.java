package nl.inl.blacklab.search.fimatch;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.apache.lucene.index.LeafReaderContext;

/**
 * Represents both a state in an NFA, and a complete NFA with this as the
 * starting state.
 *
 * (Note that the "matching state" is simply represented as null in the NFAs we
 * build, which is convenient when possibly appending other NFAs to it later)
 */
public abstract class NfaState {

    /**
     * Build a token state.
     *
     * @param luceneField what annotation to match
     * @param inputToken what token to match
     * @param nextState what state to go to after a succesful match
     * @return the state object
     */
    public static NfaState token(String luceneField, String inputToken, NfaState nextState) {
        return new NfaStateToken(luceneField, inputToken, nextState);
    }

    /**
     * Build a token state.
     *
     * @param luceneField what annotation to match
     * @param inputTokens what tokens to match
     * @param nextState what state to go to after a succesful match
     * @return the state object
     */
    public static NfaState token(String luceneField, Set<String> inputTokens, NfaState nextState) {
        return new NfaStateToken(luceneField, inputTokens, nextState);
    }

    public static NfaState regex(String luceneField, String pattern, NfaState nextState) {
        return new NfaStateRegex(luceneField, pattern, nextState);
    }

    public static NfaState anyToken(String luceneField, NfaState nextState) {
        return new NfaStateAnyToken(luceneField, nextState);
    }

    /**
     * Build am OR state.
     *
     * @param clausesMayLoopBack if false, no clauses loop back to earlier states,
     *            (a more efficient way of matching can be used in this case)
     * @param nextStates states to try
     * @param clausesAllSameLength are all hits for all clauses the same length?
     *            (used to optimize matching)
     * @return the state object
     */
    public static NfaState or(boolean clausesMayLoopBack, List<NfaState> nextStates, boolean clausesAllSameLength) {
        return clausesMayLoopBack ? new NfaStateOr(nextStates, clausesAllSameLength)
                : new NfaStateOrAcyclic(nextStates, clausesAllSameLength);
    }

    /**
     * Build an AND state.
     *
     * @param clausesMayLoopBack if false, no clauses loop back to earlier states,
     *            (a more efficient way of matching can be used in this case)
     * @param nextStates NFAs that must match
     * @return the state object
     */
    public static NfaState and(boolean clausesMayLoopBack, List<NfaState> nextStates) {
        return clausesMayLoopBack ? new NfaStateAnd(nextStates) : new NfaStateAndAcyclic(nextStates);
    }

    public static NfaState match() {
        return NfaStateMatch.get();
    }

    /**
     * Find all matches for this NFA in the token source.
     *
     * @param fiDoc where to read tokens from
     * @param pos current matching position
     * @param direction matching direction
     * @param matchEnds where to collect the matches found, or null if we don't want
     *            to collect them
     * @return true if any (new) matches were found, false if not
     */
    abstract boolean findMatchesInternal(ForwardIndexDocument fiDoc, int pos, int direction, Set<Integer> matchEnds);

    /**
     * Find all matches for this NFA in the token source.
     *
     * @param fiDoc where to read tokens from
     * @param pos current matching position
     * @param direction matching direction
     * @return the matches found, if any
     */
    public NavigableSet<Integer> findMatches(ForwardIndexDocument fiDoc, int pos, int direction) {
        NavigableSet<Integer> results = new TreeSet<>();
        findMatchesInternal(fiDoc, pos, direction, results);
        return results;
    }

    /**
     * Does the token source match this NFA?
     *
     * @param fiDoc where to read tokens from
     * @param pos current matching position
     * @param direction matching direction
     * @return true if fiDoc matches, false if not
     */
    public boolean matches(ForwardIndexDocument fiDoc, int pos, int direction) {
        return findMatchesInternal(fiDoc, pos, direction, null);
    }

    /**
     * For any dangling output states this state has, fill in the specified state.
     *
     * @param state state to fill in for dangling output states
     */
    abstract void fillDangling(NfaState state);

    /**
     * Does this state have a dangling output?
     * @return true if it has a dangling output, false if not
     */
    abstract boolean hasDangling();

    public void visit(Set<NfaState> visited, Consumer<NfaState> visitor) {
        // See if we've already rewritten this state; if so, return the rewritten state.
        if (!visited.contains(this)) {
            // Not yet visited; do so now.
            visited.add(this);
            if (visitor != null)
                visitor.accept(this);
            getConnectedStates().forEach(state -> {
                if (state != null)
                    state.visit(visited, visitor);
            });
        }
    }

    abstract Collection<NfaState> getConnectedStates();

    public Collection<NfaState> getDangling() {
        // Collect dangling states, i.e. those that have no next state.
        // Note that this is not the same as hasDangling(), which only checks this state.
        Set<NfaState> statesWithDangling = new HashSet<>();
        visit(new HashSet<>(), state -> {
            if (state.hasDangling())
                statesWithDangling.add(state);
        });
        return statesWithDangling;
    }

    protected static NfaState rewriteState(NfaState state, Map<NfaState, NfaState> rewritten,
            BiFunction<NfaState, Map<NfaState, NfaState>, NfaState> rewriter) {
        // See if we've already rewritten this state; if so, return the rewritten state.
        NfaState rewrittenState = rewritten.get(state);
        if (rewrittenState == null) {
            // Not yet rewritten; do so now.
            rewrittenState = rewriter.apply(state, rewritten);
            rewritten.put(state, rewrittenState); // should've been done already in copyInternal(), but ok
        }
        return rewrittenState;
    }

    /**
     * Return a copy of the fragment starting from this state, and collect all
     * (copied) states with dangling outputs.
     *
     * @param dangling where to collect copied states with dangling outputs, or null
     *            if we don't care about these
     * @param onCopyState optional callback to call on each copied state
     * @return the copied fragment
     */
    public final NfaState copy(Collection<NfaState> dangling, Consumer<NfaState> onCopyState) {
        return copy(dangling, new HashMap<>(), onCopyState);
    }

    /**
     * Return a copy of the fragment starting from this state, and collect all
     * (copied) states with dangling outputs.
     *
     * @param dangling where to collect copied states with dangling outputs, or null
     *            if we don't care about these
     * @param copiesMade states copied earlier during this copy operation, so we can
     *            deal with cyclic NFAs (i.e. don't keep copying, re-use the
     *            previous copy)
     * @param onCopyState optional callback to call on each copied state
     * @return the copied fragment
     */
    final NfaState copy(Collection<NfaState> dangling, Map<NfaState, NfaState> copiesMade, Consumer<NfaState> onCopyState) {
        return rewriteState(this, copiesMade, (state, rewritten) -> {
            // Otherwise, copy this state.
            // NOTE: copyInternal() must already add the copied state to copiesMade BEFORE rewriting any other states!
            //       This is important to avoid infinite recursion when copying cyclic NFAs.
            NfaState copiedState = state.copyInternal(dangling, copiesMade, onCopyState);
            if (onCopyState != null)
                onCopyState.accept(copiedState);
            return copiedState;
        });
    }

    /**
     * Return a copy of the fragment starting from this state, and collect all
     * (copied) states with dangling outputs.
     *
     * Subclasses can override this (not copy()), so they don't have to look at
     * copiesMade but can always just create a copy of themselves.
     *
     * IMPORTANT: implementations MUST add the copied state to copiesMade BEFORE
     *            rewriting any other states! Otherwise, infinite recursion could
     *            occur when copying cyclic NFAs.
     *
     * @param dangling where to collect copied states with dangling outputs, or null
     *            if we don't care about these
     * @param copiesMade states copied earlier during this copy operation, so we can
     *            deal with cyclic NFAs (i.e. don't keep copying, re-use the
     *            previous copy)
     * @return the copied fragment
     */
    abstract NfaState copyInternal(Collection<NfaState> dangling, Map<NfaState, NfaState> copiesMade, Consumer<NfaState> onCopyState);

    /**
     * Set the next state for a given input.
     *
     * @param input input
     * @param state next state
     */
    public abstract void setNextState(int input, NfaState state);

    @Override
    public String toString() {
        Map<NfaState, Integer> stateNrs = new HashMap<>();
        return "NFA:" + dump(stateNrs);
    }

    /**
     * Visit each node and replace dangling arrows (nulls) with the match state.
     */
    public final void finish() {
        visit(new HashSet<>(), nfaState -> nfaState.finishInternal());
    }

    /**
     * Visit each node and replace dangling arrows (nulls) with the match state.
     *
     * @param visited nodes visited so far, this one included. finish() uses this to
     *            make sure we don't visit the same node twice, so always call
     *            finish() in your implementations, not finishInternal().
     */
    protected void finishInternal() {
        // Default implementation does nothing, subclasses should override this
        // if they need to do something special when finishing.
    }

    public static String dump(NfaState state, Map<NfaState, Integer> stateNrs) {
        return state == null ? "DANGLING" : state.dump(stateNrs);
    }

    public String dump(Map<NfaState, Integer> stateNrs) {
        Integer n = stateNrs.get(this);
        if (n != null) {
            // If we've already seen this state, just refer to it by number.
            return "#" + n;
        }
        n = stateNrs.size() + 1;
        stateNrs.put(this, n);
        return "#" + n + ":" + dumpInternal(stateNrs);
    }

    protected abstract String dumpInternal(Map<NfaState, Integer> stateNrs);

    /**
     * Does this NFA match the empty sequence?
     * 
     * @param statesVisited states we've already visited, so we can deal with cycles
     * @return true if it matches the empty sequence, false if not
     */
    public abstract boolean matchesEmptySequence(Set<NfaState> statesVisited);

    /**
     * Are all hits from this NFA the same length?
     * 
     * @param statesVisited states we've already visited, so we can deal with cycles
     * @return true if all hits are the same length, false if not
     */
    public abstract boolean hitsAllSameLength(Set<NfaState> statesVisited);

    /**
     * What's the minimum hit length?
     * 
     * @param statesVisited states we've already visited, so we can deal with cycles
     * @return minimum hit length
     */
    public abstract int hitsLengthMin(Set<NfaState> statesVisited);

    /**
     * What's the maximum hit length?
     * 
     * @param statesVisited states we've already visited, so we can deal with cycles
     * @return maximum hit length
     */
    public abstract int hitsLengthMax(Set<NfaState> statesVisited);

    /**
     * Return a copy of this NFA fragment with the specified context.
     *
     * @param lrc context to use
     * @return copy with this context
     */
    public NfaState forLeafReaderContext(LeafReaderContext lrc) {
        return copy(null, new HashMap<>(), state -> state.setLeafReaderContext(lrc));
    }

    /**
     * Sets the LeafReaderContext for this NFA state.
     */
    protected void setLeafReaderContext(LeafReaderContext lrc) {
    }

    public final void lookupAnnotationIndexes(ForwardIndexAccessor fiAccessor) {
        visit(new HashSet<>(), nfaState -> {
            // Actually look up the indexes for the annotations we need
            nfaState.lookupAnnotationIndexesInternal(fiAccessor);
        });
    }

    void lookupAnnotationIndexesInternal(ForwardIndexAccessor fiAccessor) {
        // Default implementation does nothing, subclasses should override this
        // if they need to look up annotation indexes.
    }

    @Override
    public boolean equals(Object obj) {
        // NOTE: We intentionally don't provide our own equals() implementation here, because we care about object
        // identity, not equality (i.e. when copying an NFA, we need to process each state instance once only).
        return super.equals(obj);
    }

}
