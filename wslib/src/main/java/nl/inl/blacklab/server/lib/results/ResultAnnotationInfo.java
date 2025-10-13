package nl.inl.blacklab.server.lib.results;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.TruncatableFreqList;

public class ResultAnnotationInfo {

    private final Annotation annotation;

    private final boolean showValues;

    private final TruncatableFreqList terms;

    ResultAnnotationInfo(BlackLabIndex index, Annotation annotation, boolean showValues, long limitValues) {
        this.annotation = annotation;
        this.showValues = showValues;
        if (showValues && !index.isEmpty()) {
            terms = WebserviceOperations.getAnnotationValues(index, annotation, limitValues);
        } else {
            terms = TruncatableFreqList.dummy();
        }
    }

    public Annotation getAnnotation() {
        return annotation;
    }

    public boolean isShowValues() {
        return showValues;
    }

    public TruncatableFreqList getTerms() {
        return terms;
    }
}
