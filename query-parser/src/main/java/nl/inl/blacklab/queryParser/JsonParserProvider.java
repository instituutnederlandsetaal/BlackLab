package nl.inl.blacklab.queryParser;

import java.io.IOException;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.plugins.QueryParserProvider;
import nl.inl.blacklab.plugins.param.PluginParams;
import nl.inl.blacklab.search.BLQueryParser;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.textpattern.CompleteQuery;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.util.Json;

/** Parse JSON structure to a TextPattern. */
public class JsonParserProvider extends QueryParserProvider {

    @Override
    public String getName() {
        return "json-bql";
    }

    @Override
    public String getDescription() {
        return "Parse a query in BlackLab's JSON format";
    }

    @Override
    public BLQueryParser get(BlackLabIndex index, PluginParams config) {
        return query -> {
            try {
                TextPattern tp = Json.getJaxbReader().readValue(query, TextPattern.class);
                return new CompleteQuery(tp);
            } catch (IOException e) {
                throw new InvalidQuery(e);
            }
        };
    }

}
