package nl.inl.blacklab.resultproperty;

import java.util.List;
import java.util.Objects;

import nl.inl.blacklab.exceptions.MatchInfoNotFound;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.indexmetadata.RelationUtil;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.lucene.RelationListInfo;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.util.PropertySerializeUtil;

/**
 * A hit property for grouping on an attribute of a matched span.
 *
 * E.g. for a query like <tt>"water" within &lt;speech speaker="Obama" /&gt;</tt>, you can capture the
 * <tt>speaker</tt> attribute of the <tt>speech</tt> element.
 */
public class HitPropertySpanAttribute extends HitProperty {

    public static final String ID = "span-attribute";

    public static final PropertyValueString VALUE_ATTR_NOT_FOUND = new PropertyValueString("ATTRIBUTE_NOT_FOUND");

    static HitPropertySpanAttribute deserializeProp(BlackLabIndex index, AnnotatedField field, List<String> infos) {
        if (infos.isEmpty())
            throw new IllegalArgumentException("span-attribute requires at least one argument (span name)");
        String spanName = infos.get(0);
        String spanAttribute = infos.size() > 1 ? infos.get(1) : null;
        MatchSensitivity sensitivity = infos.size() > 2 ?
                (infos.get(2).isEmpty() ? MatchSensitivity.SENSITIVE : MatchSensitivity.fromName(infos.get(2))) :
                MatchSensitivity.SENSITIVE;
        return new HitPropertySpanAttribute(spanName, spanAttribute, sensitivity);
    }

    /** Name of match info to use */
    private String groupName;

    /** Index of the match info */
    private int groupIndex = -1;

    /** If set: use the first tag/relation with this name in the match info list */
    private String relNameInList = null;

    /** Is it a full relation type (e.g. "relClass::relType") or not (e.g. "relType")? */
    private boolean relNameIsFullRelType = false;

    /** Name of the attribute to capture */
    private String attributeName;

    /** The sensitivity of the match */
    private MatchSensitivity sensitivity;

    private Hits hits;

    HitPropertySpanAttribute(HitPropertySpanAttribute prop, Hits hits, boolean invert) {
        super();
        groupName = prop.groupName;
        relNameInList = prop.relNameInList;
        relNameIsFullRelType = prop.relNameIsFullRelType;
        attributeName = prop.attributeName;
        sensitivity = prop.sensitivity;
        this.hits = hits;
        reverse = prop.reverse ? !invert : invert;

        // Determine group index. We don't use the one from prop (if any), because
        // index might be different for different hits object.
        groupIndex = groupName.isEmpty() ? 0 : this.hits.matchInfoIndex(groupName);
        if (groupIndex < 0)
            throw new MatchInfoNotFound(groupName);
    }

    public HitPropertySpanAttribute(String groupName, String attributeName,
            MatchSensitivity sensitivity) {
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

        this.attributeName = attributeName;
        this.sensitivity = sensitivity;
    }

    @Override
    public HitProperty copyWith(Hits newHits, boolean invert) {
        return new HitPropertySpanAttribute(this, newHits, invert);
    }

    @Override
    public boolean isDocPropOrHitText() {
        // we use match info attributes which are already part of the hit; no extra context needed
        return true;
    }

    @Override
    public PropertyValue get(long hitIndex) {
        MatchInfo matchInfo = hits.get(hitIndex).matchInfo()[groupIndex];
        if (matchInfo == null)
            return VALUE_ATTR_NOT_FOUND;

        if (relNameInList != null && matchInfo instanceof RelationListInfo) {
            RelationListInfo relList = (RelationListInfo) matchInfo;
            if (relNameIsFullRelType) {
                // Look for the first full-type match in the list
                for (RelationInfo namedGroup: relList.getRelations()) {
                    if (namedGroup.getFullRelationType().equals(relNameInList)) {
                        matchInfo = namedGroup;
                        break;
                    }
                }
            } else {
                // Look for the first type match in the list
                for (RelationInfo namedGroup: relList.getRelations()) {
                    if (namedGroup.getRelationType().equals(relNameInList)) {
                        matchInfo = namedGroup;
                        break;
                    }
                }
            }
        }
        if (!(matchInfo instanceof RelationInfo))
            return VALUE_ATTR_NOT_FOUND;
        RelationInfo span = (RelationInfo) matchInfo;
        return new PropertyValueString(span.getAttributes().get(attributeName));
    }

    @Override
    public String name() {
        return "span attribute";
    }

    @Override
    public String serialize() {
        return PropertySerializeUtil.combineParts("span-attribute", groupName, attributeName, sensitivity.toString());
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        HitPropertySpanAttribute that = (HitPropertySpanAttribute) o;
        return Objects.equals(groupName, that.groupName) && Objects.equals(relNameInList,
                that.relNameInList) && Objects.equals(attributeName, that.attributeName)
                && sensitivity == that.sensitivity && Objects.equals(hits, that.hits);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), groupName, relNameInList, attributeName,
                sensitivity, hits);
    }
}
