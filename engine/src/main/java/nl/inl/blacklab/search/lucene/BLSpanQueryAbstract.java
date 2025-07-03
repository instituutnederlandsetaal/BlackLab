package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.QueryVisitor;

import nl.inl.blacklab.search.results.QueryInfo;

/**
 * A base class for a SpanQuery with an array of clauses. Provides default
 * implementations of some abstract methods in SpanQuery.
 */
abstract class BLSpanQueryAbstract extends BLSpanQuery {
    /**
     * The field name for this query. The "base" part is only applicable when
     * dealing with annotated fields: the base field name of "contents" and
     * "contents%pos" would both be "contents".
     */
    protected String baseFieldName = "";

    protected String luceneFieldName = "";

    protected List<BLSpanQuery> clauses;

    protected BLSpanQueryAbstract(Collection<BLSpanQuery> clauses) {
        super(getQueryInfo(clauses));
        this.clauses = new ArrayList<>(clauses);
        baseFieldName = checkAllCompatibleFields(clauses);
        luceneFieldName = getLuceneFieldName(clauses);
    }

    private String getLuceneFieldName(Collection<BLSpanQuery> clauses) {
        for (BLSpanQuery clause: clauses) {
            if (clause != null)
                return clause.getRealField();
        }
        return "";
    }

    private static QueryInfo getQueryInfo(Collection<BLSpanQuery> clauses) {
        for (BLSpanQuery clause: clauses) {
            if (clause != null)
                return clause.queryInfo;
        }
        return null;
    }

    protected BLSpanQueryAbstract(BLSpanQuery... clauses) {
        this(Arrays.asList(clauses));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || this.getClass() != o.getClass())
            return false;

        final BLSpanQueryAbstract that = (BLSpanQueryAbstract) o;

        return clauses.equals(that.clauses);
    }

    @Override
    public int hashCode() {
        int h = clauses.hashCode();
        h ^= (h << 10) | (h >>> 23);
        return h;
    }

    /**
     * Returns the name of the search field. In the case of a annotated field, the
     * clauses may actually query different properties of the same annotated field
     * (e.g. "description" and "description__pos"). That's why only the prefix is
     * returned.
     *
     * @return name of the search field
     */
    @Override
    public String getField() {
        return baseFieldName;
    }

    @Override
    public String getRealField() {
        return luceneFieldName;
    }

    List<BLSpanQuery> getClauses() {
        return clauses;
    }

    protected List<BLSpanQuery> rewriteClauses(IndexReader reader) throws IOException {
        List<BLSpanQuery> rewritten = new ArrayList<>(clauses.size());
        boolean someRewritten = false;
        for (BLSpanQuery c: clauses) {
            BLSpanQuery query = c == null ? null : c.rewrite(reader);
            rewritten.add(query);
            if (query != c)
                someRewritten = true;
        }
        return someRewritten ? rewritten : null;
    }

    @Override
    public void visit(QueryVisitor visitor) {
        if (visitor.acceptField(getField())) {
            for (BLSpanQuery clause: clauses) {
                if (clause != null)
                    clause.visit(visitor.getSubVisitor(Occur.MUST, this));
            }
        }
    }

    public String clausesToString(String field) {
        return clauses.stream()
                .map(clause -> clause == null ? "(null)" : clause.toString(field))
                .collect(Collectors.joining(", "));
    }
    
    @Override
    public void setQueryInfo(QueryInfo queryInfo) {
        super.setQueryInfo(queryInfo);
        for (BLSpanQuery clause: clauses) {
            if (clause != null)
                clause.setQueryInfo(queryInfo);
        }
    }
}
