package nl.inl.blacklab.search.fimatch;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.set.mutable.primitive.IntHashSet;

import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

/**
 * Represents both a state in an NFA, and a complete NFA with this as the
 * starting state.
 */
public class NfaStateToken extends NfaState {

    static final String ANY_TOKEN = null;

    /** What annotation we're trying to match */
    protected final String luceneField;

    /**
     * Index of the annotation we're trying to match. Only valid after
     * lookupPropertyNumber() called.
     */
    private int propertyNumber = -1;

    /** The tokens this state accepts. */
    private final Set<String> inputTokenStrings;

    /**
     * The tokens this state accepts. Only valid after lookupPropertNumber() called.
     */
    private MutableIntSet inputTokensGlobalTermIds = null;

    /** Do we accept any token? */
    private boolean acceptAnyToken = false;

    /** The next state if a matching token was found. */
    protected NfaState nextState;

    public NfaStateToken(String luceneField, String inputToken, NfaState nextState) {
        this.luceneField = luceneField;
        inputTokenStrings = new HashSet<>();
        if (inputToken == null)
            acceptAnyToken = true;
        else
            inputTokenStrings.add(inputToken);
        this.nextState = nextState;
    }

    public NfaStateToken(String luceneField, Set<String> inputTokens, NfaState nextState) {
        this.luceneField = luceneField;
        this.inputTokenStrings = new HashSet<>(inputTokens);
        this.nextState = nextState;
    }

    /**
     * Find all matches for this NFA in the token source.
     *
     * @param fiDoc where to read tokens from
     * @param pos current matching position
     * @param matchEnds where to collect the matches found, or null if we don't want
     *            to collect them
     * @return true if any (new) matches were found, false if not
     */
    @Override
    public boolean findMatchesInternal(ForwardIndexDocument fiDoc, int pos, int direction, Set<Integer> matchEnds) {
        // Token state. Check if it matches token from token source, and if so, continue.
        int actualTokenSegmentTermId = fiDoc.getTokenSegmentTermId(propertyNumber, pos);
        if (acceptAnyToken && actualTokenSegmentTermId >= 0 || inputTokensGlobalTermIds.contains(actualTokenSegmentTermId)) {
            if (nextState == null) {
                // null stands for the match state
                if (matchEnds != null)
                    matchEnds.add(pos + direction);
                return true;
            }
            return nextState.findMatchesInternal(fiDoc, pos + direction, direction, matchEnds);
        }
        return false;
    }

    @Override
    void fillDangling(NfaState state) {
        if (nextState == null)
            nextState = state;
    }

    @Override
    NfaStateToken copyInternal(Collection<NfaState> dangling, Map<NfaState, NfaState> copiesMade, Consumer<NfaState> onCopyState) {
        NfaStateToken copy = new NfaStateToken(luceneField, inputTokenStrings, null);
        copiesMade.put(this, copy);
        copy.nextState = nextState == null ? null : nextState.copy(dangling, copiesMade, onCopyState);
        if (nextState == null && dangling != null)
            dangling.add(copy);
        return copy;
    }

    @Override
    public void setNextState(int i, NfaState state) {
        if (i != 0)
            throw new IllegalArgumentException("Token state only has one next state");
        nextState = state;
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
        String terms = acceptAnyToken ? "ANY" : StringUtils.join(inputTokenStrings, "|");
        if (terms.length() > 5000) {
            terms = terms.substring(0, 500) + "...";
        }
        return "TOKEN(" + terms + "," + dump(nextState, stateNrs) + ")";
    }

    @Override
    public void lookupAnnotationNumbersInternal(ForwardIndexAccessor fiAccessor, Map<NfaState, Boolean> statesVisited) {
        String[] comp = AnnotatedFieldNameUtil.getNameComponents(luceneField);
        String annotationName = comp[1];
        propertyNumber = fiAccessor.getAnnotationNumber(annotationName);
        MatchSensitivity sensitivity = AnnotatedFieldNameUtil.sensitivity(luceneField);
        inputTokensGlobalTermIds = new IntHashSet();
        for (String token : inputTokenStrings) {
            fiAccessor.getGlobalTermNumbers(inputTokensGlobalTermIds, propertyNumber, token, sensitivity);
        }
        if (nextState != null)
            nextState.lookupAnnotationNumbers(fiAccessor, statesVisited);
    }

    @Override
    protected void finishInternal(Set<NfaState> visited) {
        if (nextState == null)
            nextState = match();
        else
            nextState.finish(visited);
    }

}
