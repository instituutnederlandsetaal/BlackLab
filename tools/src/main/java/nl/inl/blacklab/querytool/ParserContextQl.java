package nl.inl.blacklab.querytool;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.plugins.param.PluginParams;
import nl.inl.blacklab.queryParser.contextql.ContextualQueryLanguageParser;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.textpattern.CompleteQuery;
import nl.inl.blacklab.search.textpattern.TextPattern;

/**
 * Parser for Contextual Query Language
 */
class ParserContextQl extends Parser {

    Query includedFilterQuery;

    @Override
    public String getPrompt() {
        return "ContextQL";
    }

    @Override
    public String getName() {
        return "Contextual Query Language";
    }

    /**
     * Parse a Contextual Query Language query to produce a TextPattern
     *
     * @param query the query
     * @return the corresponding TextPattern
     * @throws InvalidQuery on parse error
     */
    @Override
    public TextPattern parse(BlackLabIndex index, String query) throws InvalidQuery {
        //outprintln("WARNING: SRU CQL SUPPORT IS EXPERIMENTAL, MAY NOT WORK AS INTENDED");
        CompleteQuery q = ContextualQueryLanguageParser.parse(index, PluginParams.NONE, query);
        includedFilterQuery = q.filter();
        return q.pattern();
    }

    @Override
    public Query getIncludedFilterQuery() {
        return includedFilterQuery;
    }

    @Override
    public void printHelp(Output output) {
        output.line("Contextual Query Language examples:");
        output.line("  city or town                  # Find the word \"city\" or the word \"town\"");
        output.line("  \"the cit*\"                    # Find \"the\" followed by a word starting with \"cit\"");
        output.line("  lemma=plan and pos=N*         # Find forms of \"plan\" as a noun");
        output.line("  lemma=\"be stay\"               # form of \"be\" followed by form of \"stay\"");
        output.line("  town prox//5//ordered city    # (NOTE: this is not supported yet!)");
        output.line("                                # \"city\" after \"town\", up to 5 words in between");
        output.line("\nWARNING: THIS PARSER IS STILL VERY MUCH EXPERIMENTAL. NOT SUITABLE FOR PRODUCTION.");
    }

}
