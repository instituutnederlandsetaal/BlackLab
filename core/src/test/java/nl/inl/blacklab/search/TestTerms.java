package nl.inl.blacklab.search;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.indexmetadata.Annotation;
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
        terms = index.annotationForwardIndex(ann).terms();
    }

    @Test
    public void testTerms() {
        for (int i = 0; i < terms.numberOfTerms(); i++) {
            String term = terms.get(i);
            int index = terms.indexOf(term);
            Assert.assertEquals(i, index);
        }
    }
}
