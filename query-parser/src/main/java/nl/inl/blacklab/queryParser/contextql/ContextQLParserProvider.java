package nl.inl.blacklab.queryParser.contextql;

import nl.inl.blacklab.plugins.QueryParserProvider;
import nl.inl.blacklab.plugins.param.PluginParams;
import nl.inl.blacklab.search.BLQueryParser;
import nl.inl.blacklab.search.BlackLabIndex;

public class ContextQLParserProvider extends QueryParserProvider {

    @Override
    public String getName() {
        return "contextql";
    }

    @Override
    public String getDescription() {
        return "Parse a query in SRU CQL (Contextual Query Language)";
    }

    @Override
    public BLQueryParser get(BlackLabIndex index, PluginParams config) {
        return new ContextualQueryLanguageParser(index, config);
    }

}
