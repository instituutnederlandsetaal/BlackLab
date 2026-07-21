package nl.inl.blacklab.queryParser.corpusql;

import nl.inl.blacklab.plugins.QueryParserProvider;
import nl.inl.blacklab.plugins.param.PluginParams;
import nl.inl.blacklab.search.BLQueryParser;
import nl.inl.blacklab.search.BlackLabIndex;

/** Plugin providing the BCQL query language */
public class BcqlParserProvider extends QueryParserProvider {

    @Override
    public String getName() {
        return "corpusql";
    }

    @Override
    public String getDescription() {
        return "Parse a query in BCQL (BlackLab Corpus Query Language)";
    }

    @Override
    public BLQueryParser get(BlackLabIndex index, PluginParams config) {
        return new BcqlQueryLanguageParser(index, config);
    }

}
