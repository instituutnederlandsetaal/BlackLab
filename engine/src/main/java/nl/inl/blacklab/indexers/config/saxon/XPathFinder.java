package nl.inl.blacklab.indexers.config.saxon;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
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
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmSequenceIterator;
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

    private record ExpressionCacheKey(CompilerCacheKey compiler, String expression) {}

    private record VariableBinding(QName name, XdmValue value) {}

    private record DynamicScope(CompilerCacheKey compilerKey, Map<String, PooledExpression> expressions) {}

    private static class CachedExpression {
        private final XPathExecutable executable;
        private final Map<String, QName> variableNames;

        CachedExpression(XPathExecutable executable, CompilerCacheKey compilerKey) {
            this.executable = executable;
            this.variableNames = compilerKey.varNames().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                    name -> name, name -> variableQName(name, compilerKey.namespaces())));
        }

        QName variableName(String name) {
            QName variableName = variableNames.get(name);
            if (variableName == null)
                throw new IllegalArgumentException("Variable was not declared: " + name);
            return variableName;
        }
    }

    /** Mutable selector state is kept on the document/thread-confined finder, never in the shared cache. */
    private static class PooledExpression {
        private final CachedExpression expression;
        private final ArrayDeque<XPathSelector> availableSelectors = new ArrayDeque<>();

        PooledExpression(CachedExpression expression) {
            this.expression = expression;
        }

        XPathSelector acquire() {
            XPathSelector selector = availableSelectors.pollFirst();
            return selector == null ? expression.executable.load() : selector;
        }

        void release(XPathSelector selector) {
            var controller = selector.getUnderlyingXPathContext().getXPathContextObject().getController();
            if (controller != null)
                controller.clearDocumentPool();
            availableSelectors.addFirst(selector);
        }

        QName variableName(String name) {
            return expression.variableName(name);
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
                fac.declareVariable(variableQName(var, key.namespaces()));
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

    /** Compiled expressions are immutable; mutable selectors are created per active evaluation. */
    private static final LoadingCache<ExpressionCacheKey, CachedExpression> expressionCache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterAccess(Duration.ofMinutes(1))
            .build(key -> {
                XPathCompiler compiler = compilerCache.get(key.compiler());
                synchronized (compiler) {
                    return new CachedExpression(compiler.compile(key.expression()), key.compiler());
                }
            });

    private static QName variableQName(String name, Map<String, String> namespaces) {
        if (name.startsWith("Q{"))
            return QName.fromEQName(name);
        int colon = name.indexOf(':');
        if (colon < 0)
            return new QName(name);
        String prefix = name.substring(0, colon);
        String namespace = prefix.equals(NAMESPACE_XML_PREFIX) ? NAMESPACE_XML_URI : namespaces.get(prefix);
        if (namespace == null)
            throw new InvalidConfiguration("No namespace declared for variable " + name);
        return new QName(prefix, namespace, name.substring(colon + 1));
    }

    private final Map<String, String> namespaces;

    private final CompilerCacheKey staticCompilerKey;

    /** XPathFinder instances are document/thread-confined; only the static caches require synchronization. */
    private final Map<String, PooledExpression> staticExpressions = new HashMap<>();

    private final Map<Set<String>, DynamicScope> dynamicScopes = new HashMap<>();

    private final Serializer serializer;

    /** Variables to make available from XPath */
    private final List<VariableBinding> vars;

    public XPathFinder(Map<String, String> namespaces, Map<String, String> vars) {
        this.namespaces = namespaces == null ? Map.of() : Map.copyOf(namespaces);
        this.vars = vars.entrySet().stream()
                .map(entry -> new VariableBinding(variableQName(entry.getKey(), this.namespaces),
                        new XdmAtomicValue(entry.getValue())))
                .toList();
        staticCompilerKey = new CompilerCacheKey(this.namespaces, vars.keySet());

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
    private XPathResult evaluate(String xpathExpr, XdmValue context, Map<String, XdmValue> dynamicVars)
            throws SaxonApiException {
        PooledExpression expression;
        if (!dynamicVars.isEmpty()) {
            DynamicScope scope = dynamicScopes.get(dynamicVars.keySet());
            if (scope == null) {
                Set<String> dynamicScope = Set.copyOf(dynamicVars.keySet());
                Set<String> variableNames = new HashSet<>(staticCompilerKey.varNames());
                variableNames.addAll(dynamicScope);
                CompilerCacheKey compilerKey = new CompilerCacheKey(namespaces, variableNames);
                scope = new DynamicScope(compilerKey, new HashMap<>());
                dynamicScopes.put(dynamicScope, scope);
            }
            expression = cachedExpression(scope.expressions(), scope.compilerKey(), xpathExpr);
        } else {
            expression = cachedExpression(staticExpressions, staticCompilerKey, xpathExpr);
        }
        XPathSelector selector = expression.acquire();
        for (VariableBinding var: vars)
            selector.setVariable(var.name(), var.value());
        for (Map.Entry<String, XdmValue> var: dynamicVars.entrySet())
            selector.setVariable(expression.variableName(var.getKey()), var.getValue());
        return new XPathResult(xpathExpr, expression, selector, context);
    }

    private static PooledExpression cachedExpression(Map<String, PooledExpression> localCache,
            CompilerCacheKey compilerKey, String xpathExpr) {
        PooledExpression expression = localCache.get(xpathExpr);
        if (expression == null) {
            expression = new PooledExpression(expressionCache.get(new ExpressionCacheKey(compilerKey, xpathExpr)));
            localCache.put(xpathExpr, expression);
        }
        return expression;
    }

    public List<NodeInfo> findNodes(String wordsPath, NodeInfo container) {
        return findNodes(wordsPath, container, false);
    }

    public List<NodeInfo> findNodesStrict(String wordsPath, NodeInfo container) {
        return findNodes(wordsPath, container, true);
    }

    private List<NodeInfo> findNodes(String wordsPath, NodeInfo container, boolean rejectNonNodes) {
        List<NodeInfo> results = new ArrayList<>();
        try (XPathResult result = findLeased(wordsPath, XdmValue.wrap(container))) {
            for (XdmItem item: result) {
                if (item.isNode())
                    results.add(((XdmNode) item).getUnderlyingNode());
                else if (rejectNonNodes)
                    throw new InvalidConfiguration("XPath must return nodes, but returned " +
                            item.getClass().getSimpleName() + "; for xpath " + wordsPath);
                else
                    logger.warn("XPath {} returned non-node: {}", wordsPath, item);
            }
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
        return findLeased(xPath, context);
    }

    public XPathResult findLeased(String xPath, XdmValue context) {
        return findLeased(xPath, context, Map.of());
    }

    public XPathResult findLeased(String xPath, XdmValue context, Map<String, XdmValue> dynamicVars) {
        try {
            return evaluate(xPath, context, dynamicVars);
        } catch (SaxonApiException | RuntimeException e) {
            Throwable cause = e instanceof RuntimeException && e.getCause() instanceof SaxonApiException ? e.getCause() : e;
            Exception exceptionToThrow = (cause instanceof Exception) ? (Exception) cause : new Exception(cause);
            throw new InvalidConfiguration(cause.getMessage() + "; for xpath " + xPath, exceptionToThrow);
        }
    }

    public void xpathForEach(String xPath, NodeInfo context, InputFormatTypeXml.NodeHandler handler) {
        xpathForEach(xPath, context, handler, false);
    }

    public void xpathForEachNodeStrict(String xPath, NodeInfo context, InputFormatTypeXml.NodeHandler handler) {
        xpathForEach(xPath, context, handler, true);
    }

    private void xpathForEach(String xPath, NodeInfo context, InputFormatTypeXml.NodeHandler handler,
            boolean rejectNonNodes) {
        try (XPathResult result = findLeased(xPath, XdmValue.wrap(context))) {
            for (XdmItem item : result) {
                if (item.isNode())
                    handler.handle((NodeInfo) item.getUnderlyingValue());
                else if (rejectNonNodes)
                    throw new InvalidConfiguration("XPath must return nodes, but returned " +
                            item.getClass().getSimpleName() + "; for xpath " + xPath);
            }
        }
    }

    public void xpathForEach(String xPath, XdmValue context, InputFormatTypeXml.XdmValueHandler handler) {
        try (XPathResult result = findLeased(xPath, context)) {
            for (XdmItem item : result)
                handler.handle(item);
        }
    }

    public void xpathForEachStringValue(String xPath, XdmValue context, InputFormatTypeXml.StringValueHandler handler) {
        try (XPathResult result = findLeased(xPath, context)) {
            for (XdmItem item : result)
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
        return xpathValue(xPath, context, Map.of());
    }

    public String xpathValue(String xPath, XdmValue context, Map<String, XdmValue> dynamicVars) {
        StringBuilder result = new StringBuilder();
        try (XPathResult evaluation = findLeased(xPath, context, dynamicVars)) {
            for (XdmItem item : evaluation) {
                if (!item.isNode() && !item.isAtomicValue())
                    throw new InvalidConfiguration("XPath string value cannot be taken from " +
                            item.getClass().getSimpleName() + "; for xpath " + xPath);
                result.append(item.getUnderlyingValue().getStringValue());
            }
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
    public static class XPathResult implements Iterable<XdmItem>, java.util.Iterator<XdmItem>, AutoCloseable {
        private final String expression;
        private final PooledExpression pooledExpression;
        private final XPathSelector selector;
        private XdmSequenceIterator<XdmItem> contextIterator;
        private XdmSequenceIterator<XdmItem> resultIterator;

        private XPathResult(String expression, PooledExpression pooledExpression, XPathSelector selector,
                XdmValue context) {
            this.expression = expression;
            this.pooledExpression = pooledExpression;
            this.selector = selector;
            contextIterator = context.iterator();
        }

        @Override
        public java.util.Iterator<XdmItem> iterator() {
            // XPath results are controlled single-pass iterables. Re-iteration would also violate selector leasing.
            return this;
        }

        @Override
        public boolean hasNext() {
            if (contextIterator == null)
                return false;
            try {
                while (true) {
                    if (resultIterator != null && resultIterator.hasNext())
                        return true;
                    if (resultIterator != null) {
                        resultIterator.close();
                        resultIterator = null;
                    }
                    if (contextIterator.hasNext()) {
                        selector.setContextItem(contextIterator.next());
                        resultIterator = selector.iterator();
                        continue;
                    }
                    close();
                    return false;
                }
            } catch (SaxonApiException | RuntimeException e) {
                closeAfterFailure(e);
                throw new InvalidConfiguration(e.getMessage() + "; for xpath " + expression, e);
            }
        }

        @Override
        public XdmItem next() {
            if (!hasNext())
                throw new NoSuchElementException();
            try {
                return resultIterator.next();
            } catch (RuntimeException e) {
                closeAfterFailure(e);
                if (e instanceof NoSuchElementException)
                    throw e;
                throw new InvalidConfiguration(e.getMessage() + "; for xpath " + expression, e);
            }
        }

        @Override
        public void close() {
            close(true);
        }

        private void close(boolean reusable) {
            if (contextIterator == null)
                return;
            RuntimeException failure = null;
            try {
                if (resultIterator != null)
                    resultIterator.close();
            } catch (RuntimeException e) {
                failure = e;
            }
            try {
                contextIterator.close();
            } catch (RuntimeException e) {
                if (failure == null)
                    failure = e;
                else
                    failure.addSuppressed(e);
            }
            resultIterator = null;
            contextIterator = null;
            // All context and exact-scope variables are rebound before reuse. The pool dies with this document.
            if (failure == null && reusable)
                pooledExpression.release(selector);
            if (failure != null)
                throw failure;
        }

        private void closeAfterFailure(Throwable failure) {
            try {
                close(false);
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
    }

}
