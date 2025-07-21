package nl.inl.blacklab.search.extensions;

import java.util.Arrays;
import java.util.List;

import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryCaptureOverlappingSpans;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * Extension functions for querying spans ("inline tags").
 */
public class XFSpans implements ExtensionFunctionClass {

    /** Function to automatically capture any enclosing spans with each hit */
    public static final String FUNC_WITH_SPANS = "with-spans";

    /**
     * Find relations matching type and target.
     * <p>
     * You can also set spanMode (defaults to "source").
     *
     * @param queryInfo query info
     * @param context query execution context
     * @param args function arguments: relation type, target, spanMode
     * @return relations query
     */
    private static BLSpanQuery withSpans(QueryInfo queryInfo, QueryExecutionContext context, List<Object> args) {
        BLSpanQuery query = (BLSpanQuery) args.get(0);
        BLSpanQuery spans = (BLSpanQuery) args.get(1);
        String captureAs = context.ensureUniqueCapture((String)args.get(2));
        return new SpanQueryCaptureOverlappingSpans(query, spans, captureAs);
    }

    @Override
    public void register() {
        QueryExtensions.register(FUNC_WITH_SPANS, QueryExtensions.ARGS_QQS,
                Arrays.asList(null, QueryExtensions.VALUE_ANY_SPAN, "with-spans"),
                XFSpans::withSpans);
    }

}
