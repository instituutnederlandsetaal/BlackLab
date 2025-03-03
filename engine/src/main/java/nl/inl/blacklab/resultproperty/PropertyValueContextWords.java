package nl.inl.blacklab.resultproperty;

import java.util.Arrays;
import java.util.List;

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

    /** Sensitivity to use for comparisons */
    private MatchSensitivity sensitivity;

    /**
     * With context before of the match, sorting/grouping occurs from
     * front to back (e.g. right to left for English), but display should still
     * be from back to front.
     */
    private boolean reverseOnDisplay;

    /** Our index (may be null for tests) */
    private BlackLabIndex index;

    public PropertyValueContextWords(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity, int[] value, int[] sortOrder, boolean reverseOnDisplay) {
        super(index.annotationForwardIndex(annotation).terms(), annotation);
        init(sensitivity, value, sortOrder, reverseOnDisplay);
        this.index = index;
    }

    PropertyValueContextWords(Terms terms, Annotation annotation, MatchSensitivity sensitivity, int[] value,
            int[] sortOrder, boolean reverseOnDisplay, boolean alternateField) {
        super(terms, annotation);
        valueSortOrder = init(sensitivity, value, sortOrder, reverseOnDisplay);
    }

    private int[] init(MatchSensitivity sensitivity, int[] value, int[] sortOrder,
            boolean reverseOnDisplay) {
        this.sensitivity = sensitivity;
        this.valueTokenId = value;
        if (sortOrder == null) {
            this.valueSortOrder = new int[value.length];
            terms.toSortOrder(value, valueSortOrder, sensitivity);
        } else {
            this.valueSortOrder = sortOrder;
        }
        this.reverseOnDisplay = reverseOnDisplay;
        this.index = null;
        return valueSortOrder;
    }

    @Override
    public int compareTo(Object o) {
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
        if (obj instanceof PropertyValueContextWords)
            return Arrays.equals(valueSortOrder, ((PropertyValueContextWords) obj).valueSortOrder);
        return false;
    }

    public static PropertyValue deserialize(BlackLabIndex index, AnnotatedField field, List<String> infos, boolean reverseOnDisplay) {
        List<String> infosTerms = infos.subList(2, infos.size());
        return deserializeInternal(index, field, infos, infosTerms, reverseOnDisplay);
    }

    public static PropertyValue deserializeSingleWord(BlackLabIndex index, AnnotatedField field, List<String> infos) {
        List<String> infosTerms = infos.size() > 2 ? infos.subList(2, 3) : List.of("");
        return deserializeInternal(index, field, infos, infosTerms, false);
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
        return new PropertyValueContextWords(index, annotation, sensitivity, ids, null, reverseOnDisplay);
    }

    // get displayable string version; note that we lowercase this if this is case-insensitive
    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        if (reverseOnDisplay) {
            for (int i = valueTokenId.length - 1; i >= 0; i--) {
                int v = valueTokenId[i];
                String word = v < 0 ? NO_VALUE_STR : sensitivity.desensitize(terms.get(v));
                if (word.length() > 0) {
                    if (b.length() > 0)
                        b.append(" ");
                    b.append(word);
                }
            }
        } else {
            for (int v : valueTokenId) {
                String word = v < 0 ? NO_VALUE_STR : sensitivity.desensitize(terms.get(v));
                if (word.length() > 0) {
                    if (b.length() > 0)
                        b.append(" ");
                    b.append(word);
                }
            }
        }
        return b.toString();
    }

    @Override
    public String serialize() {
        String[] parts = new String[valueTokenId.length + 3];
        parts[0] = reverseOnDisplay ? "cwsr" : "cws";
        parts[1] = annotation.fieldAndAnnotationName();
        parts[2] = sensitivity.luceneFieldSuffix();
        for (int i = 0; i < valueTokenId.length; i++) {
            String term = serializeTerm(terms, valueTokenId[i]);
            parts[i + 3] = term;
        }
        return PropertySerializeUtil.combineParts(parts);
    }

    @Override
    public int[] value() {
        return valueTokenId;
    }
}
