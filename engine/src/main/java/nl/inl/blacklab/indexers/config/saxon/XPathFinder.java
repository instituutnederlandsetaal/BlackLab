package nl.inl.blacklab.indexers.config.saxon;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;

import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.xpath.XPathEvaluator;
import nl.inl.blacklab.exceptions.ErrorIndexingFile;
import nl.inl.blacklab.exceptions.InvalidConfiguration;
import nl.inl.blacklab.indexers.config.DocIndexerXPath;

public class XPathFinder {

    public String currentNodeToString(NodeInfo node) {
        return node.getStringValue();
    }

    private static class MyNamespaceContext implements NamespaceContext {
        private final Map<String, String> ns = new HashMap<>(3);

        void add(String prefix, String uri) {
            if (uri.equals(ns.get(prefix)))
                return;
            ns.put(prefix, uri);
        }

        @Override
        public String getNamespaceURI(String prefix) {
            return ns.get(prefix);
        }

        @Override
        public String getPrefix(String namespaceURI) {
            return ns.entrySet().stream()
                    .filter(e -> e.getValue().equals(namespaceURI))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public Iterator<String> getPrefixes(String namespaceURI) {
            return ns.keySet().iterator();
        }
    }

    private final XPath xPath;

    Serializer serializer;

    /**
     * Compiled XPaths for use in one thread.
     */
    private final Map<String, XPathExpression> compiledXPaths = new HashMap<>();

    public XPathFinder(XPath xPath, Map<String, String> namespaces) {
        // setup namespace aware xpath that will compile xpath expressions
        this.xPath = xPath;
        if (namespaces != null && !namespaces.isEmpty()) {
            MyNamespaceContext context = new MyNamespaceContext();
            context.add("xml", "http://www.w3.org/XML/1998/namespace");
            for (Map.Entry<String, String> e: namespaces.entrySet()) {
                if (e.getKey().isEmpty()) {
                    NamespaceUri namespaceUri = NamespaceUri.of(namespaces.get(""));
                    ((XPathEvaluator)xPath).getStaticContext().setDefaultElementNamespace(namespaceUri);
                } else {
                    context.add(e.getKey(), e.getValue());
                }
            }
            xPath.setNamespaceContext(context);
        }

        // Set up serializer, for capturing XML code
        // (annotations can optionally capture XML instead of just a string value)
        Processor processor = new Processor(false);
        serializer = processor.newSerializer();
        serializer.setOutputProperty(Serializer.Property.INDENT, "yes");
    }

    /**
     * Create XPathExpression and cache.
     *
     * @param xpathExpr the xpath expression
     * @return the XPathExpression
     */
    private XPathExpression acquireExpression(String xpathExpr) throws XPathExpressionException {
        XPathExpression xPathExpression = compiledXPaths.get(xpathExpr);
        if (xPathExpression == null) {
            xPathExpression = xPath.compile(xpathExpr);
            compiledXPaths.put(xpathExpr, xPathExpression);
        }
        return xPathExpression;
    }

    /**
     * Find results in a context, return a list of Objects. This approach is useful if you don't know
     * the return type(s) in advance. This works for all return types of an xPath, also the ones that
     * return for example one boolean. Often a List&lt;NodeInfo> will be returned.
     */
    public List<?> find(String xPath, Object context) {
        try {
            return (List<?>) acquireExpression(xPath).evaluate(context, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            throw new InvalidConfiguration(e.getMessage() + "; for xpath " + xPath, e);
        }
    }

    /**
     * Calls {@link #find(String, Object)} and casts the result to List&lt;NodeInfo>, NOTE that the
     * resulting list may still contain Objects that are no NodeInfo, due to the way collections work,
     * Collections.checkedList won't help here.
     */
    @SuppressWarnings("unchecked")
    public List<NodeInfo> findNodes(String xPath, Object context) {
        return (List<NodeInfo>) find(xPath, context);
    }

    public void xpathForEach(String xPath, NodeInfo context, DocIndexerXPath.NodeHandler<NodeInfo> handler) {
        List<NodeInfo> docs = findNodes(xPath, context);
        for (NodeInfo doc: docs) {
            handler.handle(doc);
        }
    }

    public void xpathForEachStringValue(String xPath, NodeInfo context, DocIndexerXPath.StringValueHandler handler) {
        for (Object match: find(xPath, context)) {
            String value = match instanceof NodeInfo ?
                    currentNodeToString((NodeInfo) match) :
                    String.valueOf(match);
            handler.handle(value);
        }
    }

    /**
     * Capture the XML code for the given node.
     *
     * @param value the node to capture
     * @return the XML code for the node
     */
    public String currentNodeXml(NodeInfo value) {
        try {
            return serializer.serializeNodeToString(new XdmNode(value));
        } catch (SaxonApiException e) {
            throw new ErrorIndexingFile(e);
        }
    }

    /**
     * Capture the XML code for the given node.
     *
     * @param xPath   the xpath to capture
     * @param context context to capture it from
     * @return the XML code for the node
     */
    public String xpathXml(String xPath, NodeInfo context) {
        List<?> list = find(xPath, context);
        if (list.size() == 1) {
            Object o = list.get(0);
            if (o instanceof NodeInfo) {
                try {
                    return serializer.serializeNodeToString(new XdmNode((NodeInfo)o));
                } catch (SaxonApiException e) {
                    throw new ErrorIndexingFile(e);
                }
            } else {
                throw new ErrorIndexingFile("XPath matched non-NodeInfo; cannot convert to XML: " + xPath);
            }
        } else {
            if (list.isEmpty())
                return "";
            else
                throw new InvalidConfiguration(
                        String.format(
                                "list %s contains multiple values, change your xpath %s to return one result",
                                list.stream()
                                        .map(o -> o instanceof NodeInfo ?
                                                ((NodeInfo) o).toShortString() :
                                                String.valueOf(o))
                                        .toList(), xPath));
        }
    }

    /**
     * return a string representation of an xpath result, using {@link NodeInfo#getStringValue()} or
     * String.valueOf. Handling multiple results should be done in xPath, for example concat.
     *
     * @throws InvalidConfiguration when the xpath returns multiple results
     */
    public String xpathValue(String xPath, Object context) {
        List<?> list = find(xPath, context);
        if (list.size() == 1) {
            Object o = list.get(0);
            if (o instanceof NodeInfo) {
                return ((NodeInfo) o).getStringValue();
            } else {
                return String.valueOf(o);
            }
        } else {
            if (list.isEmpty())
                return "";
            else
                throw new InvalidConfiguration(
                        String.format(
                                "list %s contains multiple values, change your xpath %s to return one result or concatenate",
                                list.stream()
                                        .map(o -> o instanceof NodeInfo ?
                                                ((NodeInfo) o).toShortString() :
                                                String.valueOf(o))
                                        .toList(), xPath));
        }
    }
}
