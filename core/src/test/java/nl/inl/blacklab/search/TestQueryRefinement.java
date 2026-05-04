package nl.inl.blacklab.search;

import java.util.Arrays;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.plugins.param.PluginParams;
import nl.inl.blacklab.queryParser.corpusql.BcqlQueryLanguageParser;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.HitPropertyAfterHit;
import nl.inl.blacklab.resultproperty.HitPropertyBeforeHit;
import nl.inl.blacklab.resultproperty.HitPropertyCaptureGroup;
import nl.inl.blacklab.resultproperty.HitPropertyContextBase;
import nl.inl.blacklab.resultproperty.HitPropertyContextPart;
import nl.inl.blacklab.resultproperty.HitPropertyHitText;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.PropertyValueContextWords;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.textpattern.CompleteQuery;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.testutil.TestIndex;

public class TestQueryRefinement {

    TestIndex testIndex = TestIndex.get();

    BlackLabIndex index = testIndex.index();

    Annotation wordAnnot = index.mainAnnotatedField().annotation("word");

    @Test
    public void testHitText() {
        HitProperty prop = new HitPropertyHitText(index, wordAnnot);
        assertRefinePattern("[lemma = 'is' & word = 'be']", "[lemma = 'is']", prop, List.of("be"));
    }

    @Test
    public void testBefore() {
        HitProperty prop = new HitPropertyBeforeHit(index, wordAnnot, MatchSensitivity.INSENSITIVE, 2);
        assertRefinePattern("(?<= [word='dog'] [word='lazy']) [lemma = 'is']", "[lemma = 'is']", prop, List.of("lazy", "dog"));
    }

    @Test
    public void testAfter() {
        HitProperty prop = new HitPropertyAfterHit(index, wordAnnot, MatchSensitivity.SENSITIVE, 1);
        assertRefinePattern("[lemma = 'is'] (?= [word='(?-i)be']) ", "[lemma = 'is']", prop, List.of("be"));
    }

    @Test
    public void testCapture() {
        HitProperty prop = new HitPropertyCaptureGroup(index, wordAnnot, MatchSensitivity.SENSITIVE, "A",
                RelationInfo.SpanMode.FULL_SPAN);
        assertRefinePattern("[lemma = 'is'] A:([lemma='lazy' & word='(?-i)dog'])", "[lemma = 'is'] A:[lemma='lazy']", prop, List.of("dog"));
    }

    @Test
    public void testContextPart() {
        HitProperty prop = new HitPropertyContextPart(index, wordAnnot, MatchSensitivity.SENSITIVE, "B2");
        assertRefinePattern("(?<= [word='(?-i)is'] [] ) [lemma='lazy']", "[lemma='lazy']", prop, List.of("is"));
    }

    private static TextPattern parse(String query) {
        return BcqlQueryLanguageParser.parseToTextPattern(PluginParams.NONE, query);
    }

    private void assertRefinePattern(String expected,
            String query, HitProperty hitProp, List<String> propValTerms) {
        if (!(hitProp instanceof HitPropertyContextBase prop))
            throw new IllegalArgumentException("hitProp must be a HitPropertyContextBase");
        Annotation annot = prop.getAnnotation();
        PropertyValue propVal = getContextWords(annot, propValTerms);
        TextPattern refined = hitProp.refine(index, new CompleteQuery(parse(query)), propVal).orElseThrow().pattern();
        Assert.assertEquals(parse(expected), refined);
    }

    private static @NonNull PropertyValue getContextWords(Annotation annotation, List<String> terms) {
        Terms annotTerms = annotation.field().index().forwardIndex(annotation).terms();
        int[] termIds = terms.stream()
                .mapToInt(term -> annotTerms.indexOf(term, MatchSensitivity.SENSITIVE))
                .toArray();
        int[] sortPositions = Arrays.stream(termIds)
                .map(termId -> annotTerms.idToSortPosition(termId, MatchSensitivity.SENSITIVE))
                .toArray();
        return new PropertyValueContextWords(annotation, MatchSensitivity.SENSITIVE,
                annotTerms, termIds, sortPositions, false, null);
    }
}
