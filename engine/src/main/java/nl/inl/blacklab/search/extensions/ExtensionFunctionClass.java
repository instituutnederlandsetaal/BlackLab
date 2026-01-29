package nl.inl.blacklab.search.extensions;

import java.util.Collections;
import java.util.List;

import nl.inl.blacklab.plugins.ExprType;

/**
 * Class that adds query extension functions
 */
public interface ExtensionFunctionClass {

    /** Value to pass if there are no default parameter values. */
    List<Object> NO_DEFAULT_VALUES = Collections.emptyList();
    /** Two strings */
    List<ExprType> ARGS_S = List.of(ExprType.STRING);
    /** A single query as an argument */
    List<ExprType> ARGS_Q = List.of(ExprType.QUERY);
    /** Two strings */
    List<ExprType> ARGS_SS = List.of(ExprType.STRING, ExprType.STRING);
    /** Two strings */
    List<ExprType> ARGS_SQ = List.of(ExprType.STRING, ExprType.QUERY);
    /** A query and a string */
    List<ExprType> ARGS_QS = List.of(ExprType.QUERY, ExprType.STRING);
    /** Two queries as an argument */
    List<ExprType> ARGS_QQ = List.of(ExprType.QUERY, ExprType.QUERY);
    /** Two strings */
    List<ExprType> ARGS_SSS = List.of(ExprType.STRING, ExprType.STRING, ExprType.STRING);
    /** A query, a string and another query */
    List<ExprType> ARGS_SSQ = List.of(ExprType.STRING, ExprType.STRING, ExprType.QUERY);
    /** A query, a string and another query */
    List<ExprType> ARGS_SQS = List.of(ExprType.STRING, ExprType.QUERY, ExprType.STRING);
    /** A query, a string and another query */
    List<ExprType> ARGS_SQQ = List.of(ExprType.STRING, ExprType.QUERY, ExprType.QUERY);
    /** A query, a string and another query */
    List<ExprType> ARGS_QSS = List.of(ExprType.QUERY, ExprType.STRING, ExprType.STRING);
    /** A query, a string and another query */
    List<ExprType> ARGS_QSQ = List.of(ExprType.QUERY, ExprType.STRING, ExprType.QUERY);
    /** A query, a string and another query */
    List<ExprType> ARGS_QQS = List.of(ExprType.QUERY, ExprType.QUERY, ExprType.STRING);
    /** Three queries as an argument */
    List<ExprType> ARGS_QQQ = List.of(ExprType.QUERY, ExprType.QUERY, ExprType.QUERY);
    /** Two queries, two strings */
    List<ExprType> ARGS_QQSS = List.of(ExprType.QUERY, ExprType.QUERY, ExprType.STRING, ExprType.STRING);
    /** A string, a query, and three strings */
    List<ExprType> ARGS_SQSS = List.of(ExprType.STRING, ExprType.QUERY, ExprType.STRING, ExprType.STRING);
    /** A string, a query, and three strings */
    List<ExprType> ARGS_SQSSS = List.of(ExprType.STRING, ExprType.QUERY, ExprType.STRING, ExprType.STRING, ExprType.STRING);
    /** A query and three strings */
    List<ExprType> ARGS_QSSS = List.of(ExprType.QUERY, ExprType.STRING, ExprType.STRING, ExprType.STRING);

    void register();
}
