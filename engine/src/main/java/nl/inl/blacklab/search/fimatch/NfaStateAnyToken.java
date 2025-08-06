package nl.inl.blacklab.search.fimatch;

import java.util.Map;

public class NfaStateAnyToken extends NfaStateToken {

    public NfaStateAnyToken(String luceneField, NfaState nextState) {
        super(luceneField, ANY_TOKEN, nextState);
    }

    @Override
    protected String dumpInternal(Map<NfaState, Integer> stateNrs) {
        return "ANY(" + dump(nextState, stateNrs) + ")";
    }

    protected NfaStateToken getBasicCopy() {
        return new NfaStateAnyToken(luceneField, null);
    }
}
