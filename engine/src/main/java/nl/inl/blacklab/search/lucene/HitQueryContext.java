package nl.inl.blacklab.search.lucene;

/**
 * Provides per-hit query-wide context, such as captured groups.
 * <p>
 * This object is passed to the whole BLSpans tree before iterating over the
 * hits. Captured groups will register themselves here and receive an index in
 * the captured group array, and BLSpans objects that need access to captured
 * groups will store a reference to this context and use it later.
 */
public class HitQueryContext {

    /** Root of the BLSpans tree for this query. */
    private BLSpans rootSpans;

    /** Match info names for our query, in index order.
     *  NOTE: shared between multiple Spans that might run in parallel! */
    private final MatchInfoDefs matchInfoDefs;

    /** Default field for this query (the primary field we search in; or only field for non-parallel corpora) */
    private final String defaultField;

    /** The field this part of the query searches. For parallel corpora, this may differ from defaultField. Never null. */
    private final String field;

    public HitQueryContext(BLSpans spans, String defaultField, MatchInfoDefs matchInfoDefs) {
        this(spans, defaultField, defaultField, matchInfoDefs);
    }

    private HitQueryContext(BLSpans spans, String defaultField, String field, MatchInfoDefs matchInfoDefs) {
        this.rootSpans = spans;
        this.defaultField = defaultField;
        assert field != null;
        this.field = field;
        this.matchInfoDefs = matchInfoDefs;
    }

    public HitQueryContext withSpans(BLSpans spans) {
        return new HitQueryContext(spans, defaultField, field, matchInfoDefs);
    }

    public HitQueryContext withField(String overriddenField) {
        HitQueryContext result = this;
        if (overriddenField != null) {
            result = new HitQueryContext(rootSpans, defaultField, overriddenField, matchInfoDefs);
        }
        return result;
    }

    /**
     * Set our Spans object.
     * <p>
     * Used when manually iterating through the index segments, because we go
     * through several Spans for a single query.
     *
     * @param spans our new spans
     */
    public void setSpans(BLSpans spans) {
        this.rootSpans = spans;
    }

    /**
     * Register a match info (e.g. captured group), assigning it a unique index number.
     * <p>
     * Synchronized because it's called from SpansReader.initialize(), which can execute in multiple threads in parallel.
     *
     * @param name the group's name
     * @param type the group's type, or null if we don't know here (i.e. when referring to the group as a span)
     * @return the group's assigned index
     */
    public int registerMatchInfo(String name, MatchInfo.Type type) {
        return registerMatchInfo(name, type, getField(), null);
    }

    /**
     * Register a match info (e.g. captured group), assigning it a unique index number.
     * <p>
     * Synchronized because it's called from SpansReader.initialize(), which can execute in multiple threads in parallel.
     *
     * @param name the group's name
     * @param type the group's type, or null if we don't know here (i.e. when referring to the group as a span)
     * @param field the group's field. Never null. Used e.g. when capturing relation, which should always
     *                    be captured in the source field, even if the span mode is target (and the context reflects that).
     * @param targetField for relation and list of relations: the target field, or empty string if not applicable
     * @return the group's assigned index
     */
    public int registerMatchInfo(String name, MatchInfo.Type type, String field, String targetField) {
        return matchInfoDefs.register(name, type, field, targetField).getIndex();
    }

    /**
     * Get the number of captured groups
     * 
     * @return number of captured groups
     */
    public synchronized int numberOfMatchInfos() {
        return matchInfoDefs.currentSize();
    }

    /**
     * Retrieve all the captured group information.
     * <p>
     * Used by Hits.
     *
     * @param matchInfo array to place the captured group information into
     */
    public void getMatchInfo(MatchInfo[] matchInfo) {
        rootSpans.getMatchInfo(matchInfo);
    }

    /**
     * Get the match infos definitions.
     * <p>
     * The list is in index order.
     *
     * @return the list of match infos
     */
    public synchronized MatchInfoDefs getMatchInfoDefs() {
        return matchInfoDefs;
    }

    /**
     * Get the field for this part of the query.
     * <p>
     * Used for parallel corpora.
     *
     * @return the field this part of the query searches
     */
    public String getField() {
        return field;
    }

    public String getDefaultField() {
        return defaultField;
    }

    /**
     * Are any of the captures of type INLINE_TAG or RELATION?
     * <p>
     * If yes, getRelationInfo() can return non-null values, and we must
     * e.g. store these in SpansInBuckets.
     *
     * @return true if any of the captures are of type INLINE_TAG or RELATION
     */
    public synchronized boolean hasRelationCaptures() {
        return matchInfoDefs.currentlyHasRelationCaptures();
    }
}
