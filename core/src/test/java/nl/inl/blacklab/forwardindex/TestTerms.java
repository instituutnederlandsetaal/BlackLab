package nl.inl.blacklab.forwardindex;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.testutil.TestIndex;

public class TestTerms {

    static TestIndex testIndexIntegrated;

    private TestIndex testIndex;

    private Terms terms;

    @BeforeClass
    public static void setUpClass() {
        testIndexIntegrated = TestIndex.getWithTestDelete();
    }

    @AfterClass
    public static void tearDownClass() {
        testIndexIntegrated.close();
    }

    @Before
    public void setUp() {
        testIndex = testIndexIntegrated;
        BlackLabIndex index = testIndex.index();
        Annotation ann = index.mainAnnotatedField().mainAnnotation();
        terms = testIndex.getTermsSegment(ann);
    }

    @Test
    public void testTerms() {
        for (int i = 0; i < terms.numberOfTerms(); i++) {
            String term = terms.get(i);
            int sortPos = terms.idToSortPosition(i, MatchSensitivity.SENSITIVE);
            int sortPos2 = terms.termToSortPosition(term, MatchSensitivity.SENSITIVE);
            Assert.assertEquals("Sensitive sort positions should be identical", sortPos, sortPos2);

            sortPos = terms.idToSortPosition(i, MatchSensitivity.INSENSITIVE);
            sortPos2 = terms.termToSortPosition(term, MatchSensitivity.INSENSITIVE);
            Assert.assertEquals("Insensitive sort positions should be identical", sortPos, sortPos2);
        }
    }
}
