package nl.inl.blacklab.indexers.config.saxon;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.IntStream;

import org.junit.Assert;
import org.junit.Test;

import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmEmptySequence;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;
import nl.inl.blacklab.exceptions.InvalidConfiguration;

public class TestXPathFinder {

    @Test
    public void testTypedDynamicVariables() throws Exception {
        NodeInfo root = parseRoot("<root><w>A</w><w>B</w></root>");
        NodeInfo firstWord = new XPathFinder(Map.of(), Map.of()).findNodes(".//w[1]", root).get(0);
        XPathFinder finder = new XPathFinder(Map.of(), Map.of());

        String value = finder.xpathValue(
                "string-join(($atomic, string($node), string(empty($missing))), '|')",
                XdmValue.wrap(root),
                Map.of(
                        "atomic", new XdmAtomicValue(7),
                        "node", XdmValue.wrap(firstWord),
                        "missing", XdmEmptySequence.getInstance()));

        Assert.assertEquals("7|A|true", value);
    }

    @Test
    public void testSortingAndInlineFunctions() throws Exception {
        NodeInfo root = parseRoot("<root><w n=\"2\">B</w><w n=\"1\">A</w></root>");
        XPathFinder finder = new XPathFinder(Map.of(), Map.of());

        List<NodeInfo> words = finder.findNodes(
                "sort(.//w, (), function($w) { xs:integer(exactly-one($w/@n)) })", root);

        Assert.assertEquals(List.of("A", "B"), words.stream().map(NodeInfo::getStringValue).toList());
    }

    @Test
    public void testReentrantEvaluationUsesIndependentSelectors() throws Exception {
        NodeInfo root = parseRoot("<root><w>A</w><w>B</w></root>");
        XPathFinder finder = new XPathFinder(Map.of(), Map.of());
        List<String> values = new ArrayList<>();

        try (XPathFinder.XPathResult outer = finder.findLeased(".//w", XdmValue.wrap(root))) {
            for (XdmItem item: outer) {
                values.add("outer:" + item.getStringValue());
                finder.xpathForEachStringValue(".//w", root, value -> values.add("nested:" + value));
            }
        }

        Assert.assertEquals(List.of("outer:A", "nested:A", "nested:B", "outer:B", "nested:A", "nested:B"),
                values);
    }

    @Test
    public void testNextAdvancesAcrossContextsWithoutHasNext() throws Exception {
        NodeInfo root = parseRoot("<root><w>A</w><w>B</w></root>");
        XPathFinder finder = new XPathFinder(Map.of(), Map.of());
        List<NodeInfo> words = finder.findNodes(".//w", root);
        XdmValue contexts = new XdmValue(words.stream().map(XdmNode::new).toList());

        try (XPathFinder.XPathResult result = finder.findLeased("string(.)", contexts)) {
            Assert.assertEquals("A", result.next().getStringValue());
            Assert.assertEquals("B", result.next().getStringValue());
            Assert.assertThrows(NoSuchElementException.class, result::next);
        }
    }

    @Test
    public void testClosedLeaseCanBeReused() throws Exception {
        NodeInfo root = parseRoot("<root><w>A</w><w>B</w></root>");
        XPathFinder finder = new XPathFinder(Map.of(), Map.of());

        XPathFinder.XPathResult partial = finder.findLeased(".//w", XdmValue.wrap(root));
        Assert.assertTrue(partial.hasNext());
        Assert.assertEquals("A", partial.next().getStringValue());
        partial.close();
        Assert.assertFalse(partial.hasNext());

        Assert.assertEquals(List.of("A", "B"), finder.findNodes(".//w", root).stream()
                .map(NodeInfo::getStringValue).toList());
    }

    @Test
    public void testHandlerFailureReleasesLease() throws Exception {
        NodeInfo root = parseRoot("<root><w>A</w></root>");
        XPathFinder finder = new XPathFinder(Map.of(), Map.of());
        try {
            finder.xpathForEachStringValue(".//w", root, value -> {
                throw new IllegalStateException("expected");
            });
            Assert.fail("Expected handler failure");
        } catch (IllegalStateException e) {
            Assert.assertEquals("expected", e.getMessage());
        }
        Assert.assertEquals("A", finder.xpathValue(".//w", root));
    }

    @Test
    public void testConcurrentFindersShareCompiledExpressionsSafely() throws Exception {
        NodeInfo root = parseRoot("<root/>");

        List<Integer> actual = IntStream.range(0, 100).parallel()
                .map(i -> Integer.parseInt(new XPathFinder(Map.of(), Map.of()).xpathValue(
                        "$value", XdmValue.wrap(root), Map.of("value", new XdmAtomicValue(i)))))
                .boxed()
                .toList();

        Assert.assertEquals(IntStream.range(0, 100).boxed().toList(), actual);
    }

    @Test
    public void testPrefixedDynamicVariable() throws Exception {
        NodeInfo root = parseRoot("<root/>");
        XPathFinder finder = new XPathFinder(Map.of("p", "urn:test"), Map.of());

        Assert.assertEquals("value", finder.xpathValue("$p:value", XdmValue.wrap(root),
                Map.of("p:value", new XdmAtomicValue("value"))));
    }

    @Test
    public void testImplicitXmlNamespaceVariable() throws Exception {
        NodeInfo root = parseRoot("<root/>");
        XPathFinder finder = new XPathFinder(Map.of(), Map.of());

        Assert.assertEquals("value", finder.xpathValue("$xml:value", XdmValue.wrap(root),
                Map.of("xml:value", new XdmAtomicValue("value"))));
    }

    @Test
    public void testStringValueRejectsFunctionItems() throws Exception {
        NodeInfo root = parseRoot("<root/>");
        XPathFinder finder = new XPathFinder(Map.of(), Map.of());

        for (String expression: List.of("map { 'key': 1 }", "[1]", "function() { 1 }")) {
            try {
                finder.xpathValue(expression, root);
                Assert.fail("Expected function item to be rejected: " + expression);
            } catch (InvalidConfiguration e) {
                Assert.assertTrue(e.getMessage().contains("XPath string value cannot be taken from"));
            }
        }
    }

    private static NodeInfo parseRoot(String xml) throws Exception {
        SaxonDocumentWithElementOffsets document = SaxonHelper.parseDocument(new StringReader(xml), false);
        NodeInfo documentNode = document.getDocument().getRootNode();
        return new XPathFinder(Map.of(), Map.of()).findNodes("/*", documentNode).get(0);
    }
}
