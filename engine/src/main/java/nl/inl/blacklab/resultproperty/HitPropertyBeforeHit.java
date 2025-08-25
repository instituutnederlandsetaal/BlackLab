package nl.inl.blacklab.resultproperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.hitresults.ContextSize;
import nl.inl.blacklab.search.results.hits.Hit;

/**
 * A hit property for sorting on a number of tokens before a hit.
 */
public class HitPropertyBeforeHit extends HitPropertyContextBase {

    public static final String ID = "before";

    /** How many tokens of context-before to compare */
    protected int numberOfTokens;

    static HitPropertyBeforeHit deserializeProp(BlackLabIndex index, AnnotatedField field, List<String> infos, ContextSize contextSize) {
        DeserializeInfos i = deserializeInfos(index, field, infos);
        int numberOfTokens = i.extraIntParam(0, contextSize.before());
        return new HitPropertyBeforeHit(index, i.annotation, i.sensitivity, numberOfTokens);
    }

    static HitPropertyBeforeHit deserializePropSingleWord(BlackLabIndex index, AnnotatedField field, List<String> infos) {
        HitPropertyBeforeHit hitProp = deserializeProp(index, field, infos, ContextSize.ZERO);
        hitProp.numberOfTokens = 1;
        return hitProp;
    }

    HitPropertyBeforeHit(HitPropertyBeforeHit prop, PropContext context, boolean invert) {
        super(prop, context, invert, null);
        this.numberOfTokens = prop.numberOfTokens;
    }

    public HitPropertyBeforeHit(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity) {
        this(index, annotation, sensitivity, -1, ID);
    }

    public HitPropertyBeforeHit(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity, int numberOfTokens) {
        this(index, annotation, sensitivity, numberOfTokens, ID);
    }

    HitPropertyBeforeHit(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity, String serializeName) {
        this(index, annotation, sensitivity, -1, serializeName);
    }

    HitPropertyBeforeHit(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity, int numberOfTokens, String serializeName) {
        super("context before", serializeName, index, annotation, sensitivity, true);
        this.numberOfTokens = numberOfTokens >= 1 ? numberOfTokens : index.defaultContextSize().before();
    }

    @Override
    public List<String> serializeParts() {
        List<String> result = new ArrayList<>(super.serializeParts());
        result.add(3, Integer.toString(numberOfTokens)); // before field name
        return result;
    }

    @Override
    public HitProperty copyWith(PropContext context, boolean invert) {
        return new HitPropertyBeforeHit(this, context, invert);
    }

    @Override
    public void fetchContext() {
        if (annotation.field() == context.hits().field()) {
            // Regular hit; use start and end offsets from the hit itself
            fetchContext((int[] starts, int[] ends, int hitIndex, Hit hit) -> {
                starts[hitIndex] = Math.max(0, hit.start() - numberOfTokens);
                ends[hitIndex] = hit.start();
            });
        } else {
            // We must be searching a parallel corpus and grouping/sorting on one of the target fields.
            // Determine start and end using matchInfo instead.
            fetchContext((int[] starts, int[] ends, int hitIndex, Hit hit) -> {
                int[] startEnd = getForeignHitStartEnd(hit, annotation.field().name());
                int pos = startEnd[0] == Integer.MIN_VALUE ? hit.start() : startEnd[0];
                starts[hitIndex] = Math.max(0, pos - numberOfTokens);
                ends[hitIndex] = pos;
            });
        }
    }

    @Override
    public boolean isDocPropOrHitText() {
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
        HitPropertyBeforeHit that = (HitPropertyBeforeHit) o;
        return numberOfTokens == that.numberOfTokens;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), numberOfTokens);
    }
}
