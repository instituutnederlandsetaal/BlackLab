package nl.inl.blacklab.search.extensions;

import java.util.Collections;
import java.util.List;

/**
 * Class that adds query extension functions
 */
public interface ExtensionFunctionClass {

    /** Value to pass if there are no default parameter values. */
    List<Object> NO_DEFAULT_VALUES = Collections.emptyList();
    /** Variable number of query params */
    List<QueryExtensions.ArgType> ARGS_VAR_Q = List.of(QueryExtensions.ArgType.QUERY, QueryExtensions.ArgType.ELLIPSIS);
    /** Variable number of string params */
    List<QueryExtensions.ArgType> ARGS_VAR_S = List.of(QueryExtensions.ArgType.STRING, QueryExtensions.ArgType.ELLIPSIS);
    /** Two strings */
    List<QueryExtensions.ArgType> ARGS_S = List.of(QueryExtensions.ArgType.STRING);
    /** A single query as an argument */
    List<QueryExtensions.ArgType> ARGS_Q = List.of(QueryExtensions.ArgType.QUERY);
    /** Two strings */
    List<QueryExtensions.ArgType> ARGS_SS = List.of(QueryExtensions.ArgType.STRING, QueryExtensions.ArgType.STRING);
    /** Two strings */
    List<QueryExtensions.ArgType> ARGS_SQ = List.of(QueryExtensions.ArgType.STRING, QueryExtensions.ArgType.QUERY);
    /** A query and a string */
    List<QueryExtensions.ArgType> ARGS_QS = List.of(QueryExtensions.ArgType.QUERY, QueryExtensions.ArgType.STRING);
    /** Two queries as an argument */
    List<QueryExtensions.ArgType> ARGS_QQ = List.of(QueryExtensions.ArgType.QUERY, QueryExtensions.ArgType.QUERY);
    /** Two strings */
    List<QueryExtensions.ArgType> ARGS_SSS = List.of(QueryExtensions.ArgType.STRING, QueryExtensions.ArgType.STRING, QueryExtensions.ArgType.STRING);
    /** A query, a string and another query */
    List<QueryExtensions.ArgType> ARGS_SSQ = List.of(QueryExtensions.ArgType.STRING, QueryExtensions.ArgType.STRING, QueryExtensions.ArgType.QUERY);
    /** A query, a string and another query */
    List<QueryExtensions.ArgType> ARGS_SQS = List.of(QueryExtensions.ArgType.STRING, QueryExtensions.ArgType.QUERY, QueryExtensions.ArgType.STRING);
    /** A query, a string and another query */
    List<QueryExtensions.ArgType> ARGS_SQQ = List.of(QueryExtensions.ArgType.STRING, QueryExtensions.ArgType.QUERY, QueryExtensions.ArgType.QUERY);
    /** A query, a string and another query */
    List<QueryExtensions.ArgType> ARGS_QSS = List.of(QueryExtensions.ArgType.QUERY, QueryExtensions.ArgType.STRING, QueryExtensions.ArgType.STRING);
    /** A query, a string and another query */
    List<QueryExtensions.ArgType> ARGS_QSQ = List.of(QueryExtensions.ArgType.QUERY, QueryExtensions.ArgType.STRING, QueryExtensions.ArgType.QUERY);
    /** A query, a string and another query */
    List<QueryExtensions.ArgType> ARGS_QQS = List.of(QueryExtensions.ArgType.QUERY, QueryExtensions.ArgType.QUERY, QueryExtensions.ArgType.STRING);
    /** Three queries as an argument */
    List<QueryExtensions.ArgType> ARGS_QQQ = List.of(QueryExtensions.ArgType.QUERY, QueryExtensions.ArgType.QUERY, QueryExtensions.ArgType.QUERY);
    /** Two queries, two strings */
    List<QueryExtensions.ArgType> ARGS_QQSS = List.of(QueryExtensions.ArgType.QUERY, QueryExtensions.ArgType.QUERY, QueryExtensions.ArgType.STRING, QueryExtensions.ArgType.STRING);
    /** A string, a query, and three strings */
    List<QueryExtensions.ArgType> ARGS_SQSS = List.of(QueryExtensions.ArgType.STRING, QueryExtensions.ArgType.QUERY, QueryExtensions.ArgType.STRING, QueryExtensions.ArgType.STRING);
    /** A string, a query, and three strings */
    List<QueryExtensions.ArgType> ARGS_SQSSS = List.of(QueryExtensions.ArgType.STRING, QueryExtensions.ArgType.QUERY, QueryExtensions.ArgType.STRING, QueryExtensions.ArgType.STRING, QueryExtensions.ArgType.STRING);
    /** A query and three strings */
    List<QueryExtensions.ArgType> ARGS_QSSS = List.of(QueryExtensions.ArgType.QUERY, QueryExtensions.ArgType.STRING, QueryExtensions.ArgType.STRING, QueryExtensions.ArgType.STRING);

    void register();
}
