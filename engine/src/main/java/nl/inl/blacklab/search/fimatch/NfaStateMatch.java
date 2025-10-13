package nl.inl.blacklab.search.fimatch;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * The match state
 */
public class NfaStateMatch extends NfaState {

    /** Singleton instance of the match final state */
    private static final NfaStateMatch instance = new NfaStateMatch();

    public static NfaStateMatch get() {
        return instance;
    }

    private NfaStateMatch() {}

    @Override
    public boolean findMatchesInternal(ForwardIndexDocument fiDoc, int pos, int direction, Set<Integer> matchEnds) {
        if (matchEnds != null)
            matchEnds.add(pos);
        return true;
    }

    @Override
    void fillDangling(NfaState state) {
        // nothing to do
    }

    @Override
    NfaState copyInternal(Collection<NfaState> dangling, Map<NfaState, NfaState> copiesMade) {
        return this; // immutable, singleton
    }

    @Override
    public void setNextState(int i, NfaState state) {
        throw new UnsupportedOperationException("'Match' state has no next state");
    }

    @Override
    public boolean matchesEmptySequence(Set<NfaState> statesVisited) {
        return true;
    }

    @Override
    public boolean hitsAllSameLength(Set<NfaState> statesVisited) {
        return true;
    }

    @Override
    public int hitsLengthMin(Set<NfaState> statesVisited) {
        return 0;
    }

    @Override
    public int hitsLengthMax(Set<NfaState> statesVisited) {
        return 0;
    }

    @Override
    protected String dumpInternal(Map<NfaState, Integer> stateNrs) {
        return "MATCH()";
    }

    @Override
    void lookupAnnotationNumbersInternal(ForwardIndexAccessor fiAccessor, Map<NfaState, Boolean> statesVisited) {
        // NOP
    }

    @Override
    protected void finishInternal(Set<NfaState> visited) {
        // NOP
    }

}
