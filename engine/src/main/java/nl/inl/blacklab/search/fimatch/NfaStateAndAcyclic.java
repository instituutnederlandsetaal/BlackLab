package nl.inl.blacklab.search.fimatch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * An AND-node where none of the clauses cycle back to an earlier node. This can
 * be evaluated more efficiently, by finding matches for each of the clauses,
 * and then continuing at nextState from each of the matches found.
 */
public class NfaStateAndAcyclic extends NfaState {

    final List<NfaState> clauses;

    NfaState nextState;

    public NfaStateAndAcyclic(List<NfaState> andClauses) {
        for (NfaState clause : andClauses) {
            clause.finish(new HashSet<>());
        }
        this.clauses = new ArrayList<>(andClauses);
        this.nextState = null;
    }

    public NfaStateAndAcyclic() {
        this.clauses = new ArrayList<>();
        this.nextState = null;
    }

    @Override
    public boolean findMatchesInternal(ForwardIndexDocument fiDoc, int pos, int direction, Set<Integer> matchEnds) {
        // AND state. Find matches for all alternatives.
        Set<Integer> clausesMatchEnds = null;
        Set<Integer> matchEndsThisClause = new HashSet<>();
        for (NfaState clause : clauses) {
            matchEndsThisClause.clear();
            if (!clause.findMatchesInternal(fiDoc, pos, direction, matchEndsThisClause))
                return false; // this clause had no hits; short circuit AND
            if (clausesMatchEnds == null) {
                // First matches found
                clausesMatchEnds = matchEndsThisClause;
                matchEndsThisClause = new HashSet<>();
            } else {
                // Determine intersection with previous matches
                clausesMatchEnds.retainAll(matchEndsThisClause);
                if (clausesMatchEnds.isEmpty())
                    return false; // there are no hits left; short circuit AND
            }
        }
        boolean foundMatch = false;
        if (!clausesMatchEnds.isEmpty()) {
            // Continue matching from the matches to our OR clauses
            for (Integer clauseMatchEnd : clausesMatchEnds) {
                foundMatch |= nextState.findMatchesInternal(fiDoc, clauseMatchEnd, direction, matchEnds);
            }
        }
        return foundMatch;
    }

    @Override
    void fillDangling(NfaState state) {
        if (nextState == null)
            nextState = state;
    }

    @Override
    NfaStateAndAcyclic copyInternal(Collection<NfaState> dangling, Map<NfaState, NfaState> copiesMade, Consumer<NfaState> onCopyState) {
        NfaStateAndAcyclic copy = new NfaStateAndAcyclic();
        copiesMade.put(this, copy);
        List<NfaState> clauseCopies = new ArrayList<>();
        for (NfaState clause : clauses) {
            clause = clause.copy(null, copiesMade, onCopyState);
            clauseCopies.add(clause);
        }
        copy.clauses.addAll(clauseCopies);
        copy.nextState = nextState;
        if (nextState == null && dangling != null)
            dangling.add(copy);
        return copy;
    }

    @Override
    public void setNextState(int input, NfaState state) {
        clauses.set(input, state);
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
        for (NfaState clause : clauses) {
            if (!clause.matchesEmptySequence(statesVisited))
                return false;
        }
        return nextState == null || nextState.matchesEmptySequence(statesVisited);
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
        boolean anyClauseHitsAllSameLength = false;
        for (NfaState clause : clauses) {
            if (clause.hitsAllSameLength(statesVisited)) {
                anyClauseHitsAllSameLength = true;
                break;
            }
        }
        return anyClauseHitsAllSameLength && (nextState == null || nextState.hitsAllSameLength(statesVisited));
    }

    @Override
    public int hitsLengthMin(Set<NfaState> statesVisited) {
        if (statesVisited.contains(this)) {
            // We've found a cycle. Stop processing, and just return the
            // "safest" (least-guarantee) answer. In this case: the smallest
            // hit might be 0 long.
            return 0;
        }
        statesVisited.add(this);
        int hitLengthMin = 0;
        for (NfaState nextState : clauses) {
            int i = nextState.hitsLengthMin(statesVisited);
            if (i > hitLengthMin)
                hitLengthMin = i;
        }
        if (hitLengthMin < Integer.MAX_VALUE && nextState != null) {
            hitLengthMin += nextState.hitsLengthMin(statesVisited);
        }
        return hitLengthMin;
    }

    @Override
    public int hitsLengthMax(Set<NfaState> statesVisited) {
        if (statesVisited.contains(this)) {
            // We've found a cycle. Stop processing, and just return the
            // "safest" (least-guarantee) answer. In this case: the largest
            // hit might be "infinitely" large.
            return Integer.MAX_VALUE;
        }
        statesVisited.add(this);
        int hitLengthMax = Integer.MAX_VALUE;
        for (NfaState nextState : clauses) {
            int i = nextState.hitsLengthMax(statesVisited);
            if (i < hitLengthMax)
                hitLengthMax = i;
        }
        if (nextState != null) {
            hitLengthMax += nextState.hitsLengthMax(statesVisited);
        }
        return hitLengthMax;
    }

    @Override
    protected String dumpInternal(Map<NfaState, Integer> stateNrs) {
        String b = clauses.stream().map(s -> dump(s, stateNrs)).collect(Collectors.joining(","));
        return "AND(" + b + ", " + dump(nextState, stateNrs) + ")";
    }

    @Override
    void lookupAnnotationNumbersInternal(ForwardIndexAccessor fiAccessor, Map<NfaState, Boolean> statesVisited) {
        for (NfaState s : clauses) {
            if (s != null)
                s.lookupAnnotationNumbers(fiAccessor, statesVisited);
        }
        if (nextState != null)
            nextState.lookupAnnotationNumbers(fiAccessor, statesVisited);
    }

    @Override
    protected void finishInternal(Set<NfaState> visited) {
        for (int i = 0; i < clauses.size(); i++) {
            NfaState s = clauses.get(i);
            if (s == null)
                clauses.set(i, match());
            else
                s.finish(visited);
        }
        if (nextState == null)
            nextState = match();
        else
            nextState.finish(visited);
    }

}
