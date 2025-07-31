package nl.inl.blacklab.resultproperty;

import java.text.Collator;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.forwardindex.Collators;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.mocks.MockBlackLabIndex;
import nl.inl.blacklab.mocks.MockTerms;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldImpl;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.AnnotationImpl;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

public class TestPropertyValues {

    private static final Collator regularCollator = BlackLab.defaultCollator();

    /** A different collator is used for terms so that if a and b are equal after desensitizing, collator.compare(a, b) == 0 */
    private static final Collator termsCollator = (new Collators(regularCollator)).get(
            MatchSensitivity.INSENSITIVE);

    private static final Terms terms;

    static {
        // Test using MockTerms with a sorted list of very similar words that should not be equal
        List<String> strTerms = Arrays.asList("a", "aa", "ap", "p", "aap", "a ap", "aa p", " aap", "aap ", "a a p", "-aap", "a-ap", "aap-");
        strTerms.sort(termsCollator); // MockTerms requires that terms are already sorted
        terms = new MockTerms(strTerms.toArray(new String[0]));
    }

    @Test
    public void testPropertyValueContextWords() {
        MatchSensitivity sensitivity = MatchSensitivity.INSENSITIVE;
        MockBlackLabIndex index = new MockBlackLabIndex();
        AnnotatedField field = new AnnotatedFieldImpl(index, "contents");
        Annotation annotation = new AnnotationImpl(field, "lemma");
        PropertyValueContextWords twoThreeThree = new PropertyValueContextWords(annotation, sensitivity,
                terms, new int[]{2, 3, 3}, null, false);
        PropertyValueContextWords twoThreeThree2 = new PropertyValueContextWords(annotation, sensitivity,
                terms, new int[]{2, 3, 3}, null, false);
        PropertyValueContextWords oneThreeFour = new PropertyValueContextWords(annotation, sensitivity,
                terms, new int[]{1, 3, 4}, null, false);
        PropertyValueContextWords twoThreeFive = new PropertyValueContextWords(annotation, sensitivity,
                terms, new int[]{2, 3, 5}, null, false);
        Assert.assertEquals(0, twoThreeThree.compareTo(twoThreeThree2));
        Assert.assertEquals(-1, oneThreeFour.compareTo(twoThreeThree));
        Assert.assertEquals(1, twoThreeFive.compareTo(twoThreeThree));
    }

    private static int term(String word) {
        return terms.indexOf(word);
    }

    @Test
    public void termCollatorVsRegularCollator() {
        //Assert.assertEquals(1, collator.compare("aap", "-aap"));

        // Regular collator ignores all dashes and spaces (unless all characters are equal)...
        Assert.assertEquals( 1, regularCollator.compare(" aap | ap",      "-aap | a ap"));     // a > b
        Assert.assertEquals( 1, regularCollator.compare( "aapap",          "aapaap"));         // a > b
        Assert.assertEquals( 1, regularCollator.compare(       "ap",             "a ap"));     // a > b

        Assert.assertEquals(-1, regularCollator.compare(" aap | a",       "-aap | a"));        // a < b
        Assert.assertEquals(-1, regularCollator.compare(" aap",           "-aap"));            // a < b

        // ...but Terms collator does not!
        Assert.assertEquals(-1, termsCollator.compare(" aap | ap | ap", "-aap | a ap | p"));
        Assert.assertEquals(-1, termsCollator.compare(" aap | ap | ap", "-aap | ap | ap"));
        Assert.assertEquals(-1, termsCollator.compare(" aap", "-aap"));

    }
}
