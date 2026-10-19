package nl.inl.blacklab.search.lucene;

import java.io.IOException;

/**
 * A Spans that makes sure clauses below it get the overridden field in their HitQueryContext.
 *
 * Used for e.g. parallel corpora, in which part of the query deals with a different field.
 */
class SpansOverrideField extends BLFilterDocsSpans<BLSpans> {

    private final String overriddenField;

    public SpansOverrideField(BLSpans clause, String overriddenField) {
        super(clause, clause.guarantees());
        this.overriddenField = overriddenField;
    }

    @Override
    public int endPosition() {
        return in.endPosition();
    }

    @Override
    public int nextDoc() throws IOException {
        return in.nextDoc();
    }

    /**
     * Go to the next match.
     *
     * @return start position if we're on a valid match, NO_MORE_POSITIONS if we're done.
     */
    @Override
    public int nextStartPosition() throws IOException {
        return in.nextStartPosition();
    }

    /**
     * Go to the specified document, if it has hits. If not, go to the next document
     * containing hits.
     *
     * @param target the document number to skip to / over
     * @return start position if we're on a valid match, NO_MORE_POSITIONS if we're done.
     */
    @Override
    public int advance(int target) throws IOException {
        return in.advance(target);
    }

    /**
     * @return start of the current hit
     */
    @Override
    public int startPosition() {
        return in.startPosition();
    }

    @Override
    public String toString() {
        return "SpansOverrideField(" + in + ", " + overriddenField + ")";
    }

    @Override
    protected boolean twoPhaseCurrentDocMatches() throws IOException {
        return true; // If our clause matches, we match as well.
    }

    @Override
    public void getMatchInfo(MatchInfo[] matchInfo) {
        // NOTE: this uses the first match in the repetition for match info!
        // (for the last match, we would use firstToken + numRepetitions - 1)
        in.getMatchInfo(matchInfo);
    }

    @Override
    public boolean hasMatchInfo() {
        return in.hasMatchInfo();
    }

    @Override
    public RelationInfo getRelationInfo() {
        // NOTE: this uses the first match in the repetition for match info!
        // (for the last match, we would use firstToken + numRepetitions - 1)
        return in.getRelationInfo();
    }

    @Override
    protected void passHitQueryContextToClauses(HitQueryContext context) {
        // Our descendants should use the overridden field
        in.setHitQueryContext(context.withField(overriddenField));
    }
}
