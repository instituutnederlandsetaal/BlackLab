package nl.inl.blacklab.search.lucene;

import org.apache.lucene.index.Term;
import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.mocks.MockBlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.results.QueryInfo;

public class TestSpanQueryAnd {

    @SuppressWarnings("unused")
    @Test(expected = RuntimeException.class)
    public void testFieldMismatch() {
        BLSpanTermQuery first = new BLSpanTermQuery(null, new Term("author", "bla"));
        BLSpanTermQuery second = new BLSpanTermQuery(null, new Term("contents", "bla"));

        // Different fields; will throw exception
        new SpanQueryAnd(first, second);
    }

    @Test
    public void testAnnotatedFieldDifferentProperties() {
        QueryInfo queryInfo = QueryInfo.create(new MockBlackLabIndex());
        BLSpanTermQuery first = new BLSpanTermQuery(queryInfo, new Term(AnnotatedFieldNameUtil.annotationField("contents",
                "prop1"), "bla"));
        BLSpanTermQuery second = new BLSpanTermQuery(queryInfo, new Term(AnnotatedFieldNameUtil.annotationField("contents",
                "prop2"), "bla"));

        // No exception here because both are properties of annotated field "field"
        SpanQueryAnd q = new SpanQueryAnd(first, second);

        // getField() will produce "base field name" of annotated field
        Assert.assertEquals("contents", q.getField());
    }

}
