package nl.inl.blacklab.search;

import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.TextPatternTerm;

public class TestTextPatternRegex {

    @Test
    public void testEmptyPattern() {
        TextPatternTerm r = (TextPatternTerm) TextPattern.regex("");
        Assert.assertEquals("", r.getValue());
        Assert.assertEquals("", r.getValue());
    }

    @Test
    public void testBasicPattern() {
        TextPatternTerm r = (TextPatternTerm) TextPattern.regex("bla");
        Assert.assertEquals("bla", r.getValue());
    }
}
