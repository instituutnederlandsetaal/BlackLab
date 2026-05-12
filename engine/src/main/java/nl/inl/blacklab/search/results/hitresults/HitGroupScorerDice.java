package nl.inl.blacklab.search.results.hitresults;

import nl.inl.blacklab.plugins.HitGroupScorerType;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;

public class HitGroupScorerDice extends HitGroupScorerType {

    @Override
    public String getId() {
        return "coll-dice";
    }

    @Override
    public Type getType() {
        return Type.COLLOCATION;
    }

    @Override
    public HitGroupScorer getCollocationScorer(AnnotationSensitivity collocateAnnotation, long totalFrequency, long wordFrequency) {
        return new HitGroupCollocationScorer(collocateAnnotation) {
            @Override
            public double score(PropertyValue identity, long size) {
                long collocateFrequency = getCollocateFrequency(identity);
                return (2 * size / (double) (collocateFrequency + wordFrequency));
            }
        };
    }

    @Override
    public boolean isWebSafe() {
        return true;
    }
}
