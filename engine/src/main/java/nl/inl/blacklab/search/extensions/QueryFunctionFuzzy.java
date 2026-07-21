package nl.inl.blacklab.search.extensions;

import java.util.Arrays;
import java.util.List;

import org.apache.lucene.index.Term;

import nl.inl.blacklab.plugins.QueryFunction;
import nl.inl.blacklab.plugins.param.PInteger;
import nl.inl.blacklab.plugins.param.PString;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.SpanFuzzyQuery;
import nl.inl.blacklab.search.textpattern.TextPattern;

/** Does fuzzy matching. */
public class QueryFunctionFuzzy extends QueryFunction {
    public QueryFunctionFuzzy() {
        super("_fuzzy", "Performs fuzzy matching",
                List.of(
                PString.any("find", true),
                PInteger.nonnegative("maxEdits", true),
                PInteger.nonnegative("prefixLength", true)
            ),
            Arrays.asList(null, 2, 0), false
        );
    }

    public TextPattern.EvalResult applyFunc(QueryExecutionContext context, List<Object> parameters) {
        String word = (String) parameters.get(0);
        int maxEdits = (Integer) parameters.get(1);
        int prefixLength = (Integer) parameters.get(2);
        if (maxEdits < 0 || prefixLength < 0)
            throw new IllegalArgumentException("fuzzy(word, maxEdits=2, prefixLength=0) takes non-negative integers as its last two args");
        Term term = new Term(context.luceneField(), context.optDesensitize(word));
        return new SpanFuzzyQuery(context.queryInfo(), term, maxEdits, prefixLength);
    }

}
