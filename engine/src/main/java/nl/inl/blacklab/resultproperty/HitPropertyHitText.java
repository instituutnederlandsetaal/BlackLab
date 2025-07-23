package nl.inl.blacklab.resultproperty;

import java.util.List;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Hits;

/**
 * A hit property for grouping on the text actually matched.
 */
public class HitPropertyHitText extends HitPropertyContextBase {

    public static final String ID = "hit";

    static HitPropertyHitText deserializeProp(BlackLabIndex index, AnnotatedField field, List<String> infos) {
        DeserializeInfos i = deserializeInfos(index, field, infos);
        return new HitPropertyHitText(index, i.annotation, i.sensitivity);
    }

    HitPropertyHitText(HitPropertyHitText prop, Hits hits, boolean invert) {
        super(prop, hits, invert, null);
    }

    public HitPropertyHitText(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity) {
        super("hit text", ID, index, annotation, sensitivity, false);
    }

    public HitPropertyHitText(BlackLabIndex index, MatchSensitivity sensitivity) {
        this(index, null, sensitivity);
    }

    public HitPropertyHitText(BlackLabIndex index, Annotation annotation) {
        this(index, annotation, null);
    }

    public HitPropertyHitText(BlackLabIndex index) {
        this(index, null, null);
    }

    @Override
    public HitProperty copyWith(Hits newHits, boolean invert) {
        return new HitPropertyHitText(this, newHits, invert);
    }

    @Override
    public void fetchContext() {
        if (annotation.field() == hits.queryInfo().field()) {
            // Regular hit; use start and end offsets from the hit itself
            fetchContext((int[] starts, int[] ends, int hitIndex, Hit hit) -> {
                starts[hitIndex] = hit.start();
                ends[hitIndex] = hit.end();
            });
        } else {
            // We must be searching a parallel corpus and grouping/sorting on one of the target fields.
            // Determine start and end using matchInfo instead.
            fetchContext((int[] starts, int[] ends, int hitIndex, Hit hit) -> {
                int[] startEnd = getForeignHitStartEnd(hit, annotation.field().name());
                starts[hitIndex] = startEnd[0] == Integer.MAX_VALUE ? hit.start() : startEnd[0];
                ends[hitIndex] = startEnd[1] == Integer.MIN_VALUE ? hit.end() : startEnd[1];
            });
        }
    }

    @Override
    public boolean isDocPropOrHitText() {
        return true;
    }
}
