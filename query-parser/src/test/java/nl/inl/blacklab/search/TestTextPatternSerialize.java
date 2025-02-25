package nl.inl.blacklab.search;

import java.io.IOException;
import java.io.StringWriter;

import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.TextPatternRepetition;
import nl.inl.blacklab.search.textpattern.TextPatternSequence;
import nl.inl.blacklab.search.textpattern.TextPatternTerm;
import nl.inl.util.Json;

public class TestTextPatternSerialize {

    private static void assertRewritesTo(String expected, TextPattern pattern) throws IOException {
        StringWriter writer = new StringWriter();
        Json.getJaxbWriter(false).writeValue(writer, pattern);
        Assert.assertEquals(expected, writer.toString());
    }

    private static void assertRoundtrip(TextPattern pattern) throws IOException {
        // Serialize value
        StringWriter writer = new StringWriter();
        Json.getJaxbWriter().writeValue(writer, pattern);

        // Deserialize value
        TextPattern deserialized = Json.getJaxbReader().readValue(writer.toString(), TextPattern.class);

        // Check that they're the same
        Assert.assertEquals(pattern, deserialized);
    }

    private static void assertRoundtrip(String cqlQuery) throws IOException {
        CorpusQueryLanguageParser parser = new CorpusQueryLanguageParser();
        TextPattern pattern;
        try {
            pattern = parser.parseQuery(cqlQuery);
        } catch (InvalidQuery e) {
            throw new RuntimeException(e);
        }
        assertRoundtrip(pattern);
    }

    @Test
    public void testSimple() throws IOException {
        TextPattern pattern = new TextPatternTerm("cow");
        String expected = "{\"type\":\"term\",\"value\":\"cow\"}";
        assertRewritesTo(expected, pattern);
        assertRoundtrip(pattern);
    }

    @Test
    public void testSentence() throws IOException {
        TextPattern pattern = new TextPatternSequence(
                TextPatternRepetition.get(
                        new TextPatternTerm("lazy"),
                        1,
                        TextPattern.MAX_UNLIMITED
                ),
                new TextPatternTerm("cow"));
        String expected = "{\"type\":\"sequence\",\"clauses\":["+
                "{\"type\":\"repeat\",\"clause\":"+
                    "{\"type\":\"term\",\"value\":\"lazy\"},"+
                    "\"min\":1},"+
                "{\"type\":\"term\",\"value\":\"cow\"}"+
            "]}";
        assertRewritesTo(expected, pattern);
        assertRoundtrip(pattern);
    }

    @Test
    public void testComplexCql() throws IOException {
        String pattern = "A:('the' [lemma='quick']+) ([pos='adj' & word!='yellow'] ('fox' 'jumps') 'over') within B:</s>";
        assertRoundtrip(pattern);
    }

    @Test
    public void testRelationsCql() throws IOException {
        String pattern = "[pos='VERB' & xpos = '.*tgw.*mv.*'  & deprel='root'] "+
                "-nsubj-> ([pos='NOUN'] !-(det|nmod:poss)-> []); "+
                "-obj-> ([pos='NOUN'] !-(det|nmod:poss)->[])";
        assertRoundtrip(pattern);
    }

    @Test
    public void testConstraints() throws IOException {
        String pattern = "A:[] 'or' B:[] :: A.lemma = B.lemma & start(A) > end(B) & A.word = 'koe'";
        assertRoundtrip(pattern);
    }

    @Test
    public void testIntRange() throws IOException {
        //assertRoundtrip("[number=in[24,42]]");
        assertRoundtrip("<s number='1'/>");
        //assertRoundtrip("<s number=in[123,4567]/>");
    }
}
