package nl.inl.blacklab.search.results.hitresults;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.plugins.HitGroupScorerType;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;

/** Scorer that just uses the group size */
public class HitGroupScorerSize extends HitGroupScorerType {

    @Override
    public String getId() {
        return "coll-groupsize";
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
                return HitGroupScorerSize.this;
            }

            @Override
            public double score(PropertyValue identity, long size) {
                return size;
            }
        };
    }

    @Override
    public boolean isWebSafe() {
        return true;
    }
}
