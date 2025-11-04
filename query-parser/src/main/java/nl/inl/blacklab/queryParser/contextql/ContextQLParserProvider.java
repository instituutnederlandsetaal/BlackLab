package nl.inl.blacklab.queryParser.contextql;

import java.util.Map;

import nl.inl.blacklab.plugins.QueryParserProvider;
import nl.inl.blacklab.search.BLQueryParser;
import nl.inl.blacklab.search.BlackLabIndex;

public class ContextQLParserProvider extends QueryParserProvider {

    @Override
    public String getId() {
        return "contextql";
    }

    @Override
    public BLQueryParser get(BlackLabIndex index, Map<String, Object> config) {
        return new ContextualQueryLanguageParser(index, config);
    }
}
