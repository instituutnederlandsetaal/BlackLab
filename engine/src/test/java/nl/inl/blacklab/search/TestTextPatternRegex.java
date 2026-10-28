package nl.inl.blacklab.search;

import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.search.textpattern.TextPattern;

public class TestTextPatternRegex {

    @Test
    public void testEmptyPattern() {
        TextPattern r = TextPattern.regex("");
        Assert.assertEquals("TERM()", r.toString());
    }

    @Test
    public void testBasicPattern() {
        TextPattern r = TextPattern.regex("bla");
        Assert.assertEquals("TERM(bla)", r.toString());
    }
}
