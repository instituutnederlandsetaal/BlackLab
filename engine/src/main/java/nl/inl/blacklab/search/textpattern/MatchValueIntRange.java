package nl.inl.blacklab.search.textpattern;

import nl.inl.util.RangeRegex;

public record MatchValueIntRange(int min, int max) implements MatchValue {

    @Override
    public String regex() {
        if (min > max)
            return RangeRegex.REGEX_WITHOUT_MATCHES; // a regex that will never match anything
        return RangeRegex.forRange(min, max);
    }

    @Override
    public String getBcql() {
        return "in[" + min + "," + max + "]";
    }

    @Override
    public TextPatternTerm textPattern() {
        return new TextPatternIntRange(min, max);
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
