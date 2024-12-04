package nl.inl.blacklab.search.textpattern;

import java.util.Objects;

import nl.inl.util.StringUtil;

class MatchValueRegex implements MatchValue {
    private final String regex;

    public MatchValueRegex(String regex) {
        this.regex = regex;
    }

    public String getRegex() {
        return regex;
    }

    public String getBcql() {
        return "'" + regex.replaceAll("[\\\\']", "\\\\$0") + "'";
    }

    @Override
    public TextPatternTerm textPattern() {
        return new TextPatternRegex(getRegex());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        MatchValueRegex that = (MatchValueRegex) o;
        return Objects.equals(regex, that.regex);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(regex);
    }

    @Override
    public String toString() {
        return getBcql();
    }

    @Override
    public MatchValue desensitize() {
        return MatchValue.regex(StringUtil.desensitize(regex));
    }
}
