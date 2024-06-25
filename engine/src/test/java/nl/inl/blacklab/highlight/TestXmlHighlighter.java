package nl.inl.blacklab.highlight;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import nl.inl.util.XmlHighlighter;
import nl.inl.util.XmlHighlighter.HitCharSpan;
import nl.inl.util.XmlHighlighter.UnbalancedTagsStrategy;

public class TestXmlHighlighter {

    private XmlHighlighter hl;

    @Before
    public void setUp() {
        hl = new XmlHighlighter();
        hl.setRemoveEmptyHlTags(false); // don't do this for testing, as it might conceal mistakes
    }

    @Test
    public void testHighlightNoTags() {
        String xmlContent = "The quick brown fox jumps over the lazy dog.";

        List<HitCharSpan> hits = new ArrayList<>();
        hits.add(new HitCharSpan(10, 25));
        Assert.assertEquals("The quick <hl>brown fox jumps</hl> over the lazy dog.", hl.highlight(xmlContent, hits));
    }

    @Test
    public void testHighlightEndsUnmatched() {
        String xmlContent = "The quick</i> brown <b>fox</b> jumps over <em>the lazy dog.";

        List<HitCharSpan> hits = new ArrayList<>();
        hits.add(new HitCharSpan(4, 49));
        Assert.assertEquals(
                "<i>The <hl>quick</hl></i><hl> brown <b>fox</b> jumps over </hl><em><hl>the</hl> lazy dog.</em>",
                hl.highlight(xmlContent, hits));
    }

    @Test
    public void testHighlightMatchedInsideHit() {
        String xmlContent = "The quick <em>brown fox</em> jumps over the lazy dog.";

        List<HitCharSpan> hits = new ArrayList<>();
        hits.add(new HitCharSpan(4, 34));
        Assert.assertEquals("The <hl>quick <em>brown fox</em> jumps</hl> over the lazy dog.",
                hl.highlight(xmlContent, hits));
    }

    @Test
    public void testHighlightMatchedInsideHitEdges() {
        String xmlContent = "The quick <em>brown fox</em> jumps over the lazy dog.";

        List<HitCharSpan> hits = new ArrayList<>();
        hits.add(new HitCharSpan(10, 28));
        Assert.assertEquals("The quick <hl><em>brown fox</em></hl> jumps over the lazy dog.",
                hl.highlight(xmlContent, hits));
    }

    @Test
    public void testHighlightNotMatchedInsideHitEdge1() {
        String xmlContent = "The quick <em>brown fox</em> jumps over the lazy dog.";

        List<HitCharSpan> hits = new ArrayList<>();
        hits.add(new HitCharSpan(10, 23));
        Assert.assertEquals("The quick <hl></hl><em><hl>brown fox</hl></em> jumps over the lazy dog.",
                hl.highlight(xmlContent, hits));
    }

    @Test
    public void testHighlightNotMatchedInsideHitEdge2() {
        String xmlContent = "The quick <em>brown fox</em> jumps over the lazy dog.";

        List<HitCharSpan> hits = new ArrayList<>();
        hits.add(new HitCharSpan(14, 28));
        Assert.assertEquals("The quick <em><hl>brown fox</hl></em><hl></hl> jumps over the lazy dog.",
                hl.highlight(xmlContent, hits));
    }

    @Test
    public void testHighlightUnmatchedInsideHit() {
        String xmlContent = "The quick <em>brown fox</em> jumps over the lazy dog.";

        List<HitCharSpan> hits = new ArrayList<>();
        hits.add(new HitCharSpan(20, 34));
        Assert.assertEquals("The quick <em>brown <hl>fox</hl></em><hl> jumps</hl> over the lazy dog.",
                hl.highlight(xmlContent, hits));
    }

    @Test
    public void testHighlightSelfClosingTag() {
        String xmlContent = "The quick brown <word content='fox' / > jumps over the lazy dog.";

        List<HitCharSpan> hits = new ArrayList<>();
        hits.add(new HitCharSpan(10, 45));
        Assert.assertEquals("The quick <hl>brown <word content='fox' / > jumps</hl> over the lazy dog.",
                hl.highlight(xmlContent, hits));
    }

    @Test
    public void testMakeWellFormedAddCloseTag() {
        String xmlContent = "The <word content='fox'>jumps over";
        Assert.assertEquals("The <word content='fox'>jumps over</word>", hl.makeWellFormed(xmlContent));
    }

    @Test
    public void testMakeWellFormedAddOpenTag() {
        String xmlContent = "The fox</word> jumps over";
        Assert.assertEquals("<word>The fox</word> jumps over", hl.makeWellFormed(xmlContent));
    }

    @Test
    public void testMakeWellFormedRemoveOpenTag() {
        hl.setUnbalancedTagsStrategy(UnbalancedTagsStrategy.REMOVE_TAG);
        String xmlContent = "The <word content='fox'>jumps over";
        Assert.assertEquals("The jumps over", hl.makeWellFormed(xmlContent));
    }

    @Test
    public void testMakeWellFormedRemoveCloseTag() {
        hl.setUnbalancedTagsStrategy(UnbalancedTagsStrategy.REMOVE_TAG);
        String xmlContent = "The fox</word> jumps over";
        Assert.assertEquals("The fox jumps over", hl.makeWellFormed(xmlContent));
    }

    @Ignore // Fix exists, but causes StackOverflowError for large docs, see commented out code in XmlHighlighter
    @Test
    public void testMakeWellFormedIgnoreTagsInCdata() {
        String xmlContent = "The fox<![CDATA[  </word>\n<test>  ]]> jumps <bla>over";
        Assert.assertEquals("The fox<![CDATA[  </word>\n<test>  ]]> jumps <bla>over</bla>",
                hl.makeWellFormed(xmlContent));
    }

}
