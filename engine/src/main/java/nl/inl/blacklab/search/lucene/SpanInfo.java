package nl.inl.blacklab.search.lucene;

import java.util.Objects;

import nl.inl.blacklab.search.indexmetadata.AnnotatedField;

/**
 * Position information about a relation's source and target
 */
public class SpanInfo extends MatchInfo {

    public static SpanInfo create(int start, int end, AnnotatedField overriddenField) {
        return new SpanInfo(start, end, overriddenField);
    }

    int start;

    int end;

    private SpanInfo(int start, int end, AnnotatedField overriddenField) {
        super(overriddenField);
        this.start = start;
        this.end = end;
    }

    @Override
    public int getSpanStart() {
        return start;
    }

    @Override
    public int getSpanEnd() {
        return end;
    }

    @Override
    public Type getType() {
        return Type.SPAN;
    }

    @Override
    public String toString(String defaultField) {
        return "span(" + getSpanStart() + "-" + getSpanEnd() + ")" + toStringOptFieldName(defaultField);
    }

    @Override
    public int compareTo(MatchInfo o) {
        if (o instanceof SpanInfo si) {
            int n;
            n = Integer.compare(start, si.start);
            if (n != 0)
                return n;
            n = Integer.compare(end, si.end);
            return n;
        }
        return super.compareTo(o);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        SpanInfo spanInfo = (SpanInfo) o;
        return start == spanInfo.start && end == spanInfo.end;
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }
}
