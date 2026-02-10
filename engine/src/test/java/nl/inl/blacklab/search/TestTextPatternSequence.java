package nl.inl.blacklab.search;

import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.TextPatternAnyToken;
import nl.inl.blacklab.search.textpattern.TextPatternSequence;

public class TestTextPatternSequence {

    @Test
    public void testSequence() {
        // "the" followed by "fox", with 1-3 tokens in between
        TextPattern seq = new TextPatternSequence(TextPattern.term("the"), TextPattern.term(
                "fox"));

        Assert.assertEquals("SEQ(TERM(the), TERM(fox))", seq.toString());
    }

    @Test
    public void testSequenceAnyMiddle() {
        // "the" followed by "fox", with 1-3 tokens in between
        TextPattern seq = new TextPatternSequence(TextPattern.term("the"),
                new TextPatternAnyToken(1, 3), TextPattern.term("fox"));

        Assert.assertEquals("SEQ(TERM(the), ANYTOKEN(1, 3), TERM(fox))", seq.toString());
    }

    @Test
    public void testSequenceAnyRight() {
        // "the" followed by "fox", with 1-3 tokens in between
        TextPattern seq = new TextPatternSequence(TextPattern.term("the"),
                new TextPatternAnyToken(1, 3));

        Assert.assertEquals("SEQ(TERM(the), ANYTOKEN(1, 3))", seq.toString());
    }

    @Test
    public void testSequenceAnyLeft() {
        // "the" followed by "fox", with 1-3 tokens in between
        TextPattern seq = new TextPatternSequence(new TextPatternAnyToken(1, 3),
                TextPattern.term("fox"));

        Assert.assertEquals("SEQ(ANYTOKEN(1, 3), TERM(fox))", seq.toString());
    }

}
