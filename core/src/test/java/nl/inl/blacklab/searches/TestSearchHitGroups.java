package nl.inl.blacklab.searches;

import java.util.Collection;
import java.util.Iterator;

import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.HitGroupPropertyIdentity;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.HitPropertyDocumentStoredField;
import nl.inl.blacklab.resultproperty.HitPropertyHitText;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryFiltered;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.hitresults.HitGroup;
import nl.inl.blacklab.search.results.hitresults.HitGroups;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.TextPatternAnyToken;
import nl.inl.blacklab.testutil.TestIndex;

@RunWith(Parameterized.class)
public class TestSearchHitGroups {

    @Parameterized.Parameters(name = "index type {0}")
    public static Collection<TestIndex> typeToUse() {
        return TestIndex.typesForTests();
    }

    @Parameterized.Parameter
    public TestIndex testIndex;

    private BlackLabIndex index;

    private AnnotatedField contents;

    @Before
    public void setUp() {
        index = testIndex.index();
        contents = index.mainAnnotatedField();
    }

    @Test
    public void testHitGroups() throws InvalidQuery {
        MatchAllDocsQuery filter = new MatchAllDocsQuery();
        testGroup(true, filter); // fast path
        testGroup(false, filter); // slow path

        testGroup(false, null); // slow path
        testGroup(true, null); // fast path
    }

    private void testGroup(boolean fastPath, Query filter) throws InvalidQuery {
        String title = fastPath ? "group fast path" : "group slow path";
        TextPattern tp = new TextPatternAnyToken(1, 1);
        BLSpanQuery query = tp.toQuery(QueryInfo.create(index));
        if (filter != null)
            query = new SpanQueryFiltered(query, filter);
        HitProperty groupBy = new HitPropertyHitText(index, contents.mainAnnotation(), MatchSensitivity.SENSITIVE);
        SearchHits searchHits = index.search(contents, false).find(query);
        SearchHitGroups searchHitGroups = fastPath ? searchHits.groupStats(groupBy, 0) :
                searchHits.groupWithStoredHits(groupBy, 1);
        SearchHitGroups sortedGroups = searchHitGroups.sort(HitGroupPropertyIdentity.get());
        HitGroups groups = sortedGroups.execute();
        Assert.assertEquals(title + " # groups", 25, groups.size());
        Iterator<HitGroup> it = groups.iterator();
        HitGroup g = it.next();
        Assert.assertEquals(title + " 1st group size", 5, g.size());
        Assert.assertEquals(title + " 1st group id", "aap", g.identity().toString());
        g = it.next();
        Assert.assertEquals(title + " 2nd group size", 1, g.size());
        Assert.assertEquals(title + " 2nd group id", "be", g.identity().toString());
    }

    @Test
    public void testHitGroupsTitle() throws InvalidQuery {
        TextPattern tp = new TextPatternAnyToken(1, 1);
        BLSpanQuery query = tp.toQuery(QueryInfo.create(index));
        HitProperty groupBy = new HitPropertyDocumentStoredField(index, "title", "Title");
        SearchHits searchHits = index.search(contents, false).find(query);
        SearchHitGroups searchHitGroups = searchHits.groupWithStoredHits(groupBy, 1);
        SearchHitGroups sortedGroups = searchHitGroups.sort(HitGroupPropertyIdentity.get());
        HitGroups groups = sortedGroups.execute();
        Assert.assertEquals("# groups", 4, groups.size());
        Iterator<HitGroup> it = groups.iterator();
        HitGroup g = it.next();
        Assert.assertEquals("1st group size", 10, g.size());
        Assert.assertEquals("1st group id", "Bastardized Shakespeare", g.identity().toString());
        g = it.next();
        Assert.assertEquals("2nd group size", 12, g.size());
        Assert.assertEquals("2nd group id", "Learning words", g.identity().toString());
    }

}
