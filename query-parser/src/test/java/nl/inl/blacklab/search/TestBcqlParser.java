package nl.inl.blacklab.search;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.queryParser.corpusql.CorpusQueryLanguageParser;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.TextPatternRelationMatch;
import nl.inl.blacklab.search.textpattern.TextPatternTerm;

public class TestBcqlParser {

    @Test
    public void testEscapedQuote() throws IOException, InvalidQuery {
        String pattern = "[lemma=\"\\\"\"]";
        TextPattern tp = CorpusQueryLanguageParser.parse(pattern, "word");
        Assert.assertTrue(tp instanceof TextPatternTerm);
        Assert.assertEquals("\"", ((TextPatternTerm) tp).getValue());
    }

    @Test
    public void testParseAlignmentQuery() throws IOException, InvalidQuery {
        String pattern = "[word='the'] =verse-alignment=>nl [word='het']";
        TextPattern tp = CorpusQueryLanguageParser.parse(pattern, "word");
        Assert.assertEquals(TextPatternRelationMatch.class, tp.getClass());
    }
}
