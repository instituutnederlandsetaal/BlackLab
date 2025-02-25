package nl.inl.blacklab.search.textpattern;

import java.util.Objects;

import nl.inl.util.RangeRegex;

class MatchValueIntRange implements MatchValue {

    private final int min;

    private final int max;

    public MatchValueIntRange(int min, int max) {
        this.min = min;
        this.max = max;
    }

    public String getRegex() {
        if (min > max)
            return RangeRegex.REGEX_WITHOUT_MATCHES; // a regex that will never match anything
        return RangeRegex.forRange(min, max);
    }

    public int getMin() {
        return min;
    }

    public int getMax() {
        return max;
    }

    public String getBcql() {
        return "in[" + min + "," + max + "]";
    }

    @Override
    public TextPatternTerm textPattern() {
        return new TextPatternIntRange(min, max);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        MatchValueIntRange that = (MatchValueIntRange) o;
        return min == that.min && max == that.max;
    }

    @Override
    public int hashCode() {
        return Objects.hash(min, max);
    }

    @Override
    public String toString() {
        return getBcql();
    }

    @Override
    public MatchValue desensitize() {
        return this;
    }
}
