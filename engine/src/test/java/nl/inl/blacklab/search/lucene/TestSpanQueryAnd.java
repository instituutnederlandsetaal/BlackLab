package nl.inl.blacklab.search.lucene;

import org.apache.lucene.index.Term;
import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.mocks.MockBlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.IndexAndField;

public class TestSpanQueryAnd {

    private IndexAndField indexAndField;

    @SuppressWarnings("unused")
    @Test(expected = RuntimeException.class)
    public void testFieldMismatch() {
        MockBlackLabIndex index = new MockBlackLabIndex();
        BLSpanQuery.Context context = index.queryContext("contents2");
        BLSpanTermQuery first = new BLSpanTermQuery(context, new Term(indexAndField.field(), "bla"));
        context = index.queryContext("contents");
        BLSpanTermQuery second = new BLSpanTermQuery(context, new Term(indexAndField.field(), "bla"));

        // Different fields; will throw exception
        new SpanQueryAnd(first, second);
    }

    @Test
    public void testAnnotatedFieldDifferentProperties() {
        MockBlackLabIndex index = new MockBlackLabIndex();
        BLSpanQuery.Context context = index.defaultQueryContext();
        BLSpanTermQuery first = new BLSpanTermQuery(context, new Term(AnnotatedFieldNameUtil.luceneName("contents",
                "prop1", MatchSensitivity.SENSITIVE), "bla"));
        BLSpanTermQuery second = new BLSpanTermQuery(context, new Term(AnnotatedFieldNameUtil.luceneName("contents",
                "prop2", MatchSensitivity.SENSITIVE), "bla"));

        // No exception here because both are properties of annotated field "field"
        SpanQueryAnd q = new SpanQueryAnd(first, second);

        // getField() will produce "base field name" of annotated field
        Assert.assertEquals("contents", q.getField());
    }

}
