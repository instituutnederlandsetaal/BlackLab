import nl.inl.blacklab.plugins.QueryFunction
import nl.inl.blacklab.search.QueryExecutionContext
import nl.inl.blacklab.search.lucene.BLSpanQuery
import org.apache.lucene.index.Term
import org.apache.lucene.queries.spans.BLSpanOrQuery
import org.apache.lucene.queries.spans.SpanTermQuery

class QueryFunctionFixedSpan extends QueryFunction {
    QueryFunctionFixedSpan() {
        super("orReverse", ARGS_S, Arrays.asList(null), false)
    }

    BLSpanQuery term(String field, String value) {
        return BLSpanQuery.wrap(context.queryInfo(), new SpanTermQuery(new Term(field, value)))
    }

    BLSpanQuery applyFunc(QueryExecutionContext context, List<Object> parameters) {
        String field = context.field().mainAnnotation().mainSensitivity().luceneField()
        String value = (String) parameters.get(0)
        BLSpanQuery a = term(field, value)
        BLSpanQuery b = term(field, value.reverse())
        return new BLSpanOrQuery(a, b)
    }
}
return new QueryFunctionFixedSpan()
