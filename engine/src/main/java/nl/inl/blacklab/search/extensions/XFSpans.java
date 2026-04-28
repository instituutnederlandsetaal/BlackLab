package nl.inl.blacklab.search.extensions;

import java.util.Arrays;
import java.util.List;

import nl.inl.blacklab.plugins.QueryFunction;
import nl.inl.blacklab.plugins.param.PQuery;
import nl.inl.blacklab.plugins.param.PString;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryCaptureOverlappingSpans;

/**
 * Extension functions for querying spans ("inline tags").
 */
public class XFSpans implements ExtensionFunctionClass {

    /** Function to automatically capture any enclosing spans with each hit */
    public static final String FUNC_WITH_SPANS = "with-spans";

    @Override
    public void register() {
        /// with-spans(query, spans, captureAs): automatically capture any enclosing spans with each hit.
        QueryExtensions.register(FUNC_WITH_SPANS, List.of(PQuery.required("query"),
                        PQuery.required("spans"), PString.identifier("captureAs")),
                Arrays.asList(null, QueryFunction.VALUE_ANY_SPAN, FUNC_WITH_SPANS),
                (queryInfo, context, args) -> {
                    BLSpanQuery query = (BLSpanQuery) args.get(0);
                    BLSpanQuery spans = (BLSpanQuery) args.get(1);
                    String captureAs = context.ensureUniqueCapture((String) args.get(2));
                    return new SpanQueryCaptureOverlappingSpans(query, spans, captureAs);
                });
    }

}
