package nl.inl.blacklab.search;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.exceptions.InvalidInputFormatConfig;
import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.hitresults.HitResults;
import nl.inl.blacklab.testutil.TestIndex;

public class TestParallel {

    private static TestIndex testIndex;

    @BeforeClass
    public static void setUp() throws IOException, InvalidInputFormatConfig {
        String formatName = "minimal-parallel";
        try (InputStream is = TestParallel.class.getClassLoader().getResourceAsStream(formatName + ".blf.yaml")) {
            if (is == null)
                throw new IllegalStateException("Resource not found: " + formatName + ".blf.yaml");
            try (Reader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                ConfigInputFormat format = ConfigInputFormat.read(reader, false, formatName, null);
                DocumentFormats.add(format);
            }
        }

        String docXml;
        try (InputStream is = TestParallel.class.getClassLoader().getResourceAsStream(formatName + ".xml")) {
            if (is == null)
                throw new IllegalStateException("Resource not found: " + formatName + ".xml");
            docXml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        testIndex = TestIndex.get(formatName, docXml);
    }

    @AfterClass
    public static void tearDown() {
        if (testIndex != null) {
            testIndex.close();
            testIndex = null;
        }
    }

    private HitResults find(String pattern) throws InvalidQuery {
        BLQueryParser parser = testIndex.index().getQueryParser("bcql");
        BLSpanQuery query = parser.parse(pattern).pattern()
                .toQuery(QueryInfo.create(testIndex.index(), testIndex.index().mainAnnotatedField()));
        return testIndex.index().find(query, null);
    }

    @Test
    public void testParallelQueryWithoutRfield() throws InvalidQuery {
        HitResults hits = find("[word='Dit'] =w=>en _");
        Assert.assertEquals("contents__nl", hits.field().name());
        Assert.assertEquals(1, hits.size());
        Annotation wordNl = testIndex.index().annotatedField("contents__nl").annotation("word");
        List<String> conc = testIndex.getConcordances(hits.getHits(), wordNl);
        Assert.assertEquals(List.of("[Dit] is een"), conc);
    }

    @Test
    public void testParallelQueryWithRfield() throws InvalidQuery {
        HitResults hits = find("rfield([word='Dit'] =w=>en _, 'en')");
        Assert.assertEquals("contents__en", hits.field().name());
        Assert.assertEquals(1, hits.size());
        Annotation wordEn = testIndex.index().annotatedField("contents__en").annotation("word");
        List<String> conc = testIndex.getConcordances(hits.getHits(), wordEn);
        Assert.assertEquals(List.of("[This] is an"), conc);
    }

    @Test
    public void testMultiTargetParallelQueryWithRfield() throws InvalidQuery {
        HitResults hitsEn = find("rfield(\"Dit\" =w=>en _ ; =w=>de _, 'en')");
        Assert.assertEquals("contents__en", hitsEn.field().name());
        Assert.assertEquals(1, hitsEn.size());
        Annotation wordEn = testIndex.index().annotatedField("contents__en").annotation("word");
        Assert.assertEquals(List.of("[This] is an"), testIndex.getConcordances(hitsEn.getHits(), wordEn));

        HitResults hitsDe = find("rfield(\"Dit\" =w=>en _ ; =w=>de _, 'de')");
        Assert.assertEquals("contents__de", hitsDe.field().name());
        Assert.assertEquals(1, hitsDe.size());
        Annotation wordDe = testIndex.index().annotatedField("contents__de").annotation("word");
        Assert.assertEquals(List.of("[Dies] ist ein"), testIndex.getConcordances(hitsDe.getHits(), wordDe));
    }
}
