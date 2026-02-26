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
    public void testRemoveEmptyHightlights() {
        XmlHighlighter hl = new XmlHighlighter();
        try {
            String xmlContent = "The quick brown fox <b>jumps</b> over the lazy dog.";

            List<HitCharSpan> hits = List.of(new HitCharSpan(23, 32), new HitCharSpan(25, 32));
            Assert.assertEquals("The quick brown fox <b><hl n=\"0\">ju<hl n=\"1\">mps</hl></hl></b> over the lazy dog.",
                    hl.highlight(xmlContent, hits));
        } finally {
            hl.setRemoveEmptyHlTags(false);
        }
    }

    @Test
    public void testHighlightNoTags() {
        String xmlContent = "The quick brown fox jumps over the lazy dog.";

        List<HitCharSpan> hits = new ArrayList<>();
        hits.add(new HitCharSpan(10, 25));
        Assert.assertEquals("The quick <hl n=\"0\">brown fox jumps</hl> over the lazy dog.", hl.highlight(xmlContent, hits));
    }

    @Test
    public void testHighlightEndsUnmatched() {
        String xmlContent = "The quick</i> brown <b>fox</b> jumps over <em>the lazy dog.";

        List<HitCharSpan> hits = new ArrayList<>();
        hits.add(new HitCharSpan(4, 49));
        Assert.assertEquals(
                "<i>The <hl n=\"0\">quick</hl></i><hl n=\"0\"> brown <b>fox</b> jumps over </hl><em><hl n=\"0\">the</hl> lazy dog.</em>",
                hl.highlight(xmlContent, hits));
    }

    @Test
    public void testHighlightMatchedInsideHit() {
        String xmlContent = "The quick <em>brown fox</em> jumps over the lazy dog.";

        List<HitCharSpan> hits = new ArrayList<>();
        hits.add(new HitCharSpan(4, 34));
        Assert.assertEquals("The <hl n=\"0\">quick <em>brown fox</em> jumps</hl> over the lazy dog.",
                hl.highlight(xmlContent, hits));
    }

    @Test
    public void testHighlightMatchedInsideHitEdges() {
        String xmlContent = "The quick <em>brown fox</em> jumps over the lazy dog.";

        List<HitCharSpan> hits = new ArrayList<>();
        hits.add(new HitCharSpan(10, 28));
        Assert.assertEquals("The quick <hl n=\"0\"><em>brown fox</em></hl> jumps over the lazy dog.",
                hl.highlight(xmlContent, hits));
    }

    @Test
    public void testHighlightNotMatchedInsideHitEdge1() {
        String xmlContent = "The quick <em>brown fox</em> jumps over the lazy dog.";

        List<HitCharSpan> hits = new ArrayList<>();
        hits.add(new HitCharSpan(10, 23));
        Assert.assertEquals("The quick <hl n=\"0\"></hl><em><hl n=\"0\">brown fox</hl></em> jumps over the lazy dog.",
                hl.highlight(xmlContent, hits));
    }

    @Test
    public void testHighlightNotMatchedInsideHitEdge2() {
        String xmlContent = "The quick <em>brown fox</em> jumps over the lazy dog.";

        List<HitCharSpan> hits = new ArrayList<>();
        hits.add(new HitCharSpan(14, 28));
        Assert.assertEquals("The quick <em><hl n=\"0\">brown fox</hl></em><hl n=\"0\"></hl> jumps over the lazy dog.",
                hl.highlight(xmlContent, hits));
    }

    @Test
    public void testHighlightUnmatchedInsideHit() {
        String xmlContent = "The quick <em>brown fox</em> jumps over the lazy dog.";

        List<HitCharSpan> hits = new ArrayList<>();
        hits.add(new HitCharSpan(20, 34));
        Assert.assertEquals("The quick <em>brown <hl n=\"0\">fox</hl></em><hl n=\"0\"> jumps</hl> over the lazy dog.",
                hl.highlight(xmlContent, hits));
    }

    @Test
    public void testHighlightOverlappingHits() {
        String xmlContent = "The quick brown fox jumps over the lazy dog.";

        List<HitCharSpan> hits = List.of(
            new HitCharSpan(16, 25),
            new HitCharSpan(20, 30));
        Assert.assertEquals("The quick brown <hl n=\"0\">fox <hl n=\"1\">jumps</hl></hl><hl n=\"1\"> over</hl> the lazy dog.",
                hl.highlight(xmlContent, hits));
    }

    @Test
    public void testHighlightSelfClosingTag() {
        String xmlContent = "The quick brown <word content='fox' / > jumps over the lazy dog.";

        List<HitCharSpan> hits = new ArrayList<>();
        hits.add(new HitCharSpan(10, 45));
        Assert.assertEquals("The quick <hl n=\"0\">brown <word content='fox' / > jumps</hl> over the lazy dog.",
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
