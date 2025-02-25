package nl.inl.blacklab.indexers.config;

import java.util.Objects;

/**
 * Mutable Span, used during indexing (to avoid creating too many instance).
 *
 * We mostly just deal with single word positions while indexing, but for relations
 * from a source span to a target span, such as alignments in parallel corpora,
 * we need a start and an end. This class is used for that, and it's mutable to avoid
 * creating instances for each position.
 */
public class Span {
    private int start;
    private int end;

    private Span(int start, int end) {
        this.start = start;
        this.end = end;
    }

    public static Span between(int start, int end) {
        return new Span(start, end);
    }

    /** Return a span starting at the indicated position and ending at the next word (e.g. a length of 1 word) */
    public static Span token(int position) {
        return new Span(position, position + 1);
    }

    /** Return an invalid Span object. */
    public static Span invalid() {
        return new Span(-1, -1);
    }

    /** Check if parameter is non-null and has non-negative boundaries. */
    public static boolean isValid(Span span) {
        return span != null && span.start() >= 0 && span.end() >= 0;
    }

    public int start() {
        return start;
    }

    public int end() {
        return end;
    }

    public int length() {
        return end - start;
    }

    @Override
    public String toString() {
        return start + "-" + end;
    }

    public void increment() {
        start++;
        end++;
    }

    public Span plus(int increment) {
        return new Span(start + increment, end + increment);
    }

    public void setTokenPosition(int position) {
        start = position;
        end = position + 1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Span span = (Span) o;
        return start == span.start && end == span.end;
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }

    public Span copy() {
        return new Span(start, end);
    }
}
