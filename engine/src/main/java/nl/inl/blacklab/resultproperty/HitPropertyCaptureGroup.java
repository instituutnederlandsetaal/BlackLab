package nl.inl.blacklab.resultproperty;

import java.util.List;
import java.util.Objects;

import nl.inl.blacklab.exceptions.MatchInfoNotFound;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Hits;

/**
 * A hit property for grouping on a matched group.
 */
public class HitPropertyCaptureGroup extends HitPropertyContextBase {

    public static final String ID = "capture"; //TODO: deprecate, change to matchinfo? (to synch with response)

    static HitPropertyCaptureGroup deserializeProp(BlackLabIndex index, AnnotatedField field, List<String> infos) {
        DeserializeInfos i = deserializeInfos(index, field, infos);
        String matchInfoName = i.extraParam(0);
        String strSpanMode = i.extraParam(1); // source, target or full(default)
        RelationInfo.SpanMode spanMode = strSpanMode.toLowerCase().matches("source|target|full") ?
                RelationInfo.SpanMode.fromCode(strSpanMode) : RelationInfo.SpanMode.FULL_SPAN;
        return new HitPropertyCaptureGroup(index, i.annotation, i.sensitivity, matchInfoName, spanMode);
    }

    /** Name of match info to use */
    private String groupName;

    /** Part of the match info to use. Uses the full span by default, but can also
     *  use only the source of a relation or only the target. (full span of relation includes
     *  both source and target) */
    private RelationInfo.SpanMode spanMode = RelationInfo.SpanMode.FULL_SPAN;

    private int groupIndex = -1;

    HitPropertyCaptureGroup(HitPropertyCaptureGroup prop, Hits hits, boolean invert) {
        super(prop, hits, invert, determineMatchInfoField(hits, prop.groupName, prop.spanMode));
        groupName = prop.groupName;
        spanMode = prop.spanMode;

        // Determine group index. We don't use the one from prop (if any), because
        // index might be different for different hits object.
        groupIndex = groupName.isEmpty() ? 0 : this.hits.matchInfoIndex(groupName);
        if (groupIndex < 0)
            throw new MatchInfoNotFound(groupName);
    }

    /**
     * Determine what field the given match info is from.
     *
     * Only relevant for parallel corpora, where you can capture information from
     * other fields.
     *
     * @param hits     the hits object
     * @param groupName the match info group name
     * @return the field name
     */
    private static String determineMatchInfoField(Hits hits, String groupName, RelationInfo.SpanMode spanMode) {
        List<MatchInfo.Def> defs = hits.matchInfoDefs();
        return defs.stream()
                .filter(d -> d.getName().equals(groupName))
                .map(d -> spanMode == RelationInfo.SpanMode.TARGET && d.getTargetField() != null ? d.getTargetField() : d.getField())
                .findFirst().orElse(null);
    }

    public HitPropertyCaptureGroup(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity) {
        this(index, annotation, sensitivity, "", RelationInfo.SpanMode.FULL_SPAN);
    }

    public HitPropertyCaptureGroup(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity, String groupName, RelationInfo.SpanMode spanMode) {
        super("captured group", ID, index, annotation, sensitivity, false);
        this.groupName = groupName;
        this.spanMode = spanMode;
    }

    @Override
    public HitProperty copyWith(Hits newHits, boolean invert) {
        return new HitPropertyCaptureGroup(this, newHits, invert);
    }

    @Override
    public void fetchContext() {
        fetchContext((int[] starts, int[] ends, int indexInArrays, Hit hit) -> {
            MatchInfo group = hit.matchInfo()[groupIndex];
            starts[indexInArrays] = group == null ? 0 : group.spanStart(spanMode);
            ends[indexInArrays] = group == null ? 0 : group.spanEnd(spanMode);
        });
    }

    @Override
    public boolean isDocPropOrHitText() {
        // we cannot guarantee that we don't use any surrounding context!
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        HitPropertyCaptureGroup that = (HitPropertyCaptureGroup) o;
        return groupIndex == that.groupIndex && Objects.equals(groupName, that.groupName)
                && spanMode == that.spanMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), groupName, spanMode, groupIndex);
    }
}
