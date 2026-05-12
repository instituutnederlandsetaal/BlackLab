package nl.inl.blacklab.search.results.hitresults;

import nl.inl.blacklab.plugins.HitGroupScorerType;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;

public class HitGroupScorerSalience extends HitGroupScorerType {

    @Override
    public String getId() {
        return "coll-salience";
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
                if (collocateFrequency == 0)
                    collocateFrequency = 1;
                double temp = (size / (double)wordFrequency) / (double)collocateFrequency;
                temp *= totalFrequency;
                return ( StrictMath.log(size) * StrictMath.log(temp) / StrictMath.log(2.0) );
            }
        };
    }

    @Override
    public boolean isWebSafe() {
        return true;
    }
}
