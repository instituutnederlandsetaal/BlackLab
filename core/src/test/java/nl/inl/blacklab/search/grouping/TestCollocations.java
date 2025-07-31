package nl.inl.blacklab.search.grouping;

import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.TermFrequency;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.hitresults.ContextSize;
import nl.inl.blacklab.search.results.hitresults.Contexts;
import nl.inl.blacklab.search.results.hitresults.HitResults;
import nl.inl.blacklab.testutil.TestIndex;

public class TestCollocations {

    TestIndex testIndex = TestIndex.get();

    BlackLabIndex index = testIndex.index();

    @Test
    public void testThe() {
        HitResults hitResults = testIndex.find("'the'");
        Annotation annotation = index.mainAnnotatedField().mainAnnotation();
        TermFrequencyList result = Contexts.collocations(hitResults, annotation, ContextSize.get(2, 100),
                MatchSensitivity.SENSITIVE, true);
         for (TermFrequency termFrequency: result) {
             Assert.assertEquals(1, termFrequency.frequency);
             Assert.assertNotEquals("the", termFrequency.term); // only words around the hit
         }
    }

    @Test
    public void testFox() {
        HitResults hitResults = testIndex.find("'fox'");
        Annotation annotation = index.mainAnnotatedField().mainAnnotation();
        TermFrequencyList result = Contexts.collocations(hitResults, annotation, ContextSize.get(3, 100),
                MatchSensitivity.INSENSITIVE, true);
        for (TermFrequency termFrequency: result) {
            if (termFrequency.term.equals("the"))
                Assert.assertEquals(2, termFrequency.frequency);
            else
                Assert.assertEquals(1, termFrequency.frequency);
            Assert.assertNotEquals("fox", termFrequency.term); // only words around the hit
        }
    }
}
