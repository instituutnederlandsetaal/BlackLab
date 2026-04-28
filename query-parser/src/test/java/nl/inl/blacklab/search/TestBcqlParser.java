package nl.inl.blacklab.search;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.plugins.param.PluginParams;
import nl.inl.blacklab.queryParser.corpusql.BcqlQueryLanguageParser;
import nl.inl.blacklab.search.matchfilter.ConstraintValue;
import nl.inl.blacklab.search.matchfilter.ConstraintValueString;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.TextPatternCompare;
import nl.inl.blacklab.search.textpattern.TextPatternRelationMatch;
import nl.inl.blacklab.search.textpattern.TextPatternValue;

public class TestBcqlParser {

    @Test
    public void testEscapedQuote() throws InvalidQuery {
        String pattern = "[lemma=\"\\\"\"]";
        TextPattern tp = BcqlQueryLanguageParser.parse(null, PluginParams.NONE, pattern);
        Assert.assertTrue(tp instanceof TextPatternCompare);
        TextPattern rightClause = ((TextPatternCompare) tp).getRightClause();
        Assert.assertTrue(rightClause instanceof TextPatternValue);
        ConstraintValue cv = ((TextPatternValue) rightClause).getValue();
        Assert.assertTrue(cv instanceof ConstraintValueString);
        Assert.assertEquals("\"", cv.getValue());
    }

    @Test
    public void testParseAlignmentQuery() throws IOException, InvalidQuery {
        String pattern = "[word='the'] =verse-alignment=>nl [word='het']";
        TextPattern tp = BcqlQueryLanguageParser.parse(null, PluginParams.NONE, pattern);
        Assert.assertEquals(TextPatternRelationMatch.class, tp.getClass());
    }
}
