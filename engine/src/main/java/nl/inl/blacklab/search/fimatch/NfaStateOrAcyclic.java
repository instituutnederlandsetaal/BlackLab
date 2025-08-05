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
 * An OR-node where none of the clauses cycle back to an earlier node. This can
 * be evaluated more efficiently, by finding matches for each of the clauses,
 * and then continuing at nextState from each of the matches found.
 */
public class NfaStateOrAcyclic extends NfaState {

    final List<NfaState> clauses;

    NfaState nextState;

    /**
     * If we know all hits are same length (in finish()), we can optimize matching
     * by shortcircuiting OR
     */
    boolean clausesAllSameLength;

    public NfaStateOrAcyclic(List<NfaState> clauses, boolean clausesAllSameLength) {
        for (NfaState clause : clauses) {
            clause.finish(new HashSet<>());
        }
        this.clauses = new ArrayList<>(clauses);
        this.nextState = null;
        this.clausesAllSameLength = clausesAllSameLength;
    }

    public NfaStateOrAcyclic() {
        this.clauses = new ArrayList<>();
        this.nextState = null;
    }

    @Override
    public boolean findMatchesInternal(ForwardIndexDocument fiDoc, int pos, int direction, Set<Integer> matchEnds) {
        // OR/Split state. Find matches for all alternatives.
        boolean clauseMatched = false;
        Set<Integer> clauseMatchEnds = new HashSet<>();
        if (clausesAllSameLength) {
            // We can short-circuit as soon as we find a single clause hit, because there can only be one match end.
            for (NfaState clause : clauses) {
                boolean matchesFound = false;
                matchesFound = clause.findMatchesInternal(fiDoc, pos, direction, clauseMatchEnds);
                clauseMatched |= matchesFound;
                if (clauseMatched)
                    break; // short-circuit OR
            }
        } else {
            // We have to process all clauses because we need all match ends for the next phase.
            for (NfaState clause : clauses) {
                boolean matchesFound = false;
                matchesFound = clause.findMatchesInternal(fiDoc, pos, direction, clauseMatchEnds);
                clauseMatched |= matchesFound;
            }
        }
        boolean foundMatch = false;
        if (clauseMatched) {
            // Continue matching from the matches to our OR clauses
            for (Integer clauseMatchEnd : clauseMatchEnds) {
                foundMatch |= nextState.findMatchesInternal(fiDoc, clauseMatchEnd, direction, matchEnds);
                if (foundMatch && matchEnds == null)
                    break; // we don't care about the match ends, just that there are matches
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
    NfaStateOrAcyclic copyInternal(Collection<NfaState> dangling, Map<NfaState, NfaState> copiesMade, Consumer<NfaState> onCopyState) {
        NfaStateOrAcyclic copy = new NfaStateOrAcyclic();
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
        boolean anyMatchEmpty = false;
        for (NfaState clause : clauses) {
            if (clause.matchesEmptySequence(statesVisited)) {
                anyMatchEmpty = true;
                break;
            }
        }
        return anyMatchEmpty && (nextState == null || nextState.matchesEmptySequence(statesVisited));
    }

    @Override
    public boolean hitsAllSameLength(Set<NfaState> statesVisited) {
        return clausesAllSameLength && (nextState == null || nextState.hitsAllSameLength(statesVisited));
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
        for (NfaState clause : clauses) {
            int i = clause.hitsLengthMin(statesVisited);
            if (i < hitLengthMin)
                hitLengthMin = i;
        }
        if (hitLengthMin < Integer.MAX_VALUE && nextState != null) {
            hitLengthMin += nextState.hitsLengthMin(statesVisited);
        }
        return hitLengthMin;
    }

    @Override
    public int hitsLengthMax(Set<NfaState> statesVisited) {
        int hitLengthMax = -1;
        if (statesVisited.contains(this)) {
            // We've found a cycle. Stop processing, and just return the
            // "safest" (least-guarantee) answer. In this case: the largest
            // hit might be "infinitely" large.
            return Integer.MAX_VALUE;
        }
        statesVisited.add(this);
        for (NfaState clause : clauses) {
            int i = clause.hitsLengthMax(statesVisited);
            if (i > hitLengthMax)
                hitLengthMax = i;
        }
        if (hitLengthMax >= 0 && nextState != null) {
            hitLengthMax += nextState.hitsLengthMax(statesVisited);
        }
        return Math.max(hitLengthMax, 0);
    }

    @Override
    protected String dumpInternal(Map<NfaState, Integer> stateNrs) {
        String b = clauses.stream().map(clause -> dump(clause, stateNrs)).collect(Collectors.joining(","));
        return "OR(" + b + ", " + dump(nextState, stateNrs) + ")";
    }

    @Override
    void lookupAnnotationNumbersInternal(ForwardIndexAccessor fiAccessor, Map<NfaState, Boolean> statesVisited) {
        for (NfaState clause : clauses) {
            if (clause != null)
                clause.lookupAnnotationNumbers(fiAccessor, statesVisited);
        }
        if (nextState != null)
            nextState.lookupAnnotationNumbers(fiAccessor, statesVisited);
    }

    @Override
    protected void finishInternal(Set<NfaState> visited) {
        for (int i = 0; i < clauses.size(); i++) {
            NfaState clause = clauses.get(i);
            if (clause == null)
                clauses.set(i, match());
            else
                clause.finish(visited);
        }
        if (nextState == null)
            nextState = match();
        else
            nextState.finish(visited);
    }

}
