package nl.inl.blacklab.exceptions;

/** Source-disordered XML cannot be represented as left/match/right character slices. */
public class UnsupportedConcordanceRepresentation extends UnsupportedOperationException {
    public UnsupportedConcordanceRepresentation(String field) {
        super("Field " + field + " requires forward-index concordances or the document contents endpoint.");
    }
}
