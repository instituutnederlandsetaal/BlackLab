package nl.inl.blacklab.resultproperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.codec.BLTerms;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.util.PropertySerializeUtil;

public class PropertyValueContextWords extends PropertyValueContext {

    static Terms getTerms(Annotation annotation, LeafReaderContext lrc) {
        if (lrc == null)
            return annotation.field().index().forwardIndex(annotation).terms(); // use global terms
        String luceneField = annotation.forwardIndexSensitivity().luceneField();
        return BLTerms.forSegment(lrc, luceneField).reader();
    }

    /** Segment our term ids came from (will be null if this is a global value, or if arrays are length 0) */
    private LeafReaderContext lrc;

    /** Term ids for this value */
    int[] valueTokenId;

    /** Sort orders for our term ids */
    int[] valueSortOrder;

    /** Sensitivity to use for comparisons */
    private MatchSensitivity sensitivity;

    /**
     * With context before of the match, sorting/grouping occurs from
     * front to back (e.g. right to left for English), but display should still
     * be from back to front.
     */
    boolean reverseOnDisplay;

    public PropertyValueContextWords(Annotation annotation, MatchSensitivity sensitivity,
            LeafReaderContext lrc, int[] termIds, int[] sortPositions, boolean reverseOnDisplay) {
        super(getTerms(annotation, lrc), annotation);
        init(sensitivity, lrc, termIds, sortPositions, reverseOnDisplay);
    }

    public PropertyValueContextWords(Annotation annotation, MatchSensitivity sensitivity,
            Terms terms, int[] termIds, int[] sortPositions, boolean reverseOnDisplay) {
        super(terms, annotation);
        init(sensitivity, null, termIds, sortPositions, reverseOnDisplay);
    }

    private void init(MatchSensitivity sensitivity, LeafReaderContext lrc, int[] termIds, int[] sortPositions,
            boolean reverseOnDisplay) {
        this.sensitivity = sensitivity;
        this.lrc = lrc;
        this.valueTokenId = termIds;
        if (sortPositions == null) {
            this.valueSortOrder = new int[termIds.length];
            this.terms.idsToSortOrder(termIds, valueSortOrder, sensitivity);
        } else {
            this.valueSortOrder = sortPositions;
        }
        this.reverseOnDisplay = reverseOnDisplay;
    }

    @Override
    public int compareTo(Object o) {
        assert lrc == ((PropertyValueContextWords)o).lrc;
        return Arrays.compare(valueSortOrder, ((PropertyValueContextWords) o).valueSortOrder);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(valueSortOrder);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj instanceof PropertyValueContextWords) {
            return Arrays.equals(valueSortOrder, ((PropertyValueContextWords) obj).valueSortOrder);
        }
        return false;
    }

    public static PropertyValue deserialize(BlackLabIndex index, AnnotatedField field, List<String> infos, boolean reverseOnDisplay) {
        List<String> infosTerms = infos.subList(2, infos.size());
        return deserializeInternal(index, field, infos, infosTerms, reverseOnDisplay);
    }

    private static PropertyValueContextWords deserializeInternal(BlackLabIndex index, AnnotatedField field,
            List<String> infos, List<String> terms, boolean reverseOnDisplay) {
        Annotation annotation = infos.isEmpty() ? field.mainAnnotation() :
                index.metadata().annotationFromFieldAndName(infos.get(0), field);
        Terms termsObj = index.annotationForwardIndex(annotation).terms();
        MatchSensitivity sensitivity = infos.size() > 1 ? MatchSensitivity.fromLuceneFieldSuffix(infos.get(1)) :
                annotation.mainSensitivity().sensitivity();
        int[] ids = new int[terms.size()];
        for (int i = 0; i < terms.size(); i++) {
            ids[i] = deserializeToken(termsObj, terms.get(i));
        }
        return new PropertyValueContextWords(annotation, sensitivity, termsObj, ids, null, reverseOnDisplay);
    }

    // get displayable string version; note that we lowercase this if this is case-insensitive
    @Override
    public String toString() {
        List<String> parts = new ArrayList<>();
        int n = valueTokenId.length;
        for (int i = 0; i < n; i++) {
            int v = valueTokenId[i];
            String word = v < 0 ? NO_VALUE_STR : sensitivity.desensitize(terms.get(v));
            if (!word.isEmpty())
                parts.add(word);
        }
        if (reverseOnDisplay)
            Collections.reverse(parts);
        return StringUtils.join(parts, " ");
    }

    @Override
    public String serialize() {
        int length = valueTokenId.length;
        String[] parts = new String[length + 3];
        parts[0] = reverseOnDisplay ? "cwsr" : "cws";
        parts[1] = annotation.fieldAndAnnotationName();
        parts[2] = sensitivity.luceneFieldSuffix();
        for (int i = 0; i < length; i++) {
            String term = serializeTerm(terms, valueTokenId[i]);
            parts[i + 3] = term;
        }
        return PropertySerializeUtil.combineParts(parts);
    }

    @Override
    public Object value() {
        return valueTokenId;
    }

    boolean isGlobal() {
        return lrc == null;
    }

    @Override
    public PropertyValue toGlobal() {
        if (isGlobal())
            throw new IllegalStateException("Don't call toGlobal on already-global value!");
        int[] globalTermIds = Arrays.copyOf(valueTokenId, valueTokenId.length);
        terms.convertToGlobalTermIds(globalTermIds);
        int[] globalSortOrder = new int[globalTermIds.length];
        Terms globalTerms = terms.getGlobalTerms();
        globalTerms.idsToSortOrder(globalTermIds, globalSortOrder, sensitivity);
        return new PropertyValueContextWords(annotation, sensitivity,
                globalTerms, globalTermIds, globalSortOrder, reverseOnDisplay);
    }
}
