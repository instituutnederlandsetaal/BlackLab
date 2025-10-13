package nl.inl.blacklab.indexers.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ximpleware.AutoPilot;
import com.ximpleware.NavException;
import com.ximpleware.VTDException;
import com.ximpleware.VTDNav;
import com.ximpleware.XPathEvalException;
import com.ximpleware.XPathParseException;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.exceptions.ErrorIndexingFile;
import nl.inl.blacklab.exceptions.InvalidInputFormatConfig;
import nl.inl.blacklab.exceptions.MalformedInputFile;
import nl.inl.blacklab.indexers.config.DocIndexerVTD.FragmentPosition;
import nl.inl.blacklab.indexers.config.DocIndexerXPath.NodeHandler;

/**
 * Manages compiled XPaths and finding stuff in the document
 */
class XpathFinderVTD {

    protected static final Logger logger = LogManager.getLogger(XpathFinderVTD.class);

    /** Did we log a warning about a possible XPath issue? If so, don't keep warning again and again. */
    private static boolean warnedAboutXpathIssue = false;

    private static final String XPATH_CURRENT_NODE = ".";

    private final VTDNav nav;

    /**
     * For evaluating the current node to string (reusable, no reset needed)
     */
    private final AutoPilot apCurrentNode;

    /**
     * Where the current position is relative to the current fragment
     */
    private FragmentPosition fragPos = FragmentPosition.BEFORE_OPEN_TAG;

    /**
     * Fragment positions in ancestors
     */
    private final List<FragmentPosition> fragPosStack = new ArrayList<>();

    private final Map<String, String> namespaces;

    /**
     * Map from XPath expression to compiled XPath.
     */
    private final Map<String, AutoPilot> compiledXPaths = new HashMap<>();

    /**
     * AutoPilots that are currently being used. We need to keep track of this to be
     * able to re-add them to compiledXpath with the correct XPath expression later.
     */
    private final Map<AutoPilot, String> autoPilotsInUse = new HashMap<>();

    public XpathFinderVTD(VTDNav nav, Map<String, String> namespaces) {
        this.nav = nav;
        this.namespaces = namespaces == null ? Collections.emptyMap() : namespaces;
        if (nav.getEncoding() != VTDNav.FORMAT_UTF8)
            throw new UnsupportedOperationException(
                    "DocIndexerXPath only supports UTF-8 input, but document was parsed as " + nav.getEncoding()
                            + " (See VTD-XML's VTDNav.java for format codes)");
        apCurrentNode = acquireExpression(XPATH_CURRENT_NODE);
    }

    public void navpush() {
        nav.push();
        fragPosStack.add(fragPos);
        fragPos = FragmentPosition.BEFORE_OPEN_TAG;
    }

    public void navpop() {
        nav.pop();
        fragPos = fragPosStack.remove(fragPosStack.size() - 1);
    }

    /**
     * Create AutoPilot and declare namespaces on it.
     *
     * @param xpathExpr xpath expression for the AutoPilot
     * @return the AutoPilot
     */
    public AutoPilot acquireExpression(String xpathExpr) {
        AutoPilot ap = compiledXPaths.remove(xpathExpr);
        if (ap == null) {
            ap = new AutoPilot(nav);
            if (!namespaces.isEmpty()) {
                ap.declareXPathNameSpace("xml", "http://www.w3.org/XML/1998/namespace"); // builtin
                for (Entry<String, String> e: namespaces.entrySet()) {
                    ap.declareXPathNameSpace(e.getKey(), e.getValue());
                }
            }
            try {
                ap.selectXPath(xpathExpr);
            } catch (XPathParseException e) {
                throw new InvalidInputFormatConfig("Error in XPath expression " + xpathExpr + " : " + e.getMessage(),
                        e);
            }
        } else {
            // We always reset this to be safe, even though it's only needed if the same xpath was previously used in
            // a different context (e.g. @type might be used in different parts of the document).
            ap.resetXPath();
        }
        autoPilotsInUse.put(ap, xpathExpr);
        return ap;
    }

    public void releaseExpression(AutoPilot ap) {
        String xpathExpr = autoPilotsInUse.remove(ap);
        compiledXPaths.put(xpathExpr, ap);
    }

    public void xpathForEach(String xPath, VTDNav context, NodeHandler<VTDNav> handler) {
        assert context == null || context == nav;
        navpush();
        AutoPilot results = acquireExpression(xPath);
        try {
            while (results.evalXPath() != -1)
                handler.handle(context);
        } catch (VTDException e) {
            // @@@ catch and add documentname to message?
            throw new MalformedInputFile("Error indexing file, XPath = " + xPath, e);
        } finally {
            releaseExpression(results);
            navpop();
        }
    }

    public void xpathForEachStringValue(String xPath, VTDNav context, DocIndexerXPath.StringValueHandler handler) {
        assert context == null || context == nav;
        navpush();
        AutoPilot results = acquireExpression(xPath);
        try {
            while (results.evalXPath() != -1) {
                String value = currentNodeToString(context);
                handler.handle(value);
            }
        } catch (XPathEvalException e) {
            // An xpath like string(@value) will make evalXPath() fail.
            // There is no good way to check whether this exception will occur
            // When the exception occurs we try to evaluate the xpath as string
            // NOTE: an xpath with dot like: string(.//tei:availability[1]/@status='free') may fail silently!!
            if (logger.isDebugEnabled() && !warnedAboutXpathIssue) {
                warnedAboutXpathIssue = true;
                logger.debug(String.format(
                        "An xpath with a dot like %s may fail silently and may have to be replaced by one like %s",
                        "string(.//tei:availability[1]/@status='free')",
                        "string(//tei:availability[1]/@status='free')"));
            }
            String value = results.evalXPathToString();
            handler.handle(value);
        } catch (VTDException e) {
            // @@@ catch and add documentname to message?
            throw new MalformedInputFile("Error indexing file, XPath = " + xPath, e);
        } finally {
            releaseExpression(results);
            navpop();
        }
    }

    public String xpathValue(String xpath, VTDNav context) {
        assert context == null || context == nav;
        // Resolve value using XPath
        AutoPilot apLinkPath = acquireExpression(xpath);
        String result = apLinkPath.evalXPathToString();
        releaseExpression(apLinkPath);
        return result;
    }

    public String xpathXml(String valuePath, VTDNav context) {
        assert context == null || context == nav;
        navpush();
        AutoPilot apValuePath = acquireExpression(valuePath);
        try {
            return xpathXml(apValuePath);
        } finally {
            releaseExpression(apValuePath);
            navpop();
        }
    }

    public String currentNodeXml(VTDNav context) {
        assert context == null || context == nav;
        try {
            long frag = nav.getContentFragment();
            if (frag == -1)
                return "";
            int offset = (int) frag;
            int length = (int) (frag >> 32);
            return nav.toRawString(offset, length);
        } catch (VTDException e) {
            throw new ErrorIndexingFile(e);
        }
    }

    public String xpathXml(AutoPilot apValuePath) {
        try {
            if (apValuePath.evalXPath() == -1)
                return "";
            return currentNodeXml(nav);
        } catch (VTDException e) {
            throw new ErrorIndexingFile(e);
        }
    }

    public void setFragPos(FragmentPosition fragPos) {
        this.fragPos = fragPos;
    }

    public FragmentPosition getFragPos() {
        return fragPos;
    }

    public VTDNav getNav() {
        return nav;
    }

    /**
     * Gets attribute map for current element
     */
    public Map<String, List<String>> getAttributes() {
        navpush();
        AutoPilot apAttr = new AutoPilot(nav);
        apAttr.selectAttr("*");
        int i;
        Map<String, List<String>> attr = new HashMap<>();
        try {
            while ((i = apAttr.iterateAttr()) != -1) {
                String name = nav.toString(i);
                name = name.replaceAll("^\\w+:", ""); // remove namespace prefix
                String value = nav.toString(i + 1);
                attr.put(name, List.of(value));
            }
        } catch (NavException e) {
            throw BlackLabException.wrapRuntime(e);
        }
        navpop();
        return attr;
    }

    public int getCurrentByteOffset() {
        try {
            FragmentPosition fragPos = getFragPos();
            if (fragPos == FragmentPosition.BEFORE_OPEN_TAG || fragPos == FragmentPosition.AFTER_CLOSE_TAG) {
                long elFrag = nav.getElementFragment();
                int elOffset = (int) elFrag;
                if (fragPos == FragmentPosition.AFTER_CLOSE_TAG) {
                    int elLength = (int) (elFrag >> 32);
                    return elOffset + elLength;
                }
                return elOffset;
            }
            long contFrag = nav.getContentFragment();
            int contOffset = (int) contFrag;
            if (fragPos == FragmentPosition.BEFORE_CLOSE_TAG) {
                int contLength = (int) (contFrag >> 32);
                return contOffset + contLength;
            }
            return contOffset;
        } catch (NavException e) {
            throw new ErrorIndexingFile(e);
        }
    }

    public String currentNodeToString(VTDNav context) {
        assert context == null || context == nav;
        return apCurrentNode.evalXPathToString();
    }
}
