package nl.inl.blacklab.search.textpattern;

/**
 * Either a regex or an integer range that we want to match an annotation or attribute value to.
 */
public interface MatchValue {

    static MatchValue regex(String regex) {
        return new MatchValueRegex(regex);
    }

    static MatchValue intRange(int min, int max) {
        return new MatchValueIntRange(min, max);
    }

    /**
     * Return the Lucene regex.
     */
    String getRegex();

    /**
     * Return the BCQL syntax, for serialization.
     */
    String getBcql();

    TextPatternTerm textPattern();

    MatchValue desensitize();
}
