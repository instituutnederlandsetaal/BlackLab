package nl.inl.blacklab.search.extensions;

import java.util.Arrays;

import nl.inl.blacklab.search.SingleDocIdFilter;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.NfaTwoWay;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryAdjustHits;
import nl.inl.blacklab.search.lucene.SpanQueryEdge;
import nl.inl.blacklab.search.lucene.SpanQueryFiSeq;
import nl.inl.blacklab.search.lucene.SpanQueryFilterByHitLength;
import nl.inl.blacklab.search.lucene.SpanQueryFiltered;
import nl.inl.blacklab.search.lucene.SpanQueryFixedSpan;
import nl.inl.blacklab.search.lucene.SpanQueryPositionFilter;

/**
 * Extension functions for debugging forward index matching.
 */
public class XFDebug implements ExtensionFunctionClass {

    public void register() {

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

        // Resolve the first query using the forward index and the second using the inverted index
        QueryExtensions.register("_fimatch", ARGS_QQS, Arrays.asList(null, null, "0"),
                (queryInfo, context, args) -> {
                    BLSpanQuery a = (BLSpanQuery) args.get(0);
                    BLSpanQuery b = (BLSpanQuery) args.get(1);
                    int fiIndex = Integer.parseInt((String) args.get(2));
                    if (fiIndex != 1)
                        fiIndex = 0;
                    if (fiIndex == 0) {
                        // Resolve the first query using the forward index and the second using the inverted index
                        ForwardIndexAccessor fiAccessor = context.index().forwardIndexAccessor(a.getField());
                        NfaTwoWay nfaTwoWay = a.getNfaTwoWay(fiAccessor, SpanQueryFiSeq.DIR_TO_LEFT);
                        return new SpanQueryFiSeq(b, SpanQueryFiSeq.START_OF_ANCHOR, nfaTwoWay, a, SpanQueryFiSeq.DIR_TO_LEFT,
                                fiAccessor);
                    } else {
                        // Resolve the second query using the forward index and the first using the inverted index
                        ForwardIndexAccessor fiAccessor = context.index().forwardIndexAccessor(b.getField());
                        NfaTwoWay nfaTwoWay = b.getNfaTwoWay(fiAccessor, SpanQueryFiSeq.DIR_TO_RIGHT);
                        return new SpanQueryFiSeq(a, SpanQueryFiSeq.END_OF_ANCHOR, nfaTwoWay, b,
                                SpanQueryFiSeq.DIR_TO_RIGHT,
                                fiAccessor);
                    }
                });
        // Resolve the first query using the forward index and the second using the inverted index
        QueryExtensions.register("_FI1", ARGS_QQ, NO_DEFAULT_VALUES,
                (queryInfo, context, args) -> {
                    BLSpanQuery a = (BLSpanQuery) args.get(0);
                    BLSpanQuery b = (BLSpanQuery) args.get(1);
                    ForwardIndexAccessor fiAccessor = context.index().forwardIndexAccessor(a.getField());
                    NfaTwoWay nfaTwoWay = a.getNfaTwoWay(fiAccessor, SpanQueryFiSeq.DIR_TO_LEFT);
                    return new SpanQueryFiSeq(b, SpanQueryFiSeq.START_OF_ANCHOR, nfaTwoWay, a, SpanQueryFiSeq.DIR_TO_LEFT,
                            fiAccessor);
                });
        // Resolve the second query using the forward index and the first using the inverted index
        QueryExtensions.register("_FI2", ARGS_QQ, NO_DEFAULT_VALUES,
                (queryInfo, context, args) -> {
                    BLSpanQuery a = (BLSpanQuery) args.get(0);
                    BLSpanQuery b = (BLSpanQuery) args.get(1);
                    ForwardIndexAccessor fiAccessor = context.index().forwardIndexAccessor(b.getField());
                    NfaTwoWay nfaTwoWay = b.getNfaTwoWay(fiAccessor, SpanQueryFiSeq.DIR_TO_RIGHT);
                    return new SpanQueryFiSeq(a, SpanQueryFiSeq.END_OF_ANCHOR, nfaTwoWay, b, SpanQueryFiSeq.DIR_TO_RIGHT,
                            fiAccessor);
                });

        // A fixed span in every matching doc, e.g. _fixed("0", "7") find tokens 0 (inclusive) to 7 (exclusive) in
        // every doc
        QueryExtensions.register("_fixed", ARGS_SS, Arrays.asList(null, null),
                (queryInfo, context, args) -> {
                    int start = Integer.parseInt((String) args.get(0));
                    int end = Integer.parseInt((String) args.get(1));
                    return new SpanQueryFixedSpan(queryInfo, context.luceneField(), start, end);
                });

        // Return the argument unchanged
        QueryExtensions.register("_ident", ARGS_Q, NO_DEFAULT_VALUES,
                (queryInfo, context, args) -> (BLSpanQuery) args.get(0));

        // Search within a single docId, e.g. _indoc("water", "3") to find "water" in docId 3 only
        QueryExtensions.register("_indoc", ARGS_QS, NO_DEFAULT_VALUES,
                (queryInfo, context, args) -> {
                    BLSpanQuery query = (BLSpanQuery) args.get(0);
                    int docId = Integer.parseInt((String) args.get(1));
                    return new SpanQueryFiltered(query, new SingleDocIdFilter(docId));
                });

        // Filter by hit length; min and max are inclusive.
        QueryExtensions.register("_lenfilter", ARGS_QSS, Arrays.asList(null, "0", "0"),
                (queryInfo, context, args) -> {
                    BLSpanQuery query = (BLSpanQuery) args.get(0);
                    int minLength = Integer.parseInt((String) args.get(1));
                    int maxLength = Integer.parseInt((String) args.get(2));
                    return new SpanQueryFilterByHitLength(query, minLength, maxLength);
                });

        // Filter producer hits by filter query using the specified operation (optionally inverted)
        QueryExtensions.register("_posfilter", ARGS_QQSS, Arrays.asList(null, null, "matches", "false"),
                (queryInfo, context, args) -> {
                    BLSpanQuery producer = (BLSpanQuery) args.get(0);
                    BLSpanQuery filter = (BLSpanQuery) args.get(1);
                    SpanQueryPositionFilter.Operation operation = SpanQueryPositionFilter.Operation.fromStringValue((String) args.get(2));
                    boolean inverted = Boolean.parseBoolean((String) args.get(3));
                    return new SpanQueryPositionFilter(producer, filter, operation, inverted);
                });
    }

}
