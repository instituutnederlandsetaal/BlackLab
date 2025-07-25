package nl.inl.blacklab.search.lucene;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;
import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.mocks.MockBlackLabIndex;
import nl.inl.blacklab.search.results.QueryInfo;

public class TestSpanQueryFiltered {

    @Test
    public void testEquals() {
        QueryInfo queryInfo = QueryInfo.create(new MockBlackLabIndex());
        SpanQueryFiltered a = new SpanQueryFiltered(new BLSpanTermQuery(queryInfo, new Term("contents", "bla")), new TermQuery(new Term("author", "Zwets")));
        SpanQueryFiltered b = new SpanQueryFiltered(new BLSpanTermQuery(queryInfo, new Term("contents", "bla")), new TermQuery(new Term("author", "Neuzel")));
        SpanQueryFiltered c = new SpanQueryFiltered(new BLSpanTermQuery(queryInfo, new Term("contents", "bla")), new TermQuery(new Term("author", "Neuzel")));
        Assert.assertNotEquals(a, b);
        Assert.assertEquals(b, c);
    }

}
