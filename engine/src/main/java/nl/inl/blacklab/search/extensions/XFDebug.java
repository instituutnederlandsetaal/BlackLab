package nl.inl.blacklab.search.extensions;

import java.util.Arrays;

import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.NfaTwoWay;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryAdjustHits;
import nl.inl.blacklab.search.lucene.SpanQueryEdge;
import nl.inl.blacklab.search.lucene.SpanQueryFiSeq;

/**
 * Extension functions for debugging forward index matching.
 */
public class XFDebug implements ExtensionFunctionClass {

    public void register() {
        // Resolve the first query using the forward index and the second using the inverted index
        QueryExtensions.register("_FI1", ARGS_QQ, NO_DEFAULT_VALUES,
                (queryInfo, context, args) -> {
                    BLSpanQuery a = (BLSpanQuery) args.get(0);
                    BLSpanQuery b = (BLSpanQuery) args.get(1);
                    ForwardIndexAccessor fiAccessor = context.index().forwardIndexAccessor(b.getField());
                    NfaTwoWay nfaTwoWay = b.getNfaTwoWay(fiAccessor, SpanQueryFiSeq.DIR_TO_RIGHT);
                    return new SpanQueryFiSeq(a, SpanQueryFiSeq.END_OF_ANCHOR, nfaTwoWay, b, SpanQueryFiSeq.DIR_TO_RIGHT,
                            fiAccessor);
                });
        // Resolve the second query using the forward index and the first using the inverted index
        QueryExtensions.register("_FI2", ARGS_QQ, NO_DEFAULT_VALUES,
                (queryInfo, context, args) -> {
                    BLSpanQuery a = (BLSpanQuery) args.get(0);
                    BLSpanQuery b = (BLSpanQuery) args.get(1);
                    ForwardIndexAccessor fiAccessor = context.index().forwardIndexAccessor(a.getField());
                    NfaTwoWay nfaTwoWay = a.getNfaTwoWay(fiAccessor, SpanQueryFiSeq.DIR_TO_LEFT);
                    return new SpanQueryFiSeq(b, SpanQueryFiSeq.START_OF_ANCHOR, nfaTwoWay, a, SpanQueryFiSeq.DIR_TO_LEFT,
                            fiAccessor);
                });
        // Return the argument unchanged
        QueryExtensions.register("_ident", ARGS_Q, NO_DEFAULT_VALUES,
                (queryInfo, context, args) -> (BLSpanQuery) args.get(0));

        // Adjust hits
        QueryExtensions.register("_adjust", ARGS_QSS, Arrays.asList(null, "0", "0"),
                (queryInfo, context, args) -> {
                    BLSpanQuery query = (BLSpanQuery) args.get(0);
                    int startAdjust = Integer.parseInt((String) args.get(1));
                    int endAdjust = Integer.parseInt((String) args.get(2));
                    return new SpanQueryAdjustHits(query, startAdjust, endAdjust);
                });

        // Get the leading or trailing edge of the query
        QueryExtensions.register("_edge", ARGS_QS, Arrays.asList(null, "leading"),
                (queryInfo, context, args) -> {
                    BLSpanQuery query = (BLSpanQuery) args.get(0);
                    String whichEdge = ((String) args.get(1)).toLowerCase();
                    boolean trailingEdge = whichEdge.matches("t(railing)?|a(fter)?|r");
                    return new SpanQueryEdge(query, trailingEdge);
                });
    }

}
