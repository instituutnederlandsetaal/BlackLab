package nl.inl.blacklab.indexers.config.saxon;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import nl.inl.blacklab.exceptions.ErrorIndexingFile;
import nl.inl.blacklab.exceptions.InvalidConfiguration;
import nl.inl.blacklab.indexers.config.InputFormatTypeXml;

public class XPathFinder {

    private static final Logger logger = LogManager.getLogger(XPathFinder.class);

    /** Prefix for the implicitly declared xml namespace */
    public static final String NAMESPACE_XML_PREFIX = "xml";

    /** URI for the implicitly declared xml namespace */
    public static final String NAMESPACE_XML_URI = "http://www.w3.org/XML/1998/namespace";

    /**
     * Cache key for XPathCompiler instances. Includes namespaces and variable names
     * (but not values, since values can change while the compiler can be reused).
     */
    private record CompilerCacheKey(Map<String, String> namespaces, Set<String> varNames) {
        CompilerCacheKey {
            // Make defensive copies to ensure immutability
            namespaces = namespaces == null ? Map.of() : Map.copyOf(namespaces);
            varNames = varNames == null ? Set.of() : Set.copyOf(varNames);
        }
    }

    /**
     * Cache of XPathCompiler instances.
     * Static so it can be shared across all XPathFinder instances (i.e. across documents).
     * Creating XPathCompilers is slow, so we want to reuse them.
     */
    private static final LoadingCache<CompilerCacheKey, XPathCompiler> compilerCache = Caffeine.newBuilder()
        .maximumSize(50) // should be large enough for most use cases?
        .expireAfterAccess(Duration.ofMinutes(1))
        .build(key -> {
            var fac = SaxonHelper.newXPathFactory();
            fac.setCaching(true);

            for (String var: key.varNames()) {
                fac.declareVariable(new QName(var));
            }
            // xml namespace is implicit
            fac.declareNamespace(NAMESPACE_XML_PREFIX, NAMESPACE_XML_URI);
            Map<String, String> namespaces = key.namespaces();
            boolean hasNamespaces = false;
            if (!namespaces.isEmpty()) {
                for (Map.Entry<String, String> e: namespaces.entrySet()) {
                    if (e.getKey().equals(NAMESPACE_XML_PREFIX)) {
                        if (!e.getValue().equals(NAMESPACE_XML_URI))
                            logger.warn("Tried to redefine implicit 'xml' namespace prefix to '" + e.getValue()
                                    + "'); ignoring");
                        continue;
                    }
                    // Don't use namespace-aware matching if only the xml namespace is defined
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

    /**
     * Cache of XPathSelector instances per XPathCompiler, with thread-local storage.
     * XPathSelector.load() is slow, but the resulting selectors are reusable across documents
     * and variable values. We cache them per-thread, since they're not thread-safe, 
     * but we do cache them, as they are perfectly fine to reuse across documents,
     * as long as the underlying namespaces and variable declarations
     * are the same (which they are, since they're tied to the XPathCompiler).
     */
    private static final LoadingCache<XPathCompiler, ThreadLocal<Map<String, XPathSelector>>> selectorCache =
            Caffeine.newBuilder()
                    // allow GC of XPathCompiler instances
                    .weakKeys() 
                    // map is ThreadLocal, so no need for concurrent map
                    .build(key -> ThreadLocal.withInitial(HashMap::new)); 

    private final XPathCompiler xPath;

    private final Serializer serializer;

    /** Variables to make available from XPath */
    private final Map<String, String> vars = new HashMap<>();

    public XPathFinder(Map<String, String> namespaces, Map<String, String> vars) {
        this.vars.putAll(vars);

        try {
            this.xPath = compilerCache.get(new CompilerCacheKey(namespaces, this.vars.keySet()));
        } catch (Exception e) {
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
        Map<String, XPathSelector> selectors = selectorCache.get(xPath).get();
        XPathSelector selector = selectors.get(xpathExpr);
        if (selector == null) {
            selector = xPath.compile(xpathExpr).load();
            selectors.put(xpathExpr, selector);
        }
        // Always set variables since values may have changed between documents
        for (Map.Entry<String, String> var : vars.entrySet()) {
            selector.setVariable(new QName(var.getKey()), new XdmAtomicValue(var.getValue()));
        }
        return selector;
    }

    public List<NodeInfo> findNodes(String wordsPath, NodeInfo container) {
        List<NodeInfo> results = new ArrayList<>();
        for (XdmItem item: find(wordsPath, XdmValue.wrap(container))) {
            if (item.isNode())
                results.add(((XdmNode) item).getUnderlyingNode());
            else
                logger.warn("XPath {} returned non-node: {}", wordsPath, item);
        }
        return results;
    }

    /**
     * Find results in a context, return an iterable.
     *
     * @param xPath the xpath expression
     * @param context the context to evaluate the xpath in
     * @return the results
     */
    public Iterable<XdmItem> find(String xPath, XdmValue context) {
        try {
            XPathSelector selector = acquireExpression(xPath);
            return new XpathResultIterator(selector, context);
        } catch (SaxonApiException | RuntimeException e) {
            Throwable cause = e instanceof RuntimeException && e.getCause() instanceof SaxonApiException ? e.getCause() : e;
            Exception exceptionToThrow = (cause instanceof Exception) ? (Exception) cause : new Exception(cause);
            throw new InvalidConfiguration(cause.getMessage() + "; for xpath " + xPath, exceptionToThrow);
        }
    }

    public void xpathForEach(String xPath, NodeInfo context, InputFormatTypeXml.NodeHandler handler) {
        for (XdmItem item : find(xPath, XdmValue.wrap(context))) {
            if (item.isNode()) {
                handler.handle((NodeInfo) item.getUnderlyingValue());
            }
        }
    }

    public void xpathForEach(String xPath, XdmValue context, InputFormatTypeXml.XdmValueHandler handler) {
        for (XdmItem item : find(xPath, context))
            handler.handle(item);
    }

    public void xpathForEachStringValue(String xPath, XdmValue context, InputFormatTypeXml.StringValueHandler handler) {
        for (XdmItem item : find(xPath, context))
            handler.handle(item.getStringValue());
    }

    public void xpathForEachStringValue(String xPath, NodeInfo context, InputFormatTypeXml.StringValueHandler handler) {
        xpathForEachStringValue(xPath, XdmValue.wrap(context), handler);
    }

    /**
     * Capture the XML code for the given node.
     * If the value is not a node, its string values is returned.
     *
     * @param node the node to serialize
     * @return the XML code for the node
     */
    public String currentNodeXml(NodeInfo node) {
        try {
            return serializer.serializeNodeToString(new XdmNode(node));
        } catch (SaxonApiException e) {
            throw new ErrorIndexingFile("Error serializing XML for node: " + node.getDisplayName(), e);
        }
    }

    /**
     * return a string representation of an xpath result, using {@link NodeInfo#getStringValue()} or
     * String.valueOf. Handling multiple results should be done in xPath, for example concat.
     *
     * @throws InvalidConfiguration when the xpath returns multiple results
     */
    public String xpathValue(String xPath, XdmValue context) {
        StringBuilder result = new StringBuilder();
        for (XdmItem item : find(xPath, context)) {
            result.append(item.getUnderlyingValue().getStringValue());
        }
        return result.toString();
    }

    public String xpathValue(String xPath, NodeInfo context) {
        return xpathValue(xPath, XdmValue.wrap(context));
    }

    /**
     * Testing revealed that the using iterators to retrieve xpath results from Saxon is significantly faster than
     * other approaches.
     * Since this class is a major hot path in indexing, we use iterators to extract results from Saxon.
     * The difference isn't world-changing, but we can speed up the *entire* indexing process by something like 20%
     * by using iterators vs the more fluid evaluate() approach.
     */
     private static class XpathResultIterator implements Iterable<XdmItem> {
        XPathSelector selector;
        Iterator<XdmItem> ctxIt;
        Iterator<XdmItem> resultIt;

        public XpathResultIterator(final XPathSelector selector, final XdmValue context) {
            this.selector = selector;
            this.ctxIt = context.iterator();
            this.resultIt = Collections.emptyIterator();
        }
        
        @Override
        public Iterator<XdmItem> iterator() {
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    try {
                        while (true) {
                            if (resultIt != null && resultIt.hasNext())
                                return true;
                            if (ctxIt.hasNext()) {
                                selector.setContextItem(ctxIt.next());
                                resultIt = selector.iterator();
                                continue;
                            }
                            return false;
                        }
                    } catch (SaxonApiException e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public XdmItem next() {
                    return resultIt.next(); // assume it will throw if no next.
                }
            };
        };
    }
}
