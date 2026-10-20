package nl.inl.blacklab.search.lucene;

import org.apache.lucene.index.Term;
import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.mocks.MockBlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.QueryInfo;

public class TestSpanQueryAnd {

    private QueryInfo queryInfo;

    @SuppressWarnings("unused")
    @Test(expected = RuntimeException.class)
    public void testFieldMismatch() {
        MockBlackLabIndex index = new MockBlackLabIndex();
        QueryInfo queryInfo = QueryInfo.create(index, "contents2");
        BLSpanTermQuery first = new BLSpanTermQuery(queryInfo, new Term(queryInfo.field(), "bla"));
        queryInfo = QueryInfo.create(index, "contents");
        BLSpanTermQuery second = new BLSpanTermQuery(queryInfo, new Term(queryInfo.field(), "bla"));

        // Different fields; will throw exception
        new SpanQueryAnd(first, second);
    }

    @Test
    public void testAnnotatedFieldDifferentProperties() {
        QueryInfo queryInfo = QueryInfo.create(new MockBlackLabIndex());
        BLSpanTermQuery first = new BLSpanTermQuery(queryInfo, new Term(AnnotatedFieldNameUtil.luceneName("contents",
                "prop1", MatchSensitivity.SENSITIVE), "bla"));
        BLSpanTermQuery second = new BLSpanTermQuery(queryInfo, new Term(AnnotatedFieldNameUtil.luceneName("contents",
                "prop2", MatchSensitivity.SENSITIVE), "bla"));

        // No exception here because both are properties of annotated field "field"
        SpanQueryAnd q = new SpanQueryAnd(first, second);

        // getField() will produce "base field name" of annotated field
        Assert.assertEquals("contents", q.getField());
    }

}
