package nl.inl.blacklab.search.fimatch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class NfaStateAnd extends NfaState {

    final List<NfaState> nextStates;

    public NfaStateAnd(List<NfaState> nextStates) {
        this.nextStates = new ArrayList<>(nextStates);
    }

    public NfaStateAnd() {
        this.nextStates = new ArrayList<>();
    }

    @Override
    public boolean findMatchesInternal(ForwardIndexDocument fiDoc, int pos, int direction, Set<Integer> matchEnds) {
        // Split state. Find matches for all alternatives.
        Set<Integer> newHitsFound = null;
        for (NfaState nextState : nextStates) {
            Set<Integer> matchesForClause = new HashSet<>();
            if (!nextState.findMatchesInternal(fiDoc, pos, direction, matchesForClause))
                return false; // short-circuit
            if (newHitsFound == null) {
                newHitsFound = matchesForClause;
            } else {
                // Calculate intersection
                newHitsFound.retainAll(matchesForClause);
                if (newHitsFound.isEmpty())
                    return false; // no hits left; short-circuit
            }
        }
        if (matchEnds != null)
            matchEnds.addAll(newHitsFound);
        return true;
    }

    @Override
    void fillDangling(NfaState state) {
        for (int i = 0; i < nextStates.size(); i++) {
            if (nextStates.get(i) == null)
                nextStates.set(i, state);
        }
    }

    @Override
    NfaStateAnd copyInternal(Collection<NfaState> dangling, Map<NfaState, NfaState> copiesMade, Consumer<NfaState> onCopyState) {
        List<NfaState> clauseCopies = new ArrayList<>();
        boolean hasNulls = false;
        NfaStateAnd copy = new NfaStateAnd();
        copiesMade.put(this, copy);
        for (NfaState nextState : nextStates) {
            if (nextState == null)
                hasNulls = true;
            else
                nextState = nextState.copy(dangling, copiesMade, onCopyState);
            clauseCopies.add(nextState);
        }
        copy.nextStates.addAll(clauseCopies);
        if (hasNulls && dangling != null)
            dangling.add(copy);
        return copy;
    }

    @Override
    public void setNextState(int i, NfaState state) {
        this.nextStates.set(i, state);
    }

    @Override
    public boolean matchesEmptySequence(Set<NfaState> statesVisited) {
        if (statesVisited.contains(this)) {
            // We've found a cycle. Stop processing, and just return the
            // "safest" (least-guarantee) answer. In this case: we can't
            // guarantee that this DOESN'T match the empty sequence.
            return true;
        }
        statesVisited.add(this);
        for (NfaState nextState : nextStates) {
            if (!nextState.matchesEmptySequence(statesVisited))
                return false;
        }
        return true;
    }

    @Override
    public boolean hitsAllSameLength(Set<NfaState> statesVisited) {
        if (statesVisited.contains(this)) {
            // We've found a cycle. Stop processing, and just return the
            // "safest" (least-guarantee) answer. In this case: we can't
            // guarantee that hits are all the same length.
            return false;
        }
        statesVisited.add(this);
        for (NfaState nextState : nextStates) {
            // If any of the following states has all-same-length hits,
            // this state must also have them.
            if (nextState.hitsAllSameLength(statesVisited))
                return true;
        }
        return true;
    }

    @Override
    public int hitsLengthMin(Set<NfaState> statesVisited) {
        int hitLengthMin = Integer.MAX_VALUE;
        if (statesVisited.contains(this)) {
            // We've found a cycle. Stop processing, and just return the
            // "safest" (least-guarantee) answer. In this case: the smallest
            // hit might be 0 long.
            return 0;
        }
        statesVisited.add(this);
        for (NfaState nextState : nextStates) {
            int i = nextState.hitsLengthMin(statesVisited);
            if (i < hitLengthMin)
                hitLengthMin = i;
        }
        return hitLengthMin;
    }

    @Override
    public int hitsLengthMax(Set<NfaState> statesVisited) {
        int hitLengthMax = 0;
        if (statesVisited.contains(this)) {
            // We've found a cycle. Stop processing, and just return the
            // "safest" (least-guarantee) answer. In this case: the largest
            // hit might be "infinitely" large.
            return Integer.MAX_VALUE;
        }
        statesVisited.add(this);
        for (NfaState nextState : nextStates) {
            int i = nextState.hitsLengthMax(statesVisited);
            if (i > hitLengthMax)
                hitLengthMax = i;
        }
        return hitLengthMax;
    }

    @Override
    protected String dumpInternal(Map<NfaState, Integer> stateNrs) {
        String b = nextStates.stream().map(s -> dump(s, stateNrs)).collect(Collectors.joining(","));
        return "AND(" + b + ")";
    }

    @Override
    void lookupAnnotationNumbersInternal(ForwardIndexAccessor fiAccessor, Map<NfaState, Boolean> statesVisited) {
        for (NfaState s : nextStates) {
            if (s != null)
                s.lookupAnnotationNumbers(fiAccessor, statesVisited);
        }
    }

    @Override
    protected void finishInternal(Set<NfaState> visited) {
        for (int i = 0; i < nextStates.size(); i++) {
            NfaState s = nextStates.get(i);
            if (s == null)
                nextStates.set(i, match());
            else
                s.finish(visited);
        }
    }



}
