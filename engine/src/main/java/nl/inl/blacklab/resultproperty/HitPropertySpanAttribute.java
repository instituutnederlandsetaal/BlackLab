package nl.inl.blacklab.resultproperty;

import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

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

    /** If multiple matches were found for the span (i.e. in with-spans() list), join them using this separator */
    public static final String SEPARATOR_MULTIPLE_VALUES = "; ";

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
    private final String attributeName;

    /** The sensitivity of the match */
    private final MatchSensitivity sensitivity;

    HitPropertySpanAttribute(HitPropertySpanAttribute prop, Hits hits, boolean invert) {
        super(prop, hits, invert);
        groupName = prop.groupName;
        relNameInList = prop.relNameInList;
        relNameIsFullRelType = prop.relNameIsFullRelType;
        attributeName = prop.attributeName;
        sensitivity = prop.sensitivity;

        // Determine group index. We don't use the one from prop (if any), because
        // index might be different for different hits object.
        groupIndex = groupName.isEmpty() ? 0 : this.hits.matchInfoDefs().indexOf(groupName);
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
    public Class<? extends PropertyValue> getValueType() {
        return PropertyValueString.class;
    }

    @Override
    public PropertyValueString get(long hitIndex) {
        MatchInfo matchInfo = hits.get(hitIndex).matchInfo()[groupIndex];
        if (matchInfo == null)
            return PropertyValueString.NO_VALUE;

        String value;
        if (relNameInList != null && matchInfo instanceof RelationListInfo relList) {
            StringBuilder b = new StringBuilder();
            boolean found = false;
            if (relNameIsFullRelType) {
                // Look for the first full-type match in the list
                for (RelationInfo namedGroup: relList.getRelations()) {
                    if (namedGroup.getFullRelationType().equals(relNameInList)) {
                        if (b.length() > 0)
                            b.append(SEPARATOR_MULTIPLE_VALUES);
                        b.append(listIfMultiple(namedGroup.getAttributes().get(attributeName)));
                        found = true;
                    }
                }
            } else {
                // Look for the first type match in the list
                for (RelationInfo namedGroup: relList.getRelations()) {
                    if (namedGroup.getRelationType().equals(relNameInList)) {
                        if (b.length() > 0)
                            b.append(SEPARATOR_MULTIPLE_VALUES);
                        b.append(listIfMultiple(namedGroup.getAttributes().get(attributeName)));
                        found = true;
                    }
                }
            }
            if (!found)
                return PropertyValueString.NO_VALUE;
            value = b.toString();
        } else {
            if (!(matchInfo instanceof RelationInfo span))
                return PropertyValueString.NO_VALUE;
            value = StringUtils.join(span.getAttributes().get(attributeName), SEPARATOR_MULTIPLE_VALUES);
        }
        return new PropertyValueString(value);
    }

    private String listIfMultiple(List<String> values) {
        if (values.size() == 1)
            return values.get(0);
        return "[" + StringUtils.join(values, SEPARATOR_MULTIPLE_VALUES) + "]";
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
                && sensitivity == that.sensitivity;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), groupName, relNameInList, attributeName,
                sensitivity);
    }
}
