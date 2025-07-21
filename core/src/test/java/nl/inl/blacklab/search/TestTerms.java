package nl.inl.blacklab.search;

import java.util.Collection;
import java.util.List;

import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.set.mutable.primitive.IntHashSet;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.testutil.TestIndex;
import nl.inl.util.StringUtil;

@RunWith(Parameterized.class)
public class TestTerms {

    static TestIndex testIndexIntegrated;

    @Parameterized.Parameters(name = "index type {0}")
    public static Collection<BlackLabIndex.IndexType> typeToUse() {
        return List.of(BlackLabIndex.IndexType.INTEGRATED);
    }

    @Parameterized.Parameter
    public BlackLabIndex.IndexType indexType;

    private TestIndex testIndex;

    private Terms terms;

    @BeforeClass
    public static void setUpClass() {
        testIndexIntegrated = TestIndex.getWithTestDelete(BlackLabIndex.IndexType.INTEGRATED);
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

            String termDesensitized = StringUtil.desensitize(term);
            MutableIntSet results = new IntHashSet();
            terms.indexOf(results, term, MatchSensitivity.INSENSITIVE);
            results.forEach(termId -> {
                String foundTerm = StringUtil.desensitize(terms.get(termId));
                Assert.assertEquals(termDesensitized, foundTerm);
            });
        }
    }
}
