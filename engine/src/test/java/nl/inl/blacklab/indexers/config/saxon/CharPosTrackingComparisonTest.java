package nl.inl.blacklab.indexers.config.saxon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLStreamException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.TreeInfo;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.type.Type;

/**
 * Test to verify that SaxonDocumentWithElementOffsets correctly tracks
 * character offsets for XML elements.
 */
@RunWith(Parameterized.class)
public class CharPosTrackingComparisonTest {

    /** Record storing the expected results for an element. */
    record ExpectedElementOffset(String elementName, long startOffset, long endOffset) {}

    /** Record storing test data with expected results. */
    record TestCase(String name, String xml, List<ExpectedElementOffset> expectedOffsets) {}

    @Parameter(0)
    public String testName;

    @Parameter(1)
    public String xmlInput;

    @Parameter(2)
    public List<ExpectedElementOffset> expectedOffsets;

    @Parameters(name = "{index}: {0}")
    public static Collection<Object[]> data() {
        List<Object[]> testCases = new ArrayList<>();

        // Test cases with expected offset values
        List<TestCase> cases = List.of(
                new TestCase("simple-single-element",
                        "<root>text</root>",
                        List.of(new ExpectedElementOffset("root", 0, 17))),
                
                new TestCase("nested-elements",
                        "<root><child>text</child></root>",
                        List.of(
                                new ExpectedElementOffset("root", 0, 32),
                                new ExpectedElementOffset("child", 6, 25))),
                
                new TestCase("multiple-siblings",
                        "<root><a>1</a><b>2</b><c>3</c></root>",
                        List.of(
                                new ExpectedElementOffset("root", 0, 37),
                                new ExpectedElementOffset("a", 6, 14),
                                new ExpectedElementOffset("b", 14, 22),
                                new ExpectedElementOffset("c", 22, 30))),
                
                new TestCase("with-attributes",
                        "<root attr=\"value\"><child id=\"1\">text</child></root>",
                        List.of(
                                new ExpectedElementOffset("root", 0, 52),
                                new ExpectedElementOffset("child", 19, 45))),
                
                new TestCase("multiline",
                        "<root>\n  <child>\n    text\n  </child>\n</root>",
                        List.of(
                                new ExpectedElementOffset("root", 0, 44),
                                new ExpectedElementOffset("child", 9, 36))),
                
                new TestCase("self-closing-tags",
                        "<root><empty/></root>",
                        List.of(
                                new ExpectedElementOffset("root", 0, 21),
                                new ExpectedElementOffset("empty", 6, 14))),
                
                new TestCase("mixed-content",
                        "<root>before<child>inner</child>after</root>",
                        List.of(
                                new ExpectedElementOffset("root", 0, 44),
                                new ExpectedElementOffset("child", 12, 32))),
                
                new TestCase("deeply-nested",
                        "<a><b><c><d>text</d></c></b></a>",
                        List.of(
                                new ExpectedElementOffset("a", 0, 32),
                                new ExpectedElementOffset("b", 3, 28),
                                new ExpectedElementOffset("c", 6, 24),
                                new ExpectedElementOffset("d", 9, 20))),
                
                new TestCase("with-xml-declaration",
                        "<?xml version=\"1.0\"?><root><child>text</child></root>",
                        List.of(
                                new ExpectedElementOffset("root", 21, 53),
                                new ExpectedElementOffset("child", 27, 46))),
                
                new TestCase("whitespace-before-root",
                        "  <root><child>text</child></root>",
                        List.of(
                                new ExpectedElementOffset("root", 2, 34),
                                new ExpectedElementOffset("child", 8, 27))),
                
                new TestCase("multiple-attributes",
                        "<root><elem a=\"1\" b=\"2\" c=\"3\">text</elem></root>",
                        List.of(
                                new ExpectedElementOffset("root", 0, 48),
                                new ExpectedElementOffset("elem", 6, 41))),
                
                new TestCase("windows-line-endings",
                        "<root>\r\n  <child>text</child>\r\n</root>",
                        List.of(
                                new ExpectedElementOffset("root", 0, 38),
                                new ExpectedElementOffset("child", 10, 29))),
                
                new TestCase("with-comments",
                        "<root><!-- comment --><child>text</child></root>",
                        List.of(
                                new ExpectedElementOffset("root", 0, 48),
                                new ExpectedElementOffset("child", 22, 41))),
                
                new TestCase("with-cdata",
                        "<root><![CDATA[some <text>]]></root>",
                        List.of(new ExpectedElementOffset("root", 0, 36))),
                
                new TestCase("with-processing-instruction",
                        "<root><?pi data?><child>text</child></root>",
                        List.of(
                                new ExpectedElementOffset("root", 0, 43),
                                new ExpectedElementOffset("child", 17, 36)))
        );

        for (TestCase tc : cases) {
            testCases.add(new Object[] { tc.name(), tc.xml(), tc.expectedOffsets() });
        }

        return testCases;
    }

    @Test
    public void testElementOffsets() throws XMLStreamException, XPathException, IOException {
        SaxonDocumentWithElementOffsets doc = new SaxonDocumentWithElementOffsets(
                new StringReader(xmlInput),
                SaxonHelper.getProcessor().getUnderlyingConfiguration()
        );
        TreeInfo tree = doc.getDocument();
        NodeInfo root = tree.getRootNode();
        
        // Collect all elements in the document
        List<NodeInfo> elements = collectAllElements(root);
        
        // Build a map from element name to offsets for comparison
        Map<String, long[]> actualOffsets = new LinkedHashMap<>();
        for (NodeInfo elem : elements) {
            long startOffset = doc.getElementStartCharOffset(elem);
            long endOffset = doc.getElementEndCharOffset(elem);
            actualOffsets.put(elem.getLocalPart(), new long[] { startOffset, endOffset });
            
            // Verify the start offset points to a '<'
            if (startOffset >= 0 && startOffset < xmlInput.length()) {
                char startChar = xmlInput.charAt((int) startOffset);
                assertEquals("Start offset should point to '<' for element " + elem.getLocalPart(),
                        '<', startChar);
            }
        }
        
        // Verify against expected values
        for (ExpectedElementOffset expected : expectedOffsets) {
            long[] actual = actualOffsets.get(expected.elementName());
            assertNotNull("Element " + expected.elementName() + " should exist", actual);
            
            assertEquals("Start offset mismatch for element <" + expected.elementName() + ">", 
                    expected.startOffset(), actual[0]);
            assertEquals("End offset mismatch for element <" + expected.elementName() + ">", 
                    expected.endOffset(), actual[1]);
        }
    }

    /**
     * Collect all element nodes from a tree, depth-first.
     */
    private List<NodeInfo> collectAllElements(NodeInfo node) {
        List<NodeInfo> result = new ArrayList<>();
        collectElementsRecursive(node, result);
        return result;
    }

    private void collectElementsRecursive(NodeInfo node, List<NodeInfo> result) {
        if (node.getNodeKind() == Type.ELEMENT) {
            result.add(node);
        }
        AxisIterator children = node.iterateAxis(AxisInfo.CHILD);
        NodeInfo child;
        while ((child = children.next()) != null) {
            collectElementsRecursive(child, result);
        }
    }
}
