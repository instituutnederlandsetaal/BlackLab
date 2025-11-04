package nl.inl.blacklab.search.extensions;

import java.util.Collections;
import java.util.List;

import nl.inl.blacklab.plugins.QueryFunction;

/**
 * Class that adds query extension functions
 */
public interface ExtensionFunctionClass {

    /** Value to pass if there are no default parameter values. */
    List<Object> NO_DEFAULT_VALUES = Collections.emptyList();
    /** Variable number of query params */
    List<QueryFunction.ArgType> ARGS_VAR_Q = List.of(QueryFunction.ArgType.QUERY, QueryFunction.ArgType.ELLIPSIS);
    /** Variable number of string params */
    List<QueryFunction.ArgType> ARGS_VAR_S = List.of(QueryFunction.ArgType.STRING, QueryFunction.ArgType.ELLIPSIS);
    /** Two strings */
    List<QueryFunction.ArgType> ARGS_S = List.of(QueryFunction.ArgType.STRING);
    /** A single query as an argument */
    List<QueryFunction.ArgType> ARGS_Q = List.of(QueryFunction.ArgType.QUERY);
    /** Two strings */
    List<QueryFunction.ArgType> ARGS_SS = List.of(QueryFunction.ArgType.STRING, QueryFunction.ArgType.STRING);
    /** Two strings */
    List<QueryFunction.ArgType> ARGS_SQ = List.of(QueryFunction.ArgType.STRING, QueryFunction.ArgType.QUERY);
    /** A query and a string */
    List<QueryFunction.ArgType> ARGS_QS = List.of(QueryFunction.ArgType.QUERY, QueryFunction.ArgType.STRING);
    /** Two queries as an argument */
    List<QueryFunction.ArgType> ARGS_QQ = List.of(QueryFunction.ArgType.QUERY, QueryFunction.ArgType.QUERY);
    /** Two strings */
    List<QueryFunction.ArgType> ARGS_SSS = List.of(QueryFunction.ArgType.STRING, QueryFunction.ArgType.STRING, QueryFunction.ArgType.STRING);
    /** A query, a string and another query */
    List<QueryFunction.ArgType> ARGS_SSQ = List.of(QueryFunction.ArgType.STRING, QueryFunction.ArgType.STRING, QueryFunction.ArgType.QUERY);
    /** A query, a string and another query */
    List<QueryFunction.ArgType> ARGS_SQS = List.of(QueryFunction.ArgType.STRING, QueryFunction.ArgType.QUERY, QueryFunction.ArgType.STRING);
    /** A query, a string and another query */
    List<QueryFunction.ArgType> ARGS_SQQ = List.of(QueryFunction.ArgType.STRING, QueryFunction.ArgType.QUERY, QueryFunction.ArgType.QUERY);
    /** A query, a string and another query */
    List<QueryFunction.ArgType> ARGS_QSS = List.of(QueryFunction.ArgType.QUERY, QueryFunction.ArgType.STRING, QueryFunction.ArgType.STRING);
    /** A query, a string and another query */
    List<QueryFunction.ArgType> ARGS_QSQ = List.of(QueryFunction.ArgType.QUERY, QueryFunction.ArgType.STRING, QueryFunction.ArgType.QUERY);
    /** A query, a string and another query */
    List<QueryFunction.ArgType> ARGS_QQS = List.of(QueryFunction.ArgType.QUERY, QueryFunction.ArgType.QUERY, QueryFunction.ArgType.STRING);
    /** Three queries as an argument */
    List<QueryFunction.ArgType> ARGS_QQQ = List.of(QueryFunction.ArgType.QUERY, QueryFunction.ArgType.QUERY, QueryFunction.ArgType.QUERY);
    /** Two queries, two strings */
    List<QueryFunction.ArgType> ARGS_QQSS = List.of(QueryFunction.ArgType.QUERY, QueryFunction.ArgType.QUERY, QueryFunction.ArgType.STRING, QueryFunction.ArgType.STRING);
    /** A string, a query, and three strings */
    List<QueryFunction.ArgType> ARGS_SQSS = List.of(QueryFunction.ArgType.STRING, QueryFunction.ArgType.QUERY, QueryFunction.ArgType.STRING, QueryFunction.ArgType.STRING);
    /** A string, a query, and three strings */
    List<QueryFunction.ArgType> ARGS_SQSSS = List.of(QueryFunction.ArgType.STRING, QueryFunction.ArgType.QUERY, QueryFunction.ArgType.STRING, QueryFunction.ArgType.STRING, QueryFunction.ArgType.STRING);
    /** A query and three strings */
    List<QueryFunction.ArgType> ARGS_QSSS = List.of(QueryFunction.ArgType.QUERY, QueryFunction.ArgType.STRING, QueryFunction.ArgType.STRING, QueryFunction.ArgType.STRING);

    void register();
}
