package nl.inl.blacklab.indexers.config.saxon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import net.sf.saxon.om.AxisInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.tree.iter.AxisIterator;
import net.sf.saxon.type.Type;

public class TestElementSourceIdentity {

    @Test
    public void testRejectsNonElementAndForeignTreeNodes() throws Exception {
        SaxonDocumentWithElementOffsets first = parse("<root><w>one</w></root>");
        SaxonDocumentWithElementOffsets second = parse("<root><w>two</w></root>");
        NodeInfo documentNode = first.getDocument().getRootNode();
        NodeInfo foreignRoot = collectAllElements(second.getDocument().getRootNode()).get(0);

        assertEquals(-1, first.getElementOrdinal(documentNode));
        assertEquals(-1, first.getElementOrdinal(foreignRoot));
        try {
            first.getElementStartCharOffset(foreignRoot);
            fail("Expected a foreign-tree node to be rejected");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("not a material element"));
        }
    }

    @Test
    public void testRejectsElementsExpandedFromInternalEntities() throws Exception {
        String xml = "<!DOCTYPE root [<!ENTITY e '<w>x</w>'>]><root>&e;<literal/></root>";
        SaxonDocumentWithElementOffsets document = parse(xml);
        List<NodeInfo> elements = collectAllElements(document.getDocument().getRootNode());
        NodeInfo root = elements.get(0);
        NodeInfo expanded = elements.get(1);
        NodeInfo literal = elements.get(2);

        assertEquals(0, document.getElementOrdinal(root));
        assertEquals(-1, document.getElementOrdinal(expanded));
        // Woodstox also reports a stale, entity-local start for the following literal element.
        // Without scanning the source there is no safe way to reconstruct that element's exact interval.
        assertEquals(-1, document.getElementOrdinal(literal));
    }

    @Test
    public void testTextEntityDoesNotInvalidateFollowingLiteralElement() throws Exception {
        String xml = "<!DOCTYPE root [<!ENTITY e 'text'>]><root>&e;<literal/></root>";
        SaxonDocumentWithElementOffsets document = parse(xml);
        NodeInfo literal = collectAllElements(document.getDocument().getRootNode()).get(1);

        assertEquals(1, document.getElementOrdinal(literal));
        assertEquals(xml.indexOf("<literal/>"), document.getElementStartCharOffset(literal));
        assertEquals(xml.indexOf("<literal/>") + "<literal/>".length(),
                document.getElementEndCharOffset(literal));
    }

    @Test
    public void testRejectsAmbiguousExternalEntityLocation() throws Exception {
        Path entity = Files.createTempFile("blacklab-external-entity-", ".xml");
        try {
            Files.writeString(entity, "\n\n<w/>");
            String xml = "<!DOCTYPE root [<!ENTITY e SYSTEM \"" + entity + "\">]>\n<root>\n<a/>&e;</root>";
            SaxonDocumentWithElementOffsets document = parse(xml);
            List<NodeInfo> elements = collectAllElements(document.getDocument().getRootNode());

            assertEquals(0, document.getElementOrdinal(elements.get(0)));
            // The literal and external elements share Saxon's line/column identity; neither range is safe.
            assertEquals(-1, document.getElementOrdinal(elements.get(1)));
            assertEquals(-1, document.getElementOrdinal(elements.get(2)));
        } finally {
            Files.deleteIfExists(entity);
        }
    }

    private static SaxonDocumentWithElementOffsets parse(String xml) throws Exception {
        return new SaxonDocumentWithElementOffsets(
                new StringReader(xml), SaxonHelper.getProcessor().getUnderlyingConfiguration());
    }

    private static List<NodeInfo> collectAllElements(NodeInfo node) {
        List<NodeInfo> result = new ArrayList<>();
        collectElementsRecursive(node, result);
        return result;
    }

    private static void collectElementsRecursive(NodeInfo node, List<NodeInfo> result) {
        if (node.getNodeKind() == Type.ELEMENT)
            result.add(node);
        AxisIterator children = node.iterateAxis(AxisInfo.CHILD);
        NodeInfo child;
        while ((child = children.next()) != null)
            collectElementsRecursive(child, result);
    }
}
