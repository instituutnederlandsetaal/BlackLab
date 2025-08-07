package nl.inl.blacklab.resultproperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.util.PropertySerializeUtil;

public class PropertyValueContextWords extends PropertyValueContext {

    /** Term ids for this value */
    int[] valueTokenId;

    /** Sort orders for our term ids */
    int[] valueSortOrder;

    /** String version of this value (valueTokenId/valueSortOrder will be null in this case) */
    private String[] value;

    /** Sensitivity to use for comparisons */
    private MatchSensitivity sensitivity;

    /**
     * With context before of the match, sorting/grouping occurs from
     * front to back (e.g. right to left for English), but display should still
     * be from back to front.
     */
    private boolean reverseOnDisplay;

    public PropertyValueContextWords(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity,
            int[] termIds, int[] sortPositions, boolean reverseOnDisplay) {
        super(index.annotationForwardIndex(annotation).terms(), annotation);
        init(sensitivity, null, termIds, sortPositions, reverseOnDisplay);
    }

    public PropertyValueContextWords(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity,
            String[] termStrings, boolean reverseOnDisplay) {
        super(index.annotationForwardIndex(annotation).terms(), annotation);
        init(sensitivity, termStrings, null, null, reverseOnDisplay);
    }

    PropertyValueContextWords(Terms terms, Annotation annotation, MatchSensitivity sensitivity, String[] termStrings,
            int[] termIds, int[] sortPositions, boolean reverseOnDisplay) {
        super(terms, annotation);
        init(sensitivity, termStrings, termIds, sortPositions, reverseOnDisplay);
    }

    private void init(MatchSensitivity sensitivity, String[] termStrings, int[] termIds, int[] sortPositions,
            boolean reverseOnDisplay) {
        this.sensitivity = sensitivity;
        if (termStrings == null) {
            this.valueTokenId = termIds;
            if (sortPositions == null) {
                this.valueSortOrder = new int[termIds.length];
                terms.toSortOrder(termIds, valueSortOrder, sensitivity);
            } else {
                this.valueSortOrder = sortPositions;
            }
            this.value = null;
        } else {
            this.value = termStrings;
            this.valueTokenId = null;
            this.valueSortOrder = null;
        }
        this.reverseOnDisplay = reverseOnDisplay;
    }

    @Override
    public int compareTo(Object o) {
        if (value != null)
            return compareStringArrays(value, ((PropertyValueContextWords) o).value);
        return Arrays.compare(valueSortOrder, ((PropertyValueContextWords) o).valueSortOrder);
    }

    private int compareStringArrays(String[] a, String[] b) {
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int c = collator.compare(a[i], b[i]);
            if (c != 0)
                return c;
        }
        if (a.length < b.length)
            return -1; // this value is shorter, so comes first
        if (a.length > b.length)
            return 1; // this value is longer, so comes last
        return 0; // equal
    }

    @Override
    public int hashCode() {
        if (value != null)
            return Arrays.hashCode(value);
        return Arrays.hashCode(valueSortOrder);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj instanceof PropertyValueContextWords) {
            if (value != null)
                return Arrays.equals(value, ((PropertyValueContextWords) obj).value);
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
        MatchSensitivity sensitivity = infos.size() > 1 ? MatchSensitivity.fromLuceneFieldSuffix(infos.get(1)) :
                annotation.mainSensitivity().sensitivity();
        return new PropertyValueContextWords(index, annotation, sensitivity, terms.toArray(new String[0]),
                reverseOnDisplay);
    }

    // get displayable string version; note that we lowercase this if this is case-insensitive
    @Override
    public String toString() {
        List<String> parts = new ArrayList<>();
        int n = value == null ? valueTokenId.length : value.length;
        for (int i = 0; i < n; i++) {
            String word;
            if (value == null) {
                int v = valueTokenId[i];
                word = v < 0 ? NO_VALUE_STR : sensitivity.desensitize(terms.get(v));
            } else {
                String w = value[i];
                word = w == null ? NO_VALUE_STR : sensitivity.desensitize(w);
            }
            if (!word.isEmpty())
                parts.add(word);
        }
        if (reverseOnDisplay)
            Collections.reverse(parts);
        return StringUtils.join(parts, " ");
    }

    @Override
    public String serialize() {
        int length = value == null ? valueTokenId.length : value.length;
        String[] parts = new String[length + 3];
        parts[0] = reverseOnDisplay ? "cwsr" : "cws";
        parts[1] = annotation.fieldAndAnnotationName();
        parts[2] = sensitivity.luceneFieldSuffix();
        for (int i = 0; i < length; i++) {
            String term = value == null ?
                    serializeTerm(terms, valueTokenId[i]) :
                    serializeTerm(value[i]);
            parts[i + 3] = term;
        }
        return PropertySerializeUtil.combineParts(parts);
    }

    @Override
    public Object value() {
        return value == null ? valueTokenId : value;
    }
}
