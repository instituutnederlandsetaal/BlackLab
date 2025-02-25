package nl.inl.blacklab.search.lucene;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Provides per-hit query-wide context, such as captured groups.
 *
 * This object is passed to the whole BLSpans tree before iterating over the
 * hits. Captured groups will register themselves here and receive an index in
 * the captured group array, and BLSpans objects that need access to captured
 * groups will store a reference to this context and use it later.
 */
public class HitQueryContext {

    /** Root of the BLSpans tree for this query. */
    private BLSpans rootSpans;

    /** Match info names for our query, in index order */
    List<MatchInfo.Def> matchInfoDefs = new ArrayList<>();

    /** Default field for this query (the primary field we search in; or only field for non-parallel corpora) */
    private final String defaultField;

    /** The field this part of the query searches. For parallel corpora, this may differ from defaultField. Never null. */
    private final String field;

    public HitQueryContext(BLSpans spans, String defaultField) {
        this(spans, defaultField, defaultField);
    }

    private HitQueryContext(BLSpans spans, String defaultField, String field) {
        this.rootSpans = spans;
        this.defaultField = defaultField;
        assert field != null;
        this.field = field;
    }

    public HitQueryContext withSpans(BLSpans spans) {
        HitQueryContext result = new HitQueryContext(spans, defaultField, field);
        result.matchInfoDefs = matchInfoDefs;
        return result;
    }

    public HitQueryContext withField(String overriddenField) {
        HitQueryContext result = this;
        if (overriddenField != null) {
            result = new HitQueryContext(rootSpans, defaultField, overriddenField);
            result.matchInfoDefs = matchInfoDefs;
        }
        return result;
    }

    /**
     * Set our Spans object.
     *
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
     *
     * @param name the group's name
     * @param type the group's type, or null if we don't know here (i.e. when referring to the group as a span)
     * @param field the group's field. Never null. Used e.g. when capturing relation, which should always
     *                    be captured in the source field, even if the span mode is target (and the context reflects that).
     * @param targetField for relation and list of relations: the target field, or empty string if not applicable
     * @return the group's assigned index
     */
    public int registerMatchInfo(String name, MatchInfo.Type type, String field, String targetField) {
        Optional<MatchInfo.Def> mi = matchInfoDefs.stream()
                .filter(mid -> mid.getName().equals(name))
                .findFirst();
        if (mi.isPresent()) {
            mi.get().updateType(type); // update type (e.g. if group is referred to before we know its type)
            return mi.get().getIndex(); // already registered, reuse
        }
        assert field != null;
        MatchInfo.Def newMatchInfo = new MatchInfo.Def(matchInfoDefs.size(), name, type, field, targetField);
        matchInfoDefs.add(newMatchInfo);
        return newMatchInfo.getIndex(); // index in array
    }

    /**
     * Get the number of captured groups
     * 
     * @return number of captured groups
     */
    public int numberOfMatchInfos() {
        return matchInfoDefs.size();
    }

    /**
     * Retrieve all the captured group information.
     *
     * Used by Hits.
     *
     * @param matchInfo array to place the captured group information into
     */
    public void getMatchInfo(MatchInfo[] matchInfo) {
        rootSpans.getMatchInfo(matchInfo);
    }

    /**
     * Get the match infos definitions.
     *
     * The list is in index order.
     *
     * @return the list of match infos
     */
    public List<MatchInfo.Def> getMatchInfoDefs() {
        return Collections.unmodifiableList(matchInfoDefs);
    }

    /**
     * Get the field for this part of the query.
     *
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
     *
     * If yes, getRelationId() can return non-null values, and we must
     * e.g. store these in SpansInBuckets.
     *
     * @return true if any of the captures are of type INLINE_TAG or RELATION
     */
    public boolean hasRelationCaptures() {
        return matchInfoDefs.stream().anyMatch(mid -> mid.getType() == MatchInfo.Type.INLINE_TAG || mid.getType() == MatchInfo.Type.RELATION);
    }
}
