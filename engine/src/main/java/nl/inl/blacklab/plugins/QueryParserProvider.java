package nl.inl.blacklab.plugins;

import nl.inl.blacklab.plugins.param.PluginParams;
import nl.inl.blacklab.search.BLQueryParser;
import nl.inl.blacklab.search.BlackLabIndex;

/** Creates parsers for a query language family (e.g. CQL-like).
 *
 * Parsers can be configured using the config map passed to the get() method,
 * for example to make them more or less compatible with a specific dialect.
 */
public abstract class QueryParserProvider extends Plugin {

    /** Create a parser, given an index and configuration options. */
    public abstract BLQueryParser get(BlackLabIndex index, PluginParams params);

}
