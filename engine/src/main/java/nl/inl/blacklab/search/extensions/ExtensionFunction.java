package nl.inl.blacklab.search.extensions;

import java.util.List;

import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * A function that can be used as a sequence part in CQL.
 * Such a function takes a number of arguments and returns a BLSpanQuery.
 */
public interface ExtensionFunction {
    BLSpanQuery apply(QueryInfo queryInfo, QueryExecutionContext context, List<Object> args);
}
