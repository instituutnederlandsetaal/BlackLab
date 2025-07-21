package nl.inl.blacklab.search;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import nl.inl.blacklab.testutil.TestIndex;

@RunWith(Parameterized.class)
public class TestDeleteDocument {

    static TestIndex testIndexIntegrated;

    @Parameterized.Parameters(name = "index type {0}")
    public static Collection<BlackLabIndex.IndexType> typeToUse() {
        return List.of(BlackLabIndex.IndexType.INTEGRATED);
    }

    @Parameterized.Parameter
    public BlackLabIndex.IndexType indexType;

    TestIndex testIndex;

    /**
     * Expected search results;
     */
    List<String> expected;

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
    }

    @Test
    public void testSimple() {
        expected = Arrays.asList(
                "May [the] Force",
                "is [the] question");
        Assert.assertEquals(expected, testIndex.findConc(" 'the' "));

        expected = Arrays.asList(
                "May [the] Force",
                "is [the] question");
        Assert.assertEquals(expected, testIndex.findConc(" '(?-i)the' "));

        expected = Arrays.asList(
                "the [Force] be",
                "the [question]");
        Assert.assertEquals(expected, testIndex.findConc(" [pos='nou'] "));
    }
}
