package nl.inl.blacklab.search;

import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.search.textpattern.TextPatternRegex;
import nl.inl.blacklab.search.textpattern.TextPatternTerm;

public class TestTextPatternRegex {

    @Test
    public void testEmptyPattern() {
        TextPatternTerm r = new TextPatternRegex("");
        Assert.assertEquals("", r.getValue());
        Assert.assertEquals("", r.getValue());
    }

    @Test
    public void testBasicPattern() {
        TextPatternRegex r = new TextPatternRegex("bla");
        Assert.assertEquals("bla", r.getValue());
    }
}
