package nl.inl.blacklab.resultproperty;

import java.util.List;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.Hits;

/**
 * @deprecated use {@link HitPropertyBeforeHit}  instead
 */
@Deprecated
public class HitPropertyLeftContext extends HitPropertyBeforeHit {

    public static final String ID = "left";

    static HitPropertyBeforeHit deserializeProp(BlackLabIndex index, AnnotatedField field, List<String> infos, ContextSize contextSize) {
        DeserializeInfos i = deserializeInfos(index, field, infos);
        return new HitPropertyLeftContext(index, i.annotation, i.sensitivity, i.extraIntParam(0, contextSize.before()));
    }

    HitPropertyLeftContext(HitPropertyBeforeHit prop, Hits hits, boolean invert) {
        super(prop, hits, invert);
    }

    public HitPropertyLeftContext(BlackLabIndex index,
            Annotation annotation,
            MatchSensitivity sensitivity) {
        super(index, annotation, sensitivity, ID);
    }

    public HitPropertyLeftContext(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity,
            int numberOfTokens) {
        super(index, annotation, sensitivity, numberOfTokens, ID);
    }
}
