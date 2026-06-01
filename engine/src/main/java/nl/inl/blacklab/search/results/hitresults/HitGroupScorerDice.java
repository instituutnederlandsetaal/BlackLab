package nl.inl.blacklab.search.results.hitresults;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.plugins.HitGroupScorerType;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;

public class HitGroupScorerDice extends HitGroupScorerType {

    public static final String TYPE_ID = "coll-dice";

    @Override
    public String getId() {
        return TYPE_ID;
    }

    @Override
    public Type getType() {
        return Type.COLLOCATION;
    }

    @Override
    public HitGroupScorer getCollocationScorer(AnnotationSensitivity collocateAnnotation, Query filter, long totalFrequency, long wordFrequency) {
        return new HitGroupCollocationScorer(collocateAnnotation, filter) {
            @Override
            public HitGroupScorerType getType() {
                return HitGroupScorerDice.this;
            }

            @Override
            public double score(PropertyValue identity, long size) {
                long collocateFrequency = getCollocateFrequency(identity);
                long divisor = collocateFrequency + wordFrequency;
                if (divisor == 0)
                    divisor = 1;
                return (2 * size / (double) divisor);
            }
        };
    }

    @Override
    public boolean isWebSafe() {
        return true;
    }
}
