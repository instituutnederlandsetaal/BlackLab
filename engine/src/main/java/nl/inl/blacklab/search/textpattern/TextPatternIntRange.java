package nl.inl.blacklab.search.textpattern;

import java.util.Objects;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.util.RangeRegex;

/**
 * A TextPattern matching an integer range.
 */
public class TextPatternIntRange extends TextPatternTerm {

    private int min;

    private int max;

    private String annotation;

    public TextPatternIntRange(int min, int max) {
        this(min, max, null);
    }

    public TextPatternIntRange(int min, int max, String annotation) {
        super(getRegex(min, max), annotation, null);
        this.min = min;
        this.max = max;
        this.annotation = annotation;
    }

    public int getMin() {
        return min;
    }

    public int getMax() {
        return max;
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        TextPatternRegex r = new TextPatternRegex(getRegex(min, max), annotation, sensitivity);
        return r.translate(context);
    }

    private static String getRegex(int min, int max) {
        return min > max ? RangeRegex.REGEX_WITHOUT_MATCHES : RangeRegex.forRange(min, max);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TextPatternIntRange that = (TextPatternIntRange) o;
        return min == that.min && max == that.max && Objects.equals(annotation, that.annotation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(min, max, annotation);
    }

    @Override
    public TextPatternTerm withAnnotationAndSensitivity(String annotation, MatchSensitivity sensitivity) {
        if (annotation == null)
            annotation = this.annotation;
        return new TextPatternIntRange(min, max, annotation);
    }

    @Override
    public String toString() {
        return "intRange(" + min + "," + max + ")";
    }
}
