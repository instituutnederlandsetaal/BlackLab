package nl.inl.blacklab.perdocument;

import java.util.Iterator;

import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.resultproperty.DocGroupPropertyIdentity;
import nl.inl.blacklab.resultproperty.DocPropertyStoredField;
import nl.inl.blacklab.search.results.docs.DocGroup;
import nl.inl.blacklab.search.results.docs.DocGroups;
import nl.inl.blacklab.search.results.docs.DocResults;
import nl.inl.blacklab.search.results.hitresults.HitResults;
import nl.inl.blacklab.testutil.TestIndex;

public class TestDocsGrouped {

    TestIndex testIndex = TestIndex.get();

    @Test
    public void testDocResultsGroup() {
        HitResults hits = testIndex.find("'the'");
        DocResults docs = hits.perDocResults(Long.MAX_VALUE);
        DocGroups docGroups = docs.group(
                new DocPropertyStoredField(testIndex.index(), "title"), Long.MAX_VALUE);
        docGroups = docGroups.sort(new DocGroupPropertyIdentity());
        int[] expTokensPerGroup = new int[] {10, 9, 6};
        Assert.assertEquals(expTokensPerGroup.length, docGroups.size());
        Iterator<DocGroup> it = docGroups.iterator();
        for (int i = 0; i < expTokensPerGroup.length; i++) {
            DocGroup group = it.next();
            Assert.assertEquals(1, group.size());
            Assert.assertEquals(expTokensPerGroup[i], group.totalTokens());
        }
    }
}
