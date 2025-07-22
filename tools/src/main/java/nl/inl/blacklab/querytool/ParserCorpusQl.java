package nl.inl.blacklab.querytool;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.textpattern.TextPattern;

/**
 * Parser for Corpus Query Language
 */
class ParserCorpusQl extends Parser {

    @Override
    public String getPrompt() {
        return "BCQL";
    }

    @Override
    public String getName() {
        return "BlackLab Corpus Query Language";
    }

    /**
     * Parse a Corpus Query Language query to produce a TextPattern
     *
     * @param query the query
     * @return the corresponding TextPattern
     * @throws InvalidQuery on parse error
     */
    @Override
    public TextPattern parse(BlackLabIndex index, String query) throws InvalidQuery {
        return CorpusQueryLanguageParser.parse(query, index.mainAnnotatedField().mainAnnotation().name());
    }

    @Override
    public void printHelp(Output output) {
        output.line("Corpus Query Language examples:");
        output.line("  \"city\" | \"town\"               # the word \"city\" or the word \"town\"");
        output.line("  \"the\" \"cit.*\"                 # \"the\" followed by word starting with \"cit\"");
        output.line("  [lemma=\"plan\" & pos=\"N.*\"]    # forms of the word \"plan\" as a noun");
        output.line("  [lemma=\"be\"] [lemma=\"stay\"]   # form of \"be\" followed by form of \"stay\"");
        output.line("  [lemma=\"be\"]{2,}              # two or more successive forms of \"to be\"");
        output.line("  [pos=\"J.*\"]+ \"man\"            # adjectives applied to \"man\"");
        output.line("  \"town\" []{0,5} \"city\"         # \"city\" after \"town\", up to 5 words in between");
    }

}
