package nl.inl.blacklab.queryParser.corpusql;

import java.util.Map;

import nl.inl.blacklab.plugins.QueryParserProvider;
import nl.inl.blacklab.search.BLQueryParser;
import nl.inl.blacklab.search.BlackLabIndex;

/** Plugin providing the BCQL query language */
public class BcqlParserProvider extends QueryParserProvider {

    @Override
    public String getId() {
        return "corpusql";
    }

    @Override
    public BLQueryParser get(BlackLabIndex index, Map<String, Object> config) {
        return new BcqlQueryLanguageParser(index, config);
    }
}
