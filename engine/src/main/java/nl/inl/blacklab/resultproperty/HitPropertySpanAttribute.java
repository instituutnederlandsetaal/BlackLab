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
import nl.inl.blacklab.util.PropertySerializeUtil;

/**
 * A hit property for grouping on an attribute of a matched span.
 *
 * E.g. for a query like <tt>"water" within &lt;speech speaker="Obama" /&gt;</tt>, you can capture the
 * <tt>speaker</tt> attribute of the <tt>speech</tt> element.
 */
public class HitPropertySpanAttribute extends HitProperty {

    public static final String ID = "span-attribute"; //TODO: deprecate, change to matchinfo? (to synch with response)

    static HitPropertySpanAttribute deserializeProp(BlackLabIndex index, AnnotatedField field, List<String> infos) {
        if (infos.isEmpty())
            throw new IllegalArgumentException("span-attribute requires at least one argument (span name)");
        String spanName = infos.get(0);
        String spanAttribute = infos.size() > 1 ? infos.get(1) : null;
        MatchSensitivity sensitivity = infos.size() > 2 ?
                (infos.get(2).isEmpty() ? MatchSensitivity.SENSITIVE : MatchSensitivity.valueOf(infos.get(2))) :
                MatchSensitivity.SENSITIVE;
        return new HitPropertySpanAttribute(index, spanName, spanAttribute, sensitivity);
    }

    /** Name of match info to use */
    private String spanName;

    /** Index of the match info */
    private int spanIndex = -1;

    /** Name of the attribute to capture */
    private String spanAttribute;

    /** The sensitivity of the match */
    private MatchSensitivity sensitivity;

    private Hits hits;

    HitPropertySpanAttribute(HitPropertySpanAttribute prop, Hits hits, boolean invert) {
        super();
        spanName = prop.spanName;
        spanAttribute = prop.spanAttribute;
        sensitivity = prop.sensitivity;
        this.hits = hits;
        reverse = prop.reverse ? !invert : invert;

        // Determine group index. We don't use the one from prop (if any), because
        // index might be different for different hits object.
        spanIndex = spanName.isEmpty() ? 0 : this.hits.matchInfoIndex(spanName);
        if (spanIndex < 0)
            throw new MatchInfoNotFound(spanName);
    }

    public HitPropertySpanAttribute(BlackLabIndex index, String spanName, String spanAttribute,
            MatchSensitivity sensitivity) {
        this.spanName = spanName;
        this.spanAttribute = spanAttribute;
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
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        HitPropertySpanAttribute that = (HitPropertySpanAttribute) o;
        return spanIndex == that.spanIndex && Objects.equals(spanName, that.spanName) && Objects.equals(
                spanAttribute, that.spanAttribute) && sensitivity == that.sensitivity && Objects.equals(hits,
                that.hits);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), spanName, spanIndex, spanAttribute, sensitivity, hits);
    }

    @Override
    public PropertyValue get(long hitIndex) {
        MatchInfo matchInfo = hits.get(hitIndex).matchInfo()[spanIndex];
        if (matchInfo == null || !(matchInfo instanceof RelationInfo))
            return new PropertyValueString("ATTRIBUTE_NOT_FOUND");
        RelationInfo span = (RelationInfo) matchInfo;
        return new PropertyValueString(span.getAttributes().get(spanAttribute));
    }

    @Override
    public String name() {
        return "span attribute";
    }

    @Override
    public String serialize() {
        return PropertySerializeUtil.combineParts("span-attribute", spanName, spanAttribute, sensitivity.toString());
    }
}
