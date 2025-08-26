package nl.inl.blacklab.search.grouping;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import nl.inl.blacklab.Constants;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.mocks.MockTerms;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.HitPropertyCaptureGroup;
import nl.inl.blacklab.resultproperty.HitPropertyContextPart;
import nl.inl.blacklab.resultproperty.HitPropertyHitText;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.PropertyValueContext;
import nl.inl.blacklab.resultproperty.PropertyValueContextWords;
import nl.inl.blacklab.resultproperty.PropertyValueMultiple;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.results.Results;
import nl.inl.blacklab.search.results.hitresults.HitGroup;
import nl.inl.blacklab.search.results.hitresults.HitGroups;
import nl.inl.blacklab.search.results.hitresults.HitResults;
import nl.inl.blacklab.testutil.TestIndex;

@RunWith(Parameterized.class)
public class TestHitProperties {

    @Parameterized.Parameters(name = "index type {0}")
    public static Collection<TestIndex> typeToUse() {
        return TestIndex.typesForTests();
    }

    @Parameterized.Parameter
    public TestIndex testIndex;

    private BlackLabIndex index;

    private Terms terms;

    private Annotation wordAnnotation;

    @Before
    public void setUp() {
        index = testIndex.index();
        wordAnnotation = index.mainAnnotatedField().annotation("word");
        terms = index.forwardIndex(wordAnnotation).terms(); //testIndex.getTermsSegment(wordAnnotation);
    }

    private int termId(String word) {
        return terms.indexOf(word, MatchSensitivity.SENSITIVE);
    }

    @Test
    public void testHitPropHitText() {
        HitResults hitResults = testIndex.find(" 'the' ");
        HitProperty p = new HitPropertyHitText(index, MatchSensitivity.SENSITIVE);
        HitGroups g = hitResults.group(p, Results.NO_LIMIT);
        HitGroup group = g.get(new PropertyValueContextWords(wordAnnotation, MatchSensitivity.SENSITIVE, terms,
                new int[] { termId("the") }, null, false, null));
        Assert.assertEquals(3, group.size());
        group = g.get(new PropertyValueContextWords(wordAnnotation, MatchSensitivity.SENSITIVE, terms,
                new int[] { termId("The") }, null, false, null));
        Assert.assertEquals(1, group.size());
    }

    @Test
    public void testHitPropCaptureGroup() {
        HitResults hitResults = testIndex.find(" A:'the' ");
        HitProperty p = new HitPropertyCaptureGroup(index, index.mainAnnotatedField().mainAnnotation(), MatchSensitivity.SENSITIVE, "A", RelationInfo.SpanMode.FULL_SPAN);
        HitGroups g = hitResults.group(p, Results.NO_LIMIT);
        HitGroup group = g.get(new PropertyValueContextWords(wordAnnotation, MatchSensitivity.SENSITIVE, terms,
                new int[] { termId("the") }, null, false, null));
        Assert.assertEquals(3, group.size());
        group = g.get(new PropertyValueContextWords(wordAnnotation, MatchSensitivity.SENSITIVE, terms,
                new int[] { termId("The") }, null, false, null));
        Assert.assertEquals(1, group.size());
    }

    public PropertyValue multipleContextWordsValues(int[]... termsArray) {
        List<PropertyValue> l = new ArrayList<>();
        for (int[] termIds: termsArray) {
            l.add(new PropertyValueContextWords(wordAnnotation, MatchSensitivity.SENSITIVE, terms, termIds, null, false, null));
        }
        return new PropertyValueMultiple(l);
    }

    @Test
    public void testHitPropContextWords() {
        HitResults hitResults = testIndex.find(" 'the' ");
        HitProperty p = HitPropertyContextPart.contextWords(index, wordAnnotation, MatchSensitivity.SENSITIVE, "L1-1;H1-2");
        HitGroups g = hitResults.group(p, Results.NO_LIMIT);
        Assert.assertEquals(4, g.size());
        HitGroup group;
        group = g.get(multipleContextWordsValues(new int[0], new int[] { termId("The") }));
        Assert.assertEquals(1, group.size());
        group = g.get(multipleContextWordsValues(new int[] { termId("over") }, new int[] { termId("the") }));
        Assert.assertEquals(1, group.size());
        group = g.get(multipleContextWordsValues(new int[] { termId("May") }, new int[] { termId("the") }));
        Assert.assertEquals(1, group.size());
        group = g.get(multipleContextWordsValues(new int[] { termId("is") }, new int[] { termId("the") }));
        Assert.assertEquals(1, group.size());
    }

    @Test
    public void testHitPropContextWordsReverse() {
        HitResults hitResults = testIndex.find(" 'the' 'lazy' ");
        HitProperty p = HitPropertyContextPart.contextWords(index, wordAnnotation, MatchSensitivity.SENSITIVE, "L1;H2-1;R1");
        HitGroups g = hitResults.group(p, Results.NO_LIMIT);
        Assert.assertEquals(1, g.size());
        HitGroup group;
        group = g.get(
                multipleContextWordsValues(new int[] { termId("over") }, new int[] { termId("lazy"), termId("the") }, new int[] { termId("dog") }));
        Assert.assertEquals(1, group.size());
    }

    @Test
    public void testTermSerialization() {
        String[] words = { "aap", "~", "~~", "" };
        String[] expected = { "aap", "~~", "~~~", "" };
        MockTerms mockTerms = new MockTerms(words);
        for (int i = 0; i < mockTerms.numberOfTerms(); i++) {
            Assert.assertEquals(expected[i], PropertyValueContext.serializeTerm(mockTerms, i));
        }
        Assert.assertEquals("~", PropertyValueContext.serializeTerm(mockTerms, Constants.NO_TERM));
    }

}
