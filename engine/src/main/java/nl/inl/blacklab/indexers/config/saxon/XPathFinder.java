package nl.inl.blacklab.indexers.config.saxon;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.UnprefixedElementMatchingPolicy;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.trans.XPathException;
import nl.inl.blacklab.exceptions.ErrorIndexingFile;
import nl.inl.blacklab.exceptions.InvalidConfiguration;
import nl.inl.blacklab.indexers.config.InputFormatTypeXml;

public class XPathFinder {

    private static final Logger logger = LogManager.getLogger(XPathFinder.class);

    /** Prefix for the implicitly declared xml namespace */
    public static final String NAMESPACE_XML_PREFIX = "xml";

    /** URI for the implicitly declared xml namespace */
    public static final String NAMESPACE_XML_URI = "http://www.w3.org/XML/1998/namespace";

    private final XPathCompiler xPath;

    private final Serializer serializer;

    /** Variables to make available from XPath */
    private final Map<String, String> vars = new HashMap<>();

    private final LoadingCache<Map<String, String>, XPathCompiler> compilerCache = Caffeine.newBuilder()
        .maximumSize(50) // should be large enough for most use cases?
        .expireAfterAccess(Duration.ofMinutes(1))
        .build(namespaces -> {
            var fac = SaxonHelper.newXPathFactory();
            fac.setCaching(true);
            
            for (String var: vars.keySet()) {
                fac.declareVariable(new QName(var));
            }
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
        });

    private static final ThreadLocal<Map<String, XPathSelector>> compiledXPaths = ThreadLocal.withInitial(java.util.HashMap::new);

    public XPathFinder(Map<String, String> namespaces, Map<String, String> vars) {
        this.vars.putAll(vars);

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
                var selector = xPath.compile(expr).load();
                // We've declared the variable, so we have to set it, whether it is used or not
                for (Map.Entry<String, String> var: vars.entrySet()) {
                    selector.setVariable(new QName(var.getKey()), new XdmAtomicValue(var.getValue()));
                }
                return selector;
            } catch (SaxonApiException e) {
                throw new InvalidConfiguration(e.getMessage() + "; for xpath '" + xpathExpr + "'", e);
            }
        });
    }

    public List<NodeInfo> findNodes(String wordsPath, NodeInfo container) {
        List<NodeInfo> results = new ArrayList<>();
        for (XdmItem item: find(wordsPath, XdmItem.wrap(container))) {
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
     * 
     * The context can be an XdmValue containing multiple items; the XPath will be evaluated for each
     * item in the context and all results will be collected.
     */
    public XdmValue find(String xPath, XdmValue context) {
        try {
            XPathSelector selector = acquireExpression(xPath);
            List<XdmItem> results = new ArrayList<>();
            for (XdmItem v : context) {
                selector.setContextItem(v);
                for (XdmItem item : selector.evaluate()) {
                    results.add(item);
                }
            }
            return XdmValue.makeSequence(results);
        } catch (SaxonApiException e) {
            throw new InvalidConfiguration(e.getMessage() + "; for xpath " + xPath, e);
        }
    }

    public void xpathForEach(String xPath, NodeInfo context, InputFormatTypeXml.NodeHandler handler) {
        XdmValue ctx = XdmItem.wrap(context);
        for (XdmItem item: find(xPath, ctx)) {
            if (item.isNode()) {
                XdmNode node = (XdmNode)item;
                handler.handle(node.getUnderlyingNode());
            }
        }
    }

    public void xpathForEach(String xPath, XdmValue context, InputFormatTypeXml.XdmValueHandler handler) {
        for (XdmItem item: find(xPath, context)) {
            handler.handle(XdmValue.wrap(item.getUnderlyingValue()));
        }
    }

    public void xpathForEachStringValue(String xPath, XdmValue context, InputFormatTypeXml.StringValueHandler handler) {
        for (XdmItem item: find(xPath, context)) {
            handler.handle(item.getStringValue());
        }
    }

    public void xpathForEachStringValue(String xPath, NodeInfo context, InputFormatTypeXml.StringValueHandler handler) {
        xpathForEachStringValue(xPath, XdmValue.wrap(context), handler);
    }

    /**
     * Capture the XML code for the given node.
     * If the value is not a node, its string values is returned.
     *
     * @param node the node to capture
     * @return the XML code for the node
     */
    public String currentNodeXml(XdmValue node) {
        StringBuilder sb = new StringBuilder();
        for (XdmItem item: node) {
            if (item.isNode()) {
                try {
                    sb.append(serializer.serializeNodeToString((XdmNode)item));
                } catch (SaxonApiException e) {
                    throw new ErrorIndexingFile(e);
                }
            } else {
                sb.append(item.getStringValue());
            }
        }
        return sb.toString();
    }

    /**
     * Capture the XML code for the given node.
     *
     * @param xPath   the xpath to capture
     * @param context context to capture it from
     * @return the XML code for the node
     */
    public String xpathXml(String xPath, XdmValue context) {
        return currentNodeXml(find(xPath, context));
    }

    /**
     * return a string representation of an xpath result, using {@link NodeInfo#getStringValue()} or
     * String.valueOf. Handling multiple results should be done in xPath, for example concat.
     *
     * @throws InvalidConfiguration when the xpath returns multiple results
     */
    public String xpathValue(String xPath, XdmValue context) {
        XdmValue list = find(xPath, context);
        try {
            return list.getUnderlyingValue().getStringValue();
        } catch (XPathException e) {
            throw new InvalidConfiguration(String.format("Error getting string value for xpath %s : %s" + xPath, e.getMessage()), e);
        }
    }

    public String xpathValue(String xPath, NodeInfo context) {
        return xpathValue(xPath, XdmItem.wrap(context));
    }
}
