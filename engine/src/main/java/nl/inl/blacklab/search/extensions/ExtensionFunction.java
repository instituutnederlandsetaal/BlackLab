package nl.inl.blacklab.search.extensions;

import java.util.List;

import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.textpattern.TextPattern;

/**
 * A function that can be used as a sequence part in CQL.
 * Such a function takes a number of arguments and returns a BLSpanQuery.
 */
public interface ExtensionFunction {
    TextPattern.EvalResult apply(QueryInfo queryInfo, QueryExecutionContext context, List<Object> args);
}
