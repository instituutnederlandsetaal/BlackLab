package nl.inl.blacklab.resultproperty;

import java.util.List;
import java.util.Objects;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.indexmetadata.RelationUtil;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.lucene.RelationListInfo;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.HitsForHitProps;

/**
 * A hit property for grouping on a matched group.
 */
public class HitPropertyCaptureGroup extends HitPropertyContextBase {

    public static final String ID = "capture"; //TODO: deprecate, change to matchinfo? (to synch with response)

    /** Returned if match info not registered (yet) */
    private final PropertyValueContextWords emptyValue;

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
    
    /** If set: use the first tag/relation with this name in the match info list */
    private String relNameInList = null;

    /** If set: use the first tag/relation with this name in the match info list */
    private boolean relNameIsFullRelType = false;

    HitPropertyCaptureGroup(HitPropertyCaptureGroup prop, HitsForHitProps hits, boolean invert) {
        super(prop, hits, invert, determineMatchInfoField(hits, prop.groupName, prop.spanMode));
        groupName = prop.groupName;
        spanMode = prop.spanMode;

        relNameInList = prop.relNameInList;
        relNameIsFullRelType = prop.relNameIsFullRelType;
        emptyValue = prop.emptyValue;
    }

    public HitPropertyCaptureGroup(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity, String groupName, RelationInfo.SpanMode spanMode) {
        super("captured group", ID, index, annotation, sensitivity, false);
        this.groupName = groupName;

        // If groupName is of the form capturelist[relType], extract the relType
        // (in this case, we will only use the first relation with this type in the captured list;
        //  useful in combination with with-spans() to group on any tag in the hits found)
        int openBracketIndex = groupName.indexOf("[");
        if (openBracketIndex >= 0) {
            int closeBracketIndex = groupName.indexOf("]", openBracketIndex + 1);
            relNameInList = groupName.substring(openBracketIndex + 1, closeBracketIndex);
            this.groupName = groupName.substring(0, openBracketIndex);
            relNameIsFullRelType = RelationUtil.isFullType(relNameInList);
        }

        this.spanMode = spanMode;
        emptyValue = new PropertyValueContextWords(index, annotation, sensitivity, new int[0], new int[0], false);
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
    private static AnnotatedField determineMatchInfoField(HitsForHitProps hits, String groupName, RelationInfo.SpanMode spanMode) {
        return hits.matchInfoDefs().currentListFiltered(d -> d.getName().equals(groupName)).stream()
                .map(d -> spanMode == RelationInfo.SpanMode.TARGET && d.getTargetField() != null ? d.getTargetField() : d.getField())
                .findFirst().orElse(null);
    }

    @Override
    public HitProperty copyWith(HitsForHitProps newHits, boolean invert) {
        return new HitPropertyCaptureGroup(this, newHits, invert);
    }

    @Override
    public void fetchContext() {
        if (groupIndex < 0) {
            // Determine group index. Done lazily because the group might only be registered
            // when the second index segment is processed, for example.
            groupIndex = groupName.isEmpty() ? 0 : this.hits.matchInfoDefs().indexOf(groupName);
        }
        fetchContext((int[] starts, int[] ends, int indexInArrays, Hit hit) -> {
            if (groupIndex < 0) {
                // Match info not registered (yet). Return empty value.
                // Might be registered later in the matching process.
                starts[indexInArrays] = 0;
                ends[indexInArrays] = 0;
            }
            MatchInfo group = hit.matchInfos(groupIndex);
            
            if (relNameInList != null && group instanceof RelationListInfo relList) {
                if (relNameIsFullRelType) {
                    // Look for the first full-type match in the list
                    for (RelationInfo namedGroup: relList.getRelations()) {
                        if (namedGroup.getFullRelationType().equals(relNameInList)) {
                            group = namedGroup;
                            break;
                        }
                    }
                } else {
                    // Look for the first type match in the list
                    for (RelationInfo namedGroup: relList.getRelations()) {
                        if (namedGroup.getRelationType().equals(relNameInList)) {
                            group = namedGroup;
                            break;
                        }
                    }
                }
            }
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
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        HitPropertyCaptureGroup that = (HitPropertyCaptureGroup) o;
        return Objects.equals(groupName, that.groupName) && spanMode == that.spanMode && Objects.equals(
                relNameInList, that.relNameInList);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), groupName, spanMode, relNameInList);
    }
}
