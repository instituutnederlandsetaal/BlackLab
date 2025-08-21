package nl.inl.blacklab.resultproperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.hitresults.ContextSize;
import nl.inl.blacklab.search.results.hits.Hit;
import nl.inl.blacklab.search.results.hits.Hits;

/**
 * A hit property for sorting on a number of tokens after a hit.
 */
public class HitPropertyAfterHit extends HitPropertyContextBase {

    public static final String ID = "after";

    /** How many tokens of context-after to compare */
    protected int numberOfTokens;

    static HitPropertyAfterHit deserializeProp(BlackLabIndex index, AnnotatedField field, List<String> infos, ContextSize contextSize) {
        DeserializeInfos i = deserializeInfos(index, field, infos);
        int numberOfTokens = i.extraIntParam(0, contextSize.before());
        return new HitPropertyAfterHit(index, i.annotation, i.sensitivity, numberOfTokens);
    }

    static HitPropertyAfterHit deserializePropSingleWord(BlackLabIndex index, AnnotatedField field, List<String> infos) {
        HitPropertyAfterHit hitProp = deserializeProp(index, field, infos, ContextSize.ZERO);
        hitProp.numberOfTokens = 1;
        return hitProp;
    }

    HitPropertyAfterHit(HitPropertyAfterHit prop, Hits hits, LeafReaderContext lrc, boolean toGlobal, boolean invert) {
        super(prop, hits, lrc, toGlobal, invert, null);
        this.numberOfTokens = prop.numberOfTokens;
    }

    public HitPropertyAfterHit(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity) {
        this(index, annotation, sensitivity, -1, ID);
    }

    public HitPropertyAfterHit(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity, int numberOfTokens) {
        this(index, annotation, sensitivity, numberOfTokens, ID);
    }

    @SuppressWarnings("unused")
    HitPropertyAfterHit(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity, String serializeName) {
        this(index, annotation, sensitivity, -1, serializeName);
    }

    HitPropertyAfterHit(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity, int numberOfTokens, String serializeName) {
        super("context after", serializeName, index, annotation, sensitivity, false);
        this.numberOfTokens = numberOfTokens >= 1 ? numberOfTokens : index.defaultContextSize().after();
    }

    @Override
    public List<String> serializeParts() {
        List<String> result = new ArrayList<>(super.serializeParts());
        result.add(3, Integer.toString(numberOfTokens)); // before field name
        return result;
    }

    @Override
    public HitProperty copyWith(Hits newHits, LeafReaderContext lrc, boolean toGlobal, boolean invert) {
        return new HitPropertyAfterHit(this, newHits, lrc, toGlobal, invert);
    }

    @Override
    public void fetchContext() {
        if (annotation.field() == hits.field()) {
            // Regular hit; use start and end offsets from the hit itself
            fetchContext((int[] starts, int[] ends, int hitIndex, Hit hit) -> {
                starts[hitIndex] = hit.end();
                ends[hitIndex] = hit.end() + numberOfTokens;
            });
        } else {
            // We must be searching a parallel corpus and grouping/sorting on one of the target fields.
            // Determine start and end using matchInfo instead.
            fetchContext((int[] starts, int[] ends, int hitIndex, Hit hit) -> {
                int[] startEnd = getForeignHitStartEnd(hit, annotation.field().name());
                int pos = startEnd[1] == Integer.MIN_VALUE ? hit.end() : startEnd[1];
                starts[hitIndex] = pos;
                ends[hitIndex] = pos + numberOfTokens;
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
        HitPropertyAfterHit that = (HitPropertyAfterHit) o;
        return numberOfTokens == that.numberOfTokens;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), numberOfTokens);
    }
}
