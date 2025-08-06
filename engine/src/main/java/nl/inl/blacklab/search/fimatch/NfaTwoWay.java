package nl.inl.blacklab.search.fimatch;

/** Stores a (partial) NFA and its reverse. */
public class NfaTwoWay {

    private final Nfa nfa;

    private Nfa nfaReverse;

    public NfaTwoWay(Nfa nfa, Nfa nfaReverse) {
        this.nfa = nfa;
        this.nfaReverse = nfaReverse;
    }

    public Nfa getNfa() {
        return nfa;
    }

    public Nfa getNfaReverse() {
        return nfaReverse;
    }

    public NfaTwoWay copy() {
        return new NfaTwoWay(nfa.copy(), nfaReverse.copy());
    }

    public void append(NfaTwoWay part) {
        nfa.append(part.getNfa().copy());
        Nfa r = part.getNfaReverse().copy();
        r.append(nfaReverse);
        nfaReverse = r;
    }

    public void lookupAnnotationIndexes(ForwardIndexAccessor fiAccessor) {
        nfa.lookupAnnotationIndexes(fiAccessor);
        nfaReverse.lookupAnnotationIndexes(fiAccessor);
    }

    public void finish() {
        nfa.finish();
        nfaReverse.finish();
    }

}
