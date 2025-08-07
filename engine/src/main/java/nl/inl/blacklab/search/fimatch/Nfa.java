package nl.inl.blacklab.search.fimatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import nl.inl.blacklab.search.lucene.BLSpanQuery;

/**
 * A fragment of an NFA being built. Contains a starting state and a list of
 * dangling end states
 */
public class Nfa {

    /** Starting state for this NFA */
    NfaState startingState;

    /**
     * Where we need to add arrows if we want to append another bit of NFA to this
     * one.
     */
    Collection<NfaState> danglingArrows;

    public Nfa(NfaState startingState, Collection<NfaState> danglingArrows) {
        this.startingState = startingState;
        this.danglingArrows = new ArrayList<>();
        if (danglingArrows != null)
            this.danglingArrows.addAll(danglingArrows);
    }

    public NfaState getStartingState() {
        return startingState;
    }

    private void setStartingState(NfaState start) {
        this.startingState = start;
    }

    public Nfa copy() {
        return copy(null);
    }

    public Nfa copy(Consumer<NfaState> onCopy) {
        List<NfaState> dangling = new ArrayList<>();
        NfaState copy = startingState.copy(dangling, onCopy);
        return new Nfa(copy, dangling);
    }

    public Nfa append(Nfa state) {
        for (NfaState d : danglingArrows) {
            d.fillDangling(state.getStartingState());
        }
        danglingArrows.clear();
        danglingArrows.addAll(state.getDanglingArrows());
        return this;
    }

    public void repeat(int min, int max) {
        // {min, max} with min > 0 or * (0 or more)
        // First, create a sequence of min copies of the clause.
        // Then, follow up with (max - min) links with an escape arrow
        // each (or loop back to the last copy of the min sequence for
        // max == infinite)

        // Create the min part
        Nfa link = this; // we'll loop back to link later if max == infinite
        if (min > 1) {
            for (int i = 1; i < min; i++) {
                link = link.copy();
                append(link);
            }
        }

        // Create the max part, depending on whether it's infinite or not.
        if (max == BLSpanQuery.MAX_UNLIMITED) {
            // Infinite. Loop back to start of last link added.
            NfaState loopBack = NfaState.or(true, Arrays.asList(link.getStartingState(), null), false);
            for (NfaState d : danglingArrows) {
                d.fillDangling(loopBack);
            }
            danglingArrows.clear();
            danglingArrows.add(loopBack);
            if (min == 0) {
                // For * (0 or more), loopback becomes the starting state, so we
                // don't have to go through the clause but can skip it entirely.
                startingState = loopBack;
            }
        } else {
            // How many optional clauses do we still need to add?
            int numberOfLinksToAdd = max - min;

            // Finite. Allow (max - min) more occurrences of the clause.
            if (min != 0 && numberOfLinksToAdd > 0) {
                // There's already required occurrences of the clause.
                // Make a copy of link first so we don't destroy those.
                link = link.copy();
            }
            Set<NfaState> escapeArrows = new HashSet<>();

            // Do we need to make the optional fragment?
            if (numberOfLinksToAdd > 0) {
                // Make optional clause fragment (note that if min == 0, this link
                // will already be in its correct place)
                NfaState start = NfaState.or(true, Arrays.asList(link.getStartingState(), null), false);
                escapeArrows.add(start);
                link.setStartingState(start);
            }

            if (min == 0) {
                // We already have one link (see above), so add one less.
                numberOfLinksToAdd--;
                if (numberOfLinksToAdd > 0) {
                    // The first link is the one we already made.
                    // This copy is the next link we'll add.
                    link = link.copy();
                }
            }
            for (int i = 0; i < numberOfLinksToAdd; i++) {
                append(link);
                escapeArrows.add(link.getStartingState());
                if (i < numberOfLinksToAdd - 1)
                    link = link.copy(); // this will be the next link
            }
            // Escape arrows may have been filled in by append(). Set them to null again so
            // they will be treated as dangling arrows.
            for (NfaState state : escapeArrows) {
                state.setNextState(1, null);
                danglingArrows.add(state);
            }
        }
    }

    public void invert() {
        NfaState not = new NfaStateNot(startingState, null);
        startingState = not;
        danglingArrows.clear();
        danglingArrows.add(not);
    }

    public Collection<NfaState> getDanglingArrows() {
        return danglingArrows;
    }

    @Override
    public String toString() {
        return startingState.toString();
    }

    /**
     * Lookup the annotation indexes for all states in this NFA.
     *
     * @param fiAccessor forward index accessor to use
     */
    public void lookupAnnotationIndexes(ForwardIndexAccessor fiAccessor) {
        startingState.lookupAnnotationIndexes(fiAccessor);
    }

    public void finish() {
        startingState.finish();
    }
}
