package nl.inl.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.jspecify.annotations.NonNull;
import org.junit.Test;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.testutil.TestIndex;

public class TestLuceneUtil {

    TestIndex testIndex = TestIndex.get();

    BlackLabIndex index = testIndex.index();

    AnnotatedField mainAnnotatedField = index.mainAnnotatedField();

    int uniqueTermsInIndex = index.forwardIndex(mainAnnotatedField).get(mainAnnotatedField.mainAnnotation()).terms().numberOfTerms();

    String mainLuceneField = index.mainAnnotatedField().mainAnnotation().mainSensitivity().luceneField();

    final String FIELD_TITLE = "title";

    @Test
    public void testGetTermFrequency() {
        assertTermFreq(3, "the", null);
        assertTermFreq(4, "mier", null);
        assertTermFreq(1, "The", new TermQuery(new Term(FIELD_TITLE, "pangram")));
    }

    private void assertTermFreq(int expected, String the, Query docFilter) {
        // fast and slow versions should return the same result if there are no deleted docs
        long freq = LuceneUtil.getTermFrequency(index, mainLuceneField, the, docFilter, false);
        assertEquals(expected, freq);
        long freq2 = LuceneUtil.getTermFrequency(index, mainLuceneField, the, docFilter, true);
        assertEquals(expected, freq2);
    }

    @Test
    public void testParseLuceneQuery() throws ParseException {
        assertParseLuceneQueryTerms("aap");
        assertParseLuceneQueryTerms("aap", "noot");
        assertParseLuceneQueryTerms("aap", "noot", "mies");
        assertParseLuceneQueryPhrase("aap noot mies");
        assertParseLuceneQueryPhrase(" noot  mies ");
    }

    private void assertParseLuceneQueryPhrase(String phrase) throws ParseException {
        String queryStr = FIELD_TITLE + ":\"" + phrase + "\"";
        Query query = LuceneUtil.parseLuceneQuery(index, queryStr, index.analyzer(), mainLuceneField);
        Query expected = new PhraseQuery(FIELD_TITLE, phrase.trim().split("\\s+"));
        assertEquals(expected, query);
    }

    private void assertParseLuceneQueryTerms(String... terms) throws ParseException {
        String queryStr = FIELD_TITLE + ":(" + String.join(" ", terms) + ")";
        Query query = LuceneUtil.parseLuceneQuery(index, queryStr, index.analyzer(), mainLuceneField);
        if (terms.length > 1) {
            BooleanQuery.Builder expected = new BooleanQuery.Builder();
            for (String term: terms) {
                expected.add(new TermQuery(new Term(FIELD_TITLE, term)), BooleanClause.Occur.SHOULD);
            }
            assertEquals(expected.build(), query);
        } else {
            assertEquals(new TermQuery(new Term(FIELD_TITLE, terms[0])), query);
        }
    }

    @Test
    public void testFindTermsByPrefix() {
        List<String> terms = findTerms("t");
        assertEquals(2, terms.size());
        assertEquals("the", terms.get(0));
        assertEquals("to", terms.get(1));
        terms = findTerms("");
        assertEquals(11, terms.size());
    }

    private @NonNull List<String> findTerms(String prefix) {
        return LuceneUtil.findTermsByPrefix(index.reader(), mainLuceneField,
                prefix, MatchSensitivity.INSENSITIVE, 10);
    }

    @Test
    public void testGetFieldTerms() {
        Map<String, Long> termFreqs;

        termFreqs = getFieldTerms("", -1); // this also tests that termFrequencies returns the same frequencies
        assertEquals(uniqueTermsInIndex, termFreqs.size());

        termFreqs = getFieldTerms("dog", 10);
        assertEquals(2L, (long)termFreqs.get("find"));
        assertEquals(4L, (long)termFreqs.get("mier"));
        assertNull(termFreqs.get("brown"));
        termFreqs = getFieldTerms("the", 10);
        assertEquals(3L, (long)termFreqs.get("the"));
        assertEquals(1L, (long)termFreqs.get("to"));
        assertNull(termFreqs.get("force"));
    }

    private @NonNull Map<String, Long> getFieldTerms(String startFrom, int limit) {
        Map<String, Long> termFreqs = new TreeMap<>();
        LuceneUtil.getFieldTerms(index.reader(), mainLuceneField, startFrom, (term, freq) -> {
            termFreqs.compute(term, (k, v) -> v == null ? freq : v + freq);
            return limit < 0 || termFreqs.size() < limit;
        });
        if (limit >= 0)
            assertTrue(termFreqs.size() <= limit);

        if (StringUtils.isEmpty(startFrom)) {
            Map<String, Integer> termFreqs2 = LuceneUtil.termFrequencies(index.searcher(), null, mainLuceneField, null);
            termFreqs2.forEach((term, freq) -> {
                assertEquals((long)freq, (long)termFreqs.get(term));
            });
        }

        return termFreqs;
    }

    @Test
    public void testTermFrequencies() {
        Query filter = null;
        Set<String> terms = Set.of("the", "mier", "brown");
        Map<String, Integer> termFreqs = LuceneUtil.termFrequencies(index.searcher(), filter, mainLuceneField, terms);
        assertEquals(3, termFreqs.size());
        assertEquals(3L, (long)termFreqs.get("the"));
        assertEquals(4L, (long)termFreqs.get("mier"));
        assertEquals(1L, (long)termFreqs.get("brown"));
        assertNull(termFreqs.get("dog"));

        filter = new TermQuery(new Term(FIELD_TITLE, "pangram"));
        termFreqs = LuceneUtil.termFrequencies(index.searcher(), filter, mainLuceneField, terms);
        assertEquals(3, termFreqs.size());
        assertEquals(1L, (long)termFreqs.get("the"));
        assertEquals(0L, (long)termFreqs.get("mier"));
        assertEquals(1L, (long)termFreqs.get("brown"));
        assertNull(termFreqs.get("dog"));
    }

    @Test
    public void testGetSumTotalTermFreq() {
        long sumTotalTermFreq = LuceneUtil.getSumTotalTermFreq(index.reader(), mainLuceneField);
        assertEquals(43L, sumTotalTermFreq);
    }

    @Test
    public void testGetMaxTermsPerLeafReader() {
        long max = LuceneUtil.getMaxTermsPerLeafReader(index.reader(), mainLuceneField);
        assertTrue(max <= uniqueTermsInIndex);
    }

    @Test
    public void testSimpleDocIdCollector() throws IOException {
        Set<Integer> docIds = new HashSet<>();
        index.searcher().search(new MatchAllDocsQuery(), new LuceneUtil.SimpleDocIdCollector(docIds));
        assertEquals(index.reader().numDocs(), docIds.size());
        Set<Integer> expectedDocIds = new HashSet<>();
        for (int i = 0; i < docIds.size() + 1; i++) {
            expectedDocIds.add(i);
        }
        for (Integer docId: docIds) {
            assertTrue("Unexpected docId: " + docId, expectedDocIds.remove(docId));
        }
        assertEquals(1, expectedDocIds.size()); // one doc id should be missing (the index metadata document)
    }



}
