package nl.inl.blacklab.forwardindex;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.stream.Stream;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import nl.inl.blacklab.codec.BLTerms;
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
        if (testIndexIntegrated != null)
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

    @Test
    public void repeatedLastGlobalTermDoesNotShiftLaterTermOrigins() throws Exception {
        try (TestIndex fixture = TestIndex.get("testformat",
                "<doc><w>apple</w><w>eat</w></doc>",
                "<doc><w>apple</w><w>eat</w><w>peel</w></doc>")) {
            Annotation annotation = fixture.index().mainAnnotatedField().mainAnnotation();
            String field = annotation.forwardIndexSensitivity().luceneField();
            var leaves = new ArrayList<>(fixture.index().reader().leaves()) {
                // Force first segment before second: 'eat' is the existing last global id when repeated.
                @Override public Stream<LeafReaderContext> parallelStream() { return stream(); }
            };
            Assert.assertEquals(2, leaves.size());
            IndexReader reader = mock(IndexReader.class);
            when(reader.leaves()).thenReturn(leaves);
            TermsGlobal global = new TermsGlobal(field);
            global.initialize(reader);
            assertSegmentMappings(global, leaves, field);

            // Also exercise the production parallel-stream path.
            TermsGlobal parallelGlobal = new TermsGlobal(field);
            parallelGlobal.initialize(fixture.index().reader());
            assertSegmentMappings(parallelGlobal, leaves, field);
        }
    }

    private static void assertSegmentMappings(TermsGlobal global, Iterable<LeafReaderContext> leaves, String field) {
        for (LeafReaderContext leaf: leaves) {
            Terms local = BLTerms.forSegment(leaf, field).reader();
            for (int id = 0; id < local.numberOfTerms(); id++) {
                int globalId = global.toGlobalTermId(leaf, id);
                Assert.assertEquals(local.get(id), global.get(globalId));
            }
        }
    }
}
