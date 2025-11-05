package nl.inl.blacklab.indexers.config.saxon;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.UnprefixedElementMatchingPolicy;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;
import nl.inl.blacklab.exceptions.ErrorIndexingFile;
import nl.inl.blacklab.exceptions.InvalidConfiguration;
import nl.inl.blacklab.indexers.config.DocIndexerXPath;

public class XPathFinder {

    private static final Logger logger = LogManager.getLogger(XPathFinder.class);

    /** Prefix for the implicitly declared xml namespace */
    public static final String NAMESPACE_XML_PREFIX = "xml";

    /** URI for the implicitly declared xml namespace */
    public static final String NAMESPACE_XML_URI = "http://www.w3.org/XML/1998/namespace";

    private final LoadingCache<Map<String, String>, XPathCompiler> compilerCache = CacheBuilder.newBuilder()
        .maximumSize(50) // should be large enough for most use cases?
        .expireAfterAccess(Duration.ofMinutes(1))
        .build(new CacheLoader<Map<String, String>, XPathCompiler>() {
            @Override
            public XPathCompiler load(Map<String, String> namespaces) {
                var fac = SaxonHelper.newXPathFactory();
                fac.setCaching(true);
                // xml namespace is implicit
                fac.declareNamespace(NAMESPACE_XML_PREFIX, NAMESPACE_XML_URI);
                boolean hasNamespaces = false;
                if (namespaces != null && !namespaces.isEmpty()) {
                    for (Map.Entry<String, String> e: namespaces.entrySet()) {
                        if (e.getKey().equals(NAMESPACE_XML_PREFIX)) {
                            if (!e.getValue().equals(NAMESPACE_XML_URI))
                                logger.warn("Tried to redefine implicit 'xml' namespace prefix to '" + e.getValue()
                                        + "'); ignoring");
                            continue;
                        }
                        hasNamespaces = true;
                        fac.declareNamespace(e.getKey(), e.getValue());
                    }
                }
                if (!hasNamespaces) {
                    // No namespaces declared in the indexer config.
                    // Set Saxon to ignore namespace on elements without a prefix.
                    // This makes sure that we can index documents with or without namespaces, which
                    // unfortunately sometimes happens in large corpora.
                    fac.setUnprefixedElementMatchingPolicy(UnprefixedElementMatchingPolicy.ANY_NAMESPACE);
                }
                return fac;
            }
        });
    private final XPathCompiler xPath;

    private final Serializer serializer;

    private static final ThreadLocal<Map<String, XPathSelector>> compiledXPaths = ThreadLocal.withInitial(java.util.HashMap::new);

    public XPathFinder(Map<String, String> namespaces) {
        try { this.xPath = compilerCache.get(namespaces != null ? namespaces : Collections.emptyMap()); }
        catch (Exception e) {
            throw new InvalidConfiguration("Error setting up XPath compiler", e);
        }
        
        // Set up serializer, for capturing XML code
        // (annotations can optionally capture XML instead of just a string value)
        serializer = SaxonHelper.getProcessor().newSerializer();
        serializer.setOutputProperty(Serializer.Property.INDENT, "yes");
    }

    /**
     * Compile XPath expression.
     *
     * @param xpathExpr the xpath expression
     * @return the compiled expression
     */
    private XPathSelector acquireExpression(String xpathExpr) throws SaxonApiException {
        return compiledXPaths.get().computeIfAbsent(xpathExpr, expr -> {
            try {
                return xPath.compile(expr).load();
            } catch (SaxonApiException e) {
                throw new InvalidConfiguration(e.getMessage() + "; for xpath " + xPath, e);
            }
        });
    }

    public List<NodeInfo> findNodes(String wordsPath, NodeInfo container) {
        List<NodeInfo> results = new ArrayList<>();
        for (XdmItem item: find(wordsPath, container)) {
            if (item.isNode())
                results.add(((XdmNode) item).getUnderlyingNode());
            else
                logger.warn("XPath {} returned non-node: {}", wordsPath, item);
        }
        return results;
    }

    /**
     * Find results in a context, return a list of Objects. This approach is useful if you don't know
     * the return type(s) in advance. This works for all return types of an xPath, also the ones that
     * return for example one boolean. Often a List&lt;NodeInfo> will be returned.
     */
    public XdmValue find(String xPath, NodeInfo context) {
        try {
            XPathSelector selector = acquireExpression(xPath);
            selector.setContextItem(new XdmNode(context));
            return selector.evaluate();
        } catch (SaxonApiException e) {
            throw new InvalidConfiguration(e.getMessage() + "; for xpath " + xPath, e);
        }
    }

    public void xpathForEach(String xPath, NodeInfo context, DocIndexerXPath.NodeHandler<NodeInfo> handler) {
        for (XdmItem item: find(xPath, context)) {
            if (item.isNode()) {
                handler.handle(((XdmNode) item).getUnderlyingNode());
            }
        }
    }

    public void xpathForEachStringValue(String xPath, NodeInfo context, DocIndexerXPath.StringValueHandler handler) {
        for (XdmItem item: find(xPath, context)) {
            handler.handle(item.getStringValue());
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
        XdmValue list = find(xPath, context);
        if (list.size() == 1) {
            XdmItem o = list.itemAt(0);
            if (o.isNode()) {
                try {
                    return serializer.serializeNodeToString((XdmNode)o);
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
    public String xpathValue(String xPath, NodeInfo context) {
        XdmValue list = find(xPath, context);
        if (list.size() == 1) {
            return list.itemAt(0).getStringValue();
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
