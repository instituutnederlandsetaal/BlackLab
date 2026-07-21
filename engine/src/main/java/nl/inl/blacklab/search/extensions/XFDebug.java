package nl.inl.blacklab.search.extensions;

import java.util.Arrays;
import java.util.List;

import nl.inl.blacklab.plugins.param.PBoolean;
import nl.inl.blacklab.plugins.param.PEnum;
import nl.inl.blacklab.plugins.param.PInteger;
import nl.inl.blacklab.plugins.param.PQuery;
import nl.inl.blacklab.plugins.param.PString;
import nl.inl.blacklab.search.SingleDocIdFilter;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.NfaTwoWay;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanFilter;
import nl.inl.blacklab.search.lucene.SpanQueryAdjustHits;
import nl.inl.blacklab.search.lucene.SpanQueryEdge;
import nl.inl.blacklab.search.lucene.SpanQueryFiSeq;
import nl.inl.blacklab.search.lucene.SpanQueryFilterByHitLength;
import nl.inl.blacklab.search.lucene.SpanQueryFiltered;
import nl.inl.blacklab.search.lucene.SpanQueryPositionFilter;

/**
 * Extension functions for debugging forward index matching.
 */
public class XFDebug implements ExtensionFunctionClass {

    @Override
    public void register() {

        // Adjust hits
        QueryExtensions.register("_adjust", "Adjust starts and ends of hit",
                List.of(
                        PQuery.required("query"),
                        PInteger.any("before"),
                        PInteger.any("after")),
                Arrays.asList(null, 0, 0),
                (queryInfo, context, args) -> {
                    BLSpanQuery query = (BLSpanQuery) args.get(0);
                    int startAdjust = (Integer) args.get(1);
                    int endAdjust = (Integer) args.get(2);
                    return new SpanQueryAdjustHits(query, startAdjust, endAdjust);
                });

        // Get the leading or trailing edge of the query
        QueryExtensions.register("_edge", "Get the leading or trailing edge of hit",
                List.of(
                PQuery.required("query"),
                PString.matching("whichEdge", "l(eading)?|b(efore)?|t(railing)?|a(after)?")
                ), Arrays.asList(null, "leading"),
                (queryInfo, context, args) -> {
                    BLSpanQuery query = (BLSpanQuery) args.get(0);
                    String whichEdge = ((String) args.get(1)).toLowerCase();
                    boolean trailingEdge = whichEdge.matches("t(railing)?|a(fter)?|r");
                    return new SpanQueryEdge(query, trailingEdge);
                });

        // Resolve the first query using the forward index and the second using the inverted index
        QueryExtensions.register("_fimatch", "Force matching one of the clauses using the forward index",
                List.of(
                    PQuery.required("first"),
                    PQuery.required("second"),
                    PInteger.range("fiClause", 0, 1)),
                    Arrays.asList(null, null, 0),
                (queryInfo, context, args) -> {
                    BLSpanQuery a = (BLSpanQuery) args.get(0);
                    BLSpanQuery b = (BLSpanQuery) args.get(1);
                    int fiIndex = (Integer) args.get(2);
                    if (fiIndex != 1)
                        fiIndex = 0;
                    if (fiIndex == 0) {
                        // Resolve the first query using the forward index and the second using the inverted index
                        ForwardIndexAccessor fiAccessor = a.getAnnotatedField().forwardIndexAccessor();
                        NfaTwoWay nfaTwoWay = a.getNfaTwoWay(fiAccessor, SpanQueryFiSeq.DIR_TO_LEFT);
                        return new SpanQueryFiSeq(b, SpanQueryFiSeq.START_OF_ANCHOR, nfaTwoWay, a, SpanQueryFiSeq.DIR_TO_LEFT,
                                fiAccessor);
                    } else {
                        // Resolve the second query using the forward index and the first using the inverted index
                        ForwardIndexAccessor fiAccessor = a.getAnnotatedField().forwardIndexAccessor();
                        NfaTwoWay nfaTwoWay = b.getNfaTwoWay(fiAccessor, SpanQueryFiSeq.DIR_TO_RIGHT);
                        return new SpanQueryFiSeq(a, SpanQueryFiSeq.END_OF_ANCHOR, nfaTwoWay, b,
                                SpanQueryFiSeq.DIR_TO_RIGHT,
                                fiAccessor);
                    }
                });

        // Return the argument unchanged
        QueryExtensions.register("_ident", "Return the argument unchanged",
                List.of(PQuery.required("query")), List.of(),
                (queryInfo, context, args) -> (BLSpanQuery) args.get(0));

        // Search within a single docId, e.g. _indoc("water", "3") to find "water" in docId 3 only
        QueryExtensions.register("_indoc", "Search within a single Lucene doc id",
            List.of(
            PQuery.required("query"),
            PInteger.nonnegative("docId", true)
            ),
            List.of(),
            (queryInfo, context, args) -> {
                BLSpanQuery query = (BLSpanQuery) args.get(0);
                int docId = (Integer) args.get(1);
                return new SpanQueryFiltered(query, new SingleDocIdFilter(docId));
            }
        );

        // Filter by hit length; min and max are inclusive.
        QueryExtensions.register("_lenfilter", "Filter by hit length (min and max inclusive)",
            List.of(
                PQuery.required("query"),
                PInteger.nonnegative("minLength", true),
                PInteger.nonnegative("maxLength", true)),
                Arrays.asList(null, 0, 0),
                (queryInfo, context, args) -> {
                    BLSpanQuery query = (BLSpanQuery) args.get(0);
                    int minLength = (Integer) args.get(1);
                    int maxLength = (Integer) args.get(2);
                    return new SpanQueryFilterByHitLength(query, minLength, maxLength);
                });

        // Filter producer hits by filter query using the specified operation (optionally inverted)
        List<String> posFilterOps = Arrays.asList(SpanFilter.values()).stream()
                .map(v -> v.toString()).toList();
        QueryExtensions.register("_posfilter", "Construct a position filter query",
            List.of(
                PQuery.required("producer"),
                PQuery.required("filter"),
                PEnum.of("operation", SpanFilter.class, true),
                PBoolean.required("inverted")),
                Arrays.asList(null, null, "matches", false),
                (queryInfo, context, args) -> {
                    BLSpanQuery producer = (BLSpanQuery) args.get(0);
                    BLSpanQuery filter = (BLSpanQuery) args.get(1);
                    SpanFilter operation = SpanFilter.fromStringValue((String) args.get(2));
                    boolean inverted = (boolean) args.get(3);
                    return new SpanQueryPositionFilter(producer, filter, operation, inverted);
                });
    }

}
