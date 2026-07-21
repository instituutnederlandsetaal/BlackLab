package nl.inl.blacklab.search.extensions;

import java.util.List;

import nl.inl.blacklab.plugins.param.PString;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryAdjustHits;
import nl.inl.blacklab.search.textpattern.TextPattern;

/**
 * Extension functions for easily matching punctuation.
 */
public class XFPunctBeforeAfter implements ExtensionFunctionClass {

    /** Lucene regex for one or more non-whitespace characters (Lucene v8 doesn't support \S yet; v9+ does) */
    public static final String REGEX_ANY_NON_WS = "[^ \t\r\n]+";

    /** Lucene regex for zero or more whitespace characters (Lucene v8 doesn't support \s yet; v9+ does) */
    public static final String REGEX_OPT_WS = "[ \t\r\n]*";

    /** Register the punctBefore and punctAfter functions to simplify finding punctuation. */
    @Override
    public void register() {
        QueryExtensions.register("punctBefore", "Find punctuation before a token",
            List.of(PString.any("regex")),
                List.of(REGEX_ANY_NON_WS),
            (queryInfo, context, args) ->
                    getPunctQuery(context, REGEX_OPT_WS + args.get(0) + REGEX_OPT_WS)
        );
        QueryExtensions.register("punctAfter", "Find punctuation after a token",
            List.of(PString.any("regex")),
            List.of(REGEX_ANY_NON_WS),
            (queryInfo, context, args) -> {
                BLSpanQuery punctQuery = getPunctQuery(context, REGEX_OPT_WS + args.get(0) + REGEX_OPT_WS);
                return new SpanQueryAdjustHits(punctQuery, -1, -1);
            }
        );
    }

    /**
     * Get a query that matches the specified regex in the punct annotation.
     *
     * @param context the query execution context
     * @param regex the regex to match
     * @return the query
     */
    private static BLSpanQuery getPunctQuery(QueryExecutionContext context, String regex) {
        return TextPattern.regex(regex, AnnotatedFieldNameUtil.PUNCTUATION_ANNOT_NAME,
                MatchSensitivity.INSENSITIVE).toQuery(context);
    }

}
