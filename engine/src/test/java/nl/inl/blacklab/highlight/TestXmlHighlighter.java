package nl.inl.blacklab.highlight;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

import org.junit.Assert;
import org.junit.Before;
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
            Assert.assertEquals("The quick brown fox <b><hl index=\"0\">ju<hl index=\"1\">mps</hl></hl></b> over the lazy dog.",
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
        Assert.assertEquals("The quick <hl index=\"0\">brown fox jumps</hl> over the lazy dog.", hl.highlight(xmlContent, hits));
    }

    @Test
    public void testHighlightEndsUnmatched() {
        String xmlContent = "The quick</i> brown <b>fox</b> jumps over <em>the lazy dog.";

        List<HitCharSpan> hits = new ArrayList<>();
        hits.add(new HitCharSpan(4, 49));
        Assert.assertEquals(
                "<i>The <hl index=\"0\">quick</hl></i><hl index=\"0\"> brown <b>fox</b> jumps over </hl><em><hl index=\"0\">the</hl> lazy dog.</em>",
                hl.highlight(xmlContent, hits));
    }

    @Test
    public void testHighlightMatchedInsideHit() {
        String xmlContent = "The quick <em>brown fox</em> jumps over the lazy dog.";

        List<HitCharSpan> hits = new ArrayList<>();
        hits.add(new HitCharSpan(4, 34));
        Assert.assertEquals("The <hl index=\"0\">quick <em>brown fox</em> jumps</hl> over the lazy dog.",
                hl.highlight(xmlContent, hits));
    }

    @Test
    public void testHighlightMatchedInsideHitEdges() {
        String xmlContent = "The quick <em>brown fox</em> jumps over the lazy dog.";

        List<HitCharSpan> hits = new ArrayList<>();
        hits.add(new HitCharSpan(10, 28));
        Assert.assertEquals("The quick <hl index=\"0\"><em>brown fox</em></hl> jumps over the lazy dog.",
                hl.highlight(xmlContent, hits));
    }

    @Test
    public void testHighlightNotMatchedInsideHitEdge1() {
        String xmlContent = "The quick <em>brown fox</em> jumps over the lazy dog.";

        List<HitCharSpan> hits = new ArrayList<>();
        hits.add(new HitCharSpan(10, 23));
        Assert.assertEquals("The quick <hl index=\"0\"></hl><em><hl index=\"0\">brown fox</hl></em> jumps over the lazy dog.",
                hl.highlight(xmlContent, hits));
    }

    @Test
    public void testHighlightNotMatchedInsideHitEdge2() {
        String xmlContent = "The quick <em>brown fox</em> jumps over the lazy dog.";

        List<HitCharSpan> hits = new ArrayList<>();
        hits.add(new HitCharSpan(14, 28));
        Assert.assertEquals("The quick <em><hl index=\"0\">brown fox</hl></em><hl index=\"0\"></hl> jumps over the lazy dog.",
                hl.highlight(xmlContent, hits));
    }

    @Test
    public void testHighlightUnmatchedInsideHit() {
        String xmlContent = "The quick <em>brown fox</em> jumps over the lazy dog.";

        List<HitCharSpan> hits = new ArrayList<>();
        hits.add(new HitCharSpan(20, 34));
        Assert.assertEquals("The quick <em>brown <hl index=\"0\">fox</hl></em><hl index=\"0\"> jumps</hl> over the lazy dog.",
                hl.highlight(xmlContent, hits));
    }

    @Test
    public void testHighlightOverlappingHits() {
        String xmlContent = "The quick brown fox jumps over the lazy dog.";

        List<HitCharSpan> hits = List.of(
            new HitCharSpan(16, 25),
            new HitCharSpan(20, 30));
        Assert.assertEquals("The quick brown <hl index=\"0\">fox <hl index=\"1\">jumps</hl></hl><hl index=\"1\"> over</hl> the lazy dog.",
                hl.highlight(xmlContent, hits));
    }

    @Test
    public void testHighlightSelfClosingTag() {
        String xmlContent = "The quick brown <word content='fox' / > jumps over the lazy dog.";

        List<HitCharSpan> hits = new ArrayList<>();
        hits.add(new HitCharSpan(10, 45));
        Assert.assertEquals("The quick <hl index=\"0\">brown <word content='fox' / > jumps</hl> over the lazy dog.",
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

    @Test
    public void testMakeWellFormedIgnoreTagsInCdata() throws Exception {
        String xmlContent = "The fox<![CDATA[  </word>\n<test>  ]]> jumps <bla>over";
        String actual = hl.makeWellFormed(xmlContent);
        Assert.assertEquals("The fox<![CDATA[  </word>\n<test>  ]]> jumps <bla>over</bla>", actual);
        assertParsesAsFragment(actual);
    }

    @Test
    public void testMakeWellFormedHandlesQuotedGtInSelfClosingTag() throws Exception {
        for (String xmlContent: List.of("<w a=\"x>y\"/>", "<w a='x>y'/>")) {
            String actual = hl.makeWellFormed(xmlContent);
            Assert.assertEquals(xmlContent, actual);
            assertParsesAsFragment(actual);
        }
    }

    @Test
    public void testMakeWellFormedIgnoresMarkupInsideCommentsAndProcessingInstructions() throws Exception {
        String xmlContent = "<!-- </fake> --><w><?pi test=\"<fake>\"?>word";
        String actual = hl.makeWellFormed(xmlContent);
        Assert.assertEquals("<!-- </fake> --><w><?pi test=\"<fake>\"?>word</w>", actual);
        assertParsesAsFragment(actual);
    }

    @Test
    public void testMakeWellFormedIgnoresBracketsInDoctypeCommentsAndProcessingInstructions() throws Exception {
        String xmlContent = "<!DOCTYPE root [<!-- [ --><?pi [?><!ELEMENT root ANY>]><root>";
        String actual = hl.makeWellFormed(xmlContent);
        Assert.assertEquals(xmlContent + "</root>", actual);
        assertParses(actual);
    }

    @Test
    public void testMakeWellFormedIgnoresDelimitersInQuotedDoctypeValues() throws Exception {
        String xmlContent = "<!DOCTYPE root [<!ENTITY a '<!--'><!ENTITY b '<?'>]><root>";
        String actual = hl.makeWellFormed(xmlContent);
        Assert.assertEquals(xmlContent + "</root>", actual);
        assertParses(actual);
    }

    @Test
    public void testMakeWellFormedRepairsCrossBranchFragmentByAddingTags() throws Exception {
        String actual = hl.makeWellFormed("<container/></a><b><token/>");
        Assert.assertEquals("<a><container/></a><b><token/></b>", actual);
        assertParsesAsFragment(actual);
    }

    @Test
    public void testMakeWellFormedRepairsCrossBranchFragmentByRemovingTags() throws Exception {
        hl.setUnbalancedTagsStrategy(UnbalancedTagsStrategy.REMOVE_TAG);
        String actual = hl.makeWellFormed("<container/></a><b><token/>");
        Assert.assertEquals("<container/><token/>", actual);
        assertParsesAsFragment(actual);
    }

    private static void assertParsesAsFragment(String fragment) throws Exception {
        assertParses("<wrapper>" + fragment + "</wrapper>");
    }

    private static void assertParses(String xml) throws Exception {
        XMLStreamReader reader = XMLInputFactory.newFactory()
                .createXMLStreamReader(new StringReader(xml));
        try {
            while (reader.hasNext())
                reader.next();
        } finally {
            reader.close();
        }
    }

}
