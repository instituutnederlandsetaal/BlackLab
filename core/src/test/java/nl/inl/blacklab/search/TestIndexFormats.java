package nl.inl.blacklab.search;

import java.text.Collator;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.lucene.index.LeafReaderContext;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.set.mutable.primitive.IntHashSet;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.index.annotated.AnnotationSensitivities;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.FieldType;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.search.indexmetadata.MetadataFieldValues;
import nl.inl.blacklab.testutil.TestIndex;

/**
 * Test the basic functionality of the external and integrated index formats.
 * (terms, forward index)
 */
@RunWith(Parameterized.class)
public class TestIndexFormats {

    @Parameterized.Parameters(name = "index type {0}")
    public static Collection<TestIndex> typeToUse() {
        return TestIndex.typesForTests();
    }

    @Parameterized.Parameter
    public TestIndex testIndex;

    private static BlackLabIndex index;

    private static AnnotationForwardIndex wordFi;

    private static AnnotationForwardIndex posFi;

    private static Terms wordTerms;

    @Before
    public void setUp() {
        index = testIndex.index();
        AnnotatedField contents = index.mainAnnotatedField();
        Annotation word = contents.mainAnnotation();
        wordFi = index.forwardIndex(contents).get(word);
        posFi = index.forwardIndex(contents).get(contents.annotation("pos"));
        wordTerms = wordFi.terms();
    }

    @Test
    public void testSimple() {
        Assert.assertEquals("Number of terms", 28, wordTerms.numberOfTerms());
    }

    @Test
    public void testIndexOfAndGet() {
        MutableIntSet s = new IntHashSet();
        for (int i = 0; i < 28; i++) {
            String term = wordTerms.get(i);
            Assert.assertEquals("indexOf(get(" + i + "))", i, wordTerms.indexOf(term));
            s.clear();
            wordTerms.indexOf(s, term, MatchSensitivity.SENSITIVE);
            Assert.assertEquals("indexOf(get(" + i + "))", i, s.intIterator().next());
        }
    }

    /** Choose random terms and check that the comparison yields the expected value.
     */
    @Test
    public void testCompareTerms() {
        testCompareTerms(MatchSensitivity.SENSITIVE);
        testCompareTerms(MatchSensitivity.INSENSITIVE);
    }

    /**
     * Choose random terms and check that the comparison yields the expected value.
     * @param sensitive match sensitivity to use
     */
    private void testCompareTerms(MatchSensitivity sensitive) {
        Terms terms = wordFi.terms();
        Collator collator = wordFi.collators().get(sensitive);
        Random random = new Random(123_456);
        for (int i = 0; i < 100; i++) {
            int a = random.nextInt(28);
            int b = random.nextInt(28);
            String ta = terms.get(a);
            String tb = terms.get(b);
            int expected = collator.compare(ta, tb);
            int actual = Integer.compare(terms.idToSortPosition(a, sensitive),
                    terms.idToSortPosition(b, sensitive));
            Assert.assertEquals(
                    ta + "(" + a + ") <=> " + tb + "(" + b + ") (" + sensitive + ")",
                    expected,
                    actual
            );
        }
    }

    @Test
    public void testDocLength() {
        for (int i = 0; i < TestIndex.DOC_LENGTHS_TOKENS.length; i++) {
            int expectedLength = TestIndex.DOC_LENGTHS_TOKENS[i] + BlackLabIndexAbstract.IGNORE_EXTRA_CLOSING_TOKEN;
            int docId = testIndex.getDocIdForDocNumber(i);

            Assert.assertEquals(expectedLength, wordFi.docLength(docId));

            // pos annotation doesn't occur in all docs; test that this doesn't mess up doc length
            Assert.assertEquals(expectedLength, posFi.docLength(docId));
        }
    }

    int getToken(AnnotationForwardIndex afi, int docId, int pos) {
        LeafReaderContext lrc = testIndex.index().getLeafReaderContext(docId);
        int[] context = afi.retrievePartsIntSegment(lrc, docId, new int[] { pos }, new int[] { pos + 1 }).get(0);
        if (context.length == 0)
            throw new IllegalArgumentException("Token offset out of range");
        return context[0];
    }

    @Test
    public void testRetrieve() {
        System.err.flush();
        String[] expected = { "The", "quick", "brown", "fox", "jumps", "over", "the", "lazy", "dog" };
        int docId = testIndex.getDocIdForDocNumber(0);
        for (int i = 0; i < expected.length; i++) {
            Assert.assertEquals(expected[i], wordTerms.get(getToken(wordFi, docId, i)));
        }
    }

    /** if token offset out of range, throw an exception */
    @Test(expected = IllegalArgumentException.class)
    public void testRetrieveOutOfRange() {
        wordTerms.get(getToken(wordFi, 0, TestIndex.DOC_LENGTHS_TOKENS[0] +
                BlackLabIndexAbstract.IGNORE_EXTRA_CLOSING_TOKEN));
    }

    /** translating a -1 term from segment to global should also return -1 */
    @Test
    public void testNoTerm() {
        LeafReaderContext lrc = index.reader().leaves().iterator().next();
        Assert.assertEquals(-1, wordTerms.segmentIdsToGlobalIds(lrc.ord, new int[] {-1})[0]);
    }

    @Test
    public void testMetadataTextDirection() {
        Assert.assertEquals("ltr", index.metadata().custom().get("textDirection", ""));
    }

    @Test
    public void testMetadataCounts() {
        int expectedTokenCount = Arrays.stream(TestIndex.DOC_LENGTHS_TOKENS).sum();
        Assert.assertEquals(expectedTokenCount, index.metadata().tokenCount());
        Assert.assertEquals(TestIndex.DOC_LENGTHS_TOKENS.length, index.metadata().documentCount());
    }

    @Test
    public void testMetadataMetadataField() {
        MetadataField field = index.metadata().metadataFields().get("pid");
        Assert.assertEquals(FieldType.TOKENIZED, field.type());
        MetadataFieldValues values = field.values(100);
        Assert.assertEquals(false, values.valueList().isTruncated());
        Map<String, Long> map = values.valueList().getValues();
        int expectedNumberOfDocuments = TestIndex.DOC_LENGTHS_TOKENS.length;
        Assert.assertEquals(expectedNumberOfDocuments, map.size());
        for (int i = 0; i < expectedNumberOfDocuments; i++)
            Assert.assertEquals(1, (long)map.get(Long.toString(i)));
        Assert.assertEquals(TestIndex.DOC_LENGTHS_TOKENS.length, index.metadata().documentCount());
    }

    @Test
    public void testMetadataAnnotatedField() {
        AnnotatedField field = index.metadata().annotatedFields().get("contents");
        Assert.assertTrue(field.hasRelationAnnotation());
        Assert.assertTrue(field.hasContentStore());
        Set<String> expectedAnnotations =
                new HashSet<>(Arrays.asList("word", "lemma", "pos",
                        AnnotatedFieldNameUtil.RELATIONS_ANNOT_NAME, AnnotatedFieldNameUtil.PUNCTUATION_ANNOT_NAME));
        Set<String> actualAnnotations = field.annotations().stream().map(Annotation::name).collect(Collectors.toSet());
        Assert.assertEquals(expectedAnnotations, actualAnnotations);
        Assert.assertEquals("word", field.mainAnnotation().name());
        Assert.assertEquals(AnnotationSensitivities.SENSITIVE_AND_INSENSITIVE, field.mainAnnotation().sensitivitySetting());
        Assert.assertEquals(MatchSensitivity.SENSITIVE, field.mainAnnotation().offsetsSensitivity().sensitivity());
    }

    @Test
    public void testContentStoreRetrieve() {
        AnnotatedField fieldsContents = index.mainAnnotatedField();
        ContentAccessor ca = index.contentAccessor(fieldsContents);
        int[] start = { 0, 0, 10, 20 };
        int[] end = { 10, -1, 20, -1 };
        for (int i = 0; i < TestIndex.TEST_DATA.length; i++) {
            int docId = testIndex.getDocIdForDocNumber(i);
            String[] substrings = ca.getSubstringsFromDocument(docId, start, end);
            Assert.assertEquals(4, substrings.length);
            for (int j = 0; j < substrings.length; j++) {
                int a = start[j], b = end[j];
                String docContents = TestIndex.TEST_DATA[i];
                if (b < 0)
                    b = docContents.length();
                String expected = docContents.substring(a, b);
                Assert.assertEquals(expected, substrings[j]);
            }
        }
    }

}
