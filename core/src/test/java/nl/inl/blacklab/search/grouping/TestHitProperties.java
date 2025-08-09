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
import nl.inl.blacklab.search.results.HitGroup;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.Results;
import nl.inl.blacklab.testutil.TestIndex;

@RunWith(Parameterized.class)
public class TestHitProperties {

    private static final int NO_TERM = Constants.NO_TERM;

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
        terms = testIndex.getTermsSegment(wordAnnotation);
    }

    @Test
    public void testHitPropHitText() {
        Hits hits = testIndex.find(" 'the' ");
        HitProperty p = new HitPropertyHitText(index, MatchSensitivity.SENSITIVE);
        HitGroups g = hits.group(p, Results.NO_LIMIT);
        HitGroup group = g.get(new PropertyValueContextWords(wordAnnotation, MatchSensitivity.SENSITIVE,
                new String[] { "the" }, false));
        Assert.assertEquals(3, group.size());
        group = g.get(new PropertyValueContextWords(wordAnnotation, MatchSensitivity.SENSITIVE,
                new String[] { "The" }, false));
        Assert.assertEquals(1, group.size());
    }

    @Test
    public void testHitPropCaptureGroup() {
        Hits hits = testIndex.find(" A:'the' ");
        HitProperty p = new HitPropertyCaptureGroup(index, index.mainAnnotatedField().mainAnnotation(), MatchSensitivity.SENSITIVE, "A", RelationInfo.SpanMode.FULL_SPAN);
        HitGroups g = hits.group(p, Results.NO_LIMIT);
        HitGroup group = g.get(new PropertyValueContextWords(wordAnnotation, MatchSensitivity.SENSITIVE,
                new String[] { "the" }, false));
        Assert.assertEquals(3, group.size());
        group = g.get(new PropertyValueContextWords(wordAnnotation, MatchSensitivity.SENSITIVE,
                new String[] { "The" }, false));
        Assert.assertEquals(1, group.size());
    }

    public PropertyValue multipleContextWordsValues(int[]... termsArray) {
        List<PropertyValue> l = new ArrayList<>();
        for (int[] termIds: termsArray) {
            l.add(new PropertyValueContextWords(wordAnnotation, MatchSensitivity.SENSITIVE, terms, termIds, null, false));
        }
        return new PropertyValueMultiple(l);
    }

    public PropertyValue multipleContextWordsValues(String[]... termsArray) {
        List<PropertyValue> l = new ArrayList<>();
        for (String[] terms: termsArray) {
            l.add(new PropertyValueContextWords(wordAnnotation, MatchSensitivity.SENSITIVE, terms, false));
        }
        return new PropertyValueMultiple(l);
    }

    @Test
    public void testHitPropContextWords() {
        Hits hits = testIndex.find(" 'the' ");
        HitProperty p = HitPropertyContextPart.contextWords(index, wordAnnotation, MatchSensitivity.SENSITIVE, "L1-1;H1-2");
        HitGroups g = hits.group(p, Results.NO_LIMIT);
        Assert.assertEquals(4, g.size());
        HitGroup group;
        group = g.get(multipleContextWordsValues(new String[0], new String[] { "The" }));
        Assert.assertEquals(1, group.size());
        group = g.get(multipleContextWordsValues(new String[] { "over" }, new String[] { "the" }));
        Assert.assertEquals(1, group.size());
        group = g.get(multipleContextWordsValues(new String[] { "May" }, new String[] { "the" }));
        Assert.assertEquals(1, group.size());
        group = g.get(multipleContextWordsValues(new String[] { "is" }, new String[] { "the" }));
        Assert.assertEquals(1, group.size());
    }

    @Test
    public void testHitPropContextWordsReverse() {
        Hits hits = testIndex.find(" 'the' 'lazy' ");
        HitProperty p = HitPropertyContextPart.contextWords(index, wordAnnotation, MatchSensitivity.SENSITIVE, "L1;H2-1;R1");
        HitGroups g = hits.group(p, Results.NO_LIMIT);
        Assert.assertEquals(1, g.size());
        HitGroup group;
        group = g.get(
                multipleContextWordsValues(new String[] { "over" }, new String[] { "lazy", "the" }, new String[] { "dog" }));
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
