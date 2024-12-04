package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermStates;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexIntegrated;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.RelationUtil;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 *
 * Returns spans corresponding to a certain element (tag) type.
 *
 * This version is for legacy indexes that don't have the new _relation annotation
 * (i.e. the classic external index format).
 *
 * For example, SpanQueryTags("ne") will give us spans for all the {@code <ne>}
 * elements in the document.
 */
public class SpanQueryTagsExternal extends BLSpanQuery implements TagQuery {

    public static SpanGuarantees createGuarantees() {
        return SpanGuarantees.SORTED;
    }

    final BLSpanTermQuery clause;

    private final String tagName;

    private final String baseFieldName;

    private final Map<String, String> attr;

    private final String startTagFieldName;

    public SpanQueryTagsExternal(QueryInfo queryInfo, String startTagFieldName, String tagName, Map<String, String> attr) {
        super(queryInfo);
        this.tagName = tagName;
        baseFieldName = AnnotatedFieldNameUtil.getBaseName(startTagFieldName);
        this.startTagFieldName = startTagFieldName;
        this.clause = new BLSpanTermQuery(queryInfo, new Term(startTagFieldName, tagName));
        this.attr = attr != null && attr.isEmpty() ? null : attr;
        this.guarantees = createGuarantees();
    }

    @Override
    public BLSpanQuery rewrite(IndexReader reader) {
        if (attr == null)
            return this;

        // Construct attribute filters
        List<BLSpanQuery> attrFilters = new ArrayList<>();
        for (Map.Entry<String, String> e : attr.entrySet()) {
            String value = RelationUtil.tagAttributeIndexValue(e.getKey(), e.getValue(),
                    BlackLabIndex.IndexType.EXTERNAL_FILES);
            attrFilters.add(new BLSpanTermQuery(queryInfo, new Term(startTagFieldName, value)));
        }

        // Filter the tags
        // (NOTE: only works for start tags and full elements because attribute values
        //  are indexed at the start tag!)
        BLSpanQuery filter;
        if (attrFilters.size() == 1)
            filter = attrFilters.get(0);
        else
            filter = new SpanQueryAnd(attrFilters);
        BLSpanQuery r = new SpanQueryPositionFilter(new SpanQueryTagsExternal(queryInfo, startTagFieldName, tagName, null), filter,
                SpanQueryPositionFilter.Operation.STARTS_AT, false);
        r.setQueryInfo(queryInfo);
        return r;
    }

    @Override
    public void visit(QueryVisitor visitor) {
        if (visitor.acceptField(getField())) {
            clause.visit(visitor.getSubVisitor(Occur.MUST, this));
        }
    }

    @Override
    public BLSpanWeight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        if (attr != null)
            throw new BlackLabRuntimeException("Query should've been rewritten! (attr != null)");
        BLSpanWeight weight = clause.createWeight(searcher, scoreMode, boost);
        return new SpanWeightTags(weight, searcher, scoreMode.needsScores() ? getTermStates(weight) : null, boost);
    }

    class SpanWeightTags extends BLSpanWeight {

        final BLSpanWeight weight;

        public SpanWeightTags(BLSpanWeight weight, IndexSearcher searcher, Map<Term, TermStates> terms, float boost)
                throws IOException {
            super(SpanQueryTagsExternal.this, searcher, terms, boost);
            this.weight = weight;
        }

        @Override
        public void extractTerms(Set<Term> terms) {
            weight.extractTerms(terms);
        }

        @Override
        public boolean isCacheable(LeafReaderContext ctx) {
            return weight.isCacheable(ctx);
        }

        @Override
        public void extractTermStates(Map<Term, TermStates> contexts) {
            weight.extractTermStates(contexts);
        }

        @Override
        public BLSpans getSpans(final LeafReaderContext context, Postings requiredPostings) throws IOException {
            BLSpans startTags = weight.getSpans(context, requiredPostings);
            if (startTags == null)
                return null;
            FieldInfo fieldInfo = context.reader().getFieldInfos().fieldInfo(startTagFieldName);
            boolean primaryIndicator = BlackLabIndexIntegrated.doesFieldHaveForwardIndex(fieldInfo);
            return new SpansTagsExternal(startTags, primaryIndicator);
        }

    }

    @Override
    public String toString(String field) {
        if (attr != null && !attr.isEmpty())
            return "TAGS(" + tagName + ", " + attr + ")";
        return "TAGS(" + tagName + ")";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((attr == null) ? 0 : attr.hashCode());
        result = prime * result + ((clause == null) ? 0 : clause.hashCode());
        result = prime * result + ((startTagFieldName == null) ? 0 : startTagFieldName.hashCode());
        result = prime * result + ((tagName == null) ? 0 : tagName.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        SpanQueryTagsExternal other = (SpanQueryTagsExternal) obj;
        if (attr == null) {
            if (other.attr != null)
                return false;
        } else if (!attr.equals(other.attr))
            return false;
        if (clause == null) {
            if (other.clause != null)
                return false;
        } else if (!clause.equals(other.clause))
            return false;
        if (startTagFieldName == null) {
            if (other.startTagFieldName != null)
                return false;
        } else if (!startTagFieldName.equals(other.startTagFieldName))
            return false;
        if (tagName == null) {
            if (other.tagName != null)
                return false;
        } else if (!tagName.equals(other.tagName))
            return false;
        return true;
    }

    /**
     * Returns the name of the search field. In the case of a annotated field, the
     * clauses may actually query different annotations of the same annotated field
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
        return startTagFieldName;
    }

    public String getElementName() {
        return tagName;
    }

    @Override
    public long reverseMatchingCost(IndexReader reader) {
        return clause.reverseMatchingCost(reader);
    }

    @Override
    public int forwardMatchingCost() {
        return clause.forwardMatchingCost();
    }

    @Override
    public void setQueryInfo(QueryInfo queryInfo) {
        super.setQueryInfo(queryInfo);
        clause.setQueryInfo(queryInfo);
    }
}
