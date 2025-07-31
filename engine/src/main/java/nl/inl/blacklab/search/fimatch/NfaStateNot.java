package nl.inl.blacklab.search.fimatch;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class NfaStateNot extends NfaState {

    private NfaState clause;

    private NfaState nextState;

    public NfaStateNot(NfaState clause, NfaState nextState) {
        clause.finish();
        this.clause = clause;
        this.nextState = nextState;
    }

    private NfaStateNot() {
        // OK, used by copyInternal()
    }

    @Override
    boolean findMatchesInternal(ForwardIndexDocument fiDoc, int pos, int direction, Set<Integer> matchEnds) {
        // Is this a valid position? If not, no match.
        if (!fiDoc.validPos(pos))
            return false;
        // Does the clause match?
        boolean clauseMatches = clause.findMatchesInternal(fiDoc, pos, direction, null);
        if (clauseMatches)
            return false;
        // No matches found at this position, therefore this token does match.
        return nextState.findMatchesInternal(fiDoc, pos + direction, direction, matchEnds);
    }

    @Override
    void fillDangling(NfaState state) {
        if (nextState == null)
            nextState = state;
    }

    @Override
    boolean hasDangling() {
        return nextState == null;
    }

    @Override
    Collection<NfaState> getConnectedStates() {
        return List.of(clause, nextState);
    }

    @Override
    NfaState copyInternal(Collection<NfaState> dangling, Map<NfaState, NfaState> copiesMade, Consumer<NfaState> onCopyState) {
        NfaStateNot copy = new NfaStateNot();
        copiesMade.put(this, copy);
        NfaState clauseCopy = clause.copy(null, copiesMade, onCopyState);
        NfaState nextStateCopy = nextState == null ? null : nextState.copy(dangling, copiesMade, onCopyState);
        copy.clause = clauseCopy;
        copy.nextState = nextStateCopy;
        if (nextState == null)
            dangling.add(copy);
        return copy;
    }

    @Override
    public void setNextState(int i, NfaState state) {
        throw new UnsupportedOperationException("Cannot change NOT clause");
    }

    @Override
    public boolean matchesEmptySequence(Set<NfaState> statesVisited) {
        return false;
    }

    @Override
    public boolean hitsAllSameLength(Set<NfaState> statesVisited) {
        return true;
    }

    @Override
    public int hitsLengthMin(Set<NfaState> statesVisited) {
        return 1;
    }

    @Override
    public int hitsLengthMax(Set<NfaState> statesVisited) {
        return 1;
    }

    @Override
    protected String dumpInternal(Map<NfaState, Integer> stateNrs) {
        return "NOT(" + dump(clause, stateNrs) + "," + dump(nextState, stateNrs) + ")";
    }

    @Override
    protected void finishInternal() {
        if (clause == null)
            clause = match();
        if (nextState == null)
            nextState = match();
    }

}
