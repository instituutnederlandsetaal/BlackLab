package nl.inl.blacklab.indexers.config.saxon;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
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

    /**
     * Cache of XPathCompiler instances, keyed by namespace configuration.
     * Static so it can be shared across all XPathFinder instances (i.e. across documents).
     * Creating XPathCompilers is slow, so we want to reuse them.
     */
    private static final LoadingCache<Map<String, String>, XPathCompiler> compilerCache = CacheBuilder.newBuilder()
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

    /**
     * Cache of XPathSelector instances per XPathCompiler, with thread-local storage.
     * XPathSelector.load() is slow, but the resulting selectors are reusable across documents
     * and variable values. We cache them per-thread (since they hold mutable state like context)
     * and per-compiler (since they're tied to the compiler's namespace/variable configuration).
     */
    private static final LoadingCache<XPathCompiler, ThreadLocal<Map<String, XPathSelector>>> selectorCache =
            CacheBuilder.newBuilder()
                    .weakKeys() // allow GC of XPathCompiler instances
                    .build(new CacheLoader<XPathCompiler, ThreadLocal<Map<String, XPathSelector>>>() {
                        @Override
                        public ThreadLocal<Map<String, XPathSelector>> load(XPathCompiler key) {
                            return ThreadLocal.withInitial(HashMap::new);
                        }
                    });

    private final XPathCompiler xPath;

    private final Serializer serializer;

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
     * Compile XPath expression and get a selector for it.
     * <p>
     * XPathSelector instances are cached per-thread and per-compiler, since:
     * - load() is slow but selectors are reusable across documents
     * - selectors hold mutable state (context item) so need to be per-thread
     * - selectors are tied to the compiler's namespace configuration
     *
     * @param xpathExpr the xpath expression
     * @return the compiled expression selector
     */
    private XPathSelector acquireExpression(String xpathExpr) throws SaxonApiException {
        try {
            Map<String, XPathSelector> selectors = selectorCache.get(xPath).get();
            return selectors.computeIfAbsent(xpathExpr, expr -> {
                try {
                    return xPath.compile(expr).load();
                } catch (SaxonApiException e) {
                    throw new InvalidConfiguration(e.getMessage() + "; for xpath " + xPath, e);
                }
            });
        } catch (Exception e) {
            throw new SaxonApiException(e);
        }
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
     */
    public Iterable<XdmItem> find(String xPath, XdmValue context) {
        try {
            XPathSelector selector = acquireExpression(xPath);
            // Return an iterable that iterates over each context item, and for each context item,
            // iterates over the results of the XPath evaluation.
            // This avoids creating a new XdmValue for each result, which is expensive.
            return new XpathResultIterator(selector, context);
        } catch (SaxonApiException | RuntimeException e) {
            // Unwrap RuntimeException from XpathResultIterator if it wraps a SaxonApiException
            Throwable cause = e instanceof RuntimeException && e.getCause() instanceof SaxonApiException ? e.getCause() : e;
            Exception exceptionToThrow = (cause instanceof Exception) ? (Exception) cause : new Exception(cause);
            throw new InvalidConfiguration(cause.getMessage() + "; for xpath " + xPath, exceptionToThrow);
        }
    }

    public void xpathForEach(String xPath, XdmValue context, DocIndexerXPath.NodeHandler<XdmItem> handler) {
        for (XdmItem item: find(xPath, context)) {
            handler.handle(item);
        }
    }

    public void xpathForEachStringValue(String xPath, XdmValue context, DocIndexerXPath.StringValueHandler handler) {
        for (XdmItem item: find(xPath, context)) {
            handler.handle(item.getStringValue());
        }
    }

    /**
     * Capture the XML code for the given node.
     *
     * @param item the item to capture
     * @return the XML code for the node
     */
    public String currentNodeXml(XdmItem item) {
        if (item.isNode()) {
            try {
                return serializer.serializeNodeToString((XdmNode) item);
            } catch (SaxonApiException e) {
                throw new ErrorIndexingFile(e);
            }
        } else {
            throw new ErrorIndexingFile("XPath matched non-NodeInfo; cannot convert to XML: " + xPath);
        }
    }



    /**
     * Capture the XML code for the given node.
     *
     * @param xPath   the xpath to capture
     * @param context context to capture it from
     * @return the XML code for the node
     */
    public String xpathXml(String xPath, XdmValue context) {
        Iterator<XdmItem> it = find(xPath, context).iterator();
        if (!it.hasNext())
            return "";
        XdmItem item = it.next();
        if (it.hasNext()) {
            // Collect remaining items for error message
            List<String> items = new ArrayList<>();
            items.add(item instanceof XdmNode ? ((XdmNode) item).getUnderlyingNode().toShortString() : String.valueOf(item));
            while (it.hasNext()) {
                XdmItem next = it.next();
                items.add(next instanceof XdmNode ? ((XdmNode) next).getUnderlyingNode().toShortString() : String.valueOf(next));
            }
            throw new InvalidConfiguration(
                    String.format(
                            "list %s contains multiple values, change your xpath %s to return one result",
                            items, xPath));
        }
        return currentNodeXml(item);
    }

    /**
     * return a string representation of an xpath result, using {@link NodeInfo#getStringValue()} or
     * String.valueOf. Handling multiple results should be done in xPath, for example concat.
     */
    public String xpathValue(String xPath, XdmValue context) {
        StringBuilder result = new StringBuilder();
        for (XdmItem item : find(xPath, context)) {
            result.append(item.getStringValue());
        }
        return result.toString();
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
        }
    }
}
