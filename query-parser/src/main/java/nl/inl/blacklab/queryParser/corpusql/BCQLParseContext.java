package nl.inl.blacklab.queryParser.corpusql;

/**
 * Relevant context while parsing query.
 *
 * @param isQuotedStringQuery whether quoted strings are to be interpreted as queries (i.e. outside token brackets)
 */
record BCQLParseContext(boolean isQuotedStringQuery) {
    static final BCQLParseContext DEFAULT = new BCQLParseContext(true);

    public BCQLParseContext withQuotedStringIsQuery(boolean isQuotedStringQuery) {
        return new BCQLParseContext(isQuotedStringQuery);
    }
}
