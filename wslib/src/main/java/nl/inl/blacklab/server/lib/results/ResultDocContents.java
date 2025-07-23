package nl.inl.blacklab.server.lib.results;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.document.Document;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.DocUtil;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.exceptions.NotAuthorized;
import nl.inl.blacklab.server.exceptions.NotFound;
import nl.inl.blacklab.server.lib.WebserviceParams;
import nl.inl.blacklab.server.util.BlsUtils;

/**
 * Get (part of) the original contents of a document.
 */
public class ResultDocContents {
    static final Logger logger = LogManager.getLogger(ResultDocContents.class);

    public static final Pattern XML_DECL = Pattern.compile("^\\s*<\\?xml\\s+version\\s*=\\s*([\"'])\\d\\.\\d\\1" +
            "(?:\\s+encoding\\s*=\\s*([\"'])[A-Za-z][A-Za-z0-9._-]*\\2)?" +
            "(?:\\s+standalone\\s*=\\s*([\"'])(?:yes|no)\\3)?\\s*\\?>\\s*");

    /**
     * xmlns:namespace="...." on root
     */
    public static final Pattern NAMED_NAMESPACE = Pattern.compile(" xmlns:[^=]+=\"[^\"]+\"");

    /**
     * xmls="" on root
     */
    public static final Pattern ANON_NAMESPACE = Pattern.compile("xmlns=\"([^ ]+)\"");

    /**
     * an xml namespace prefix (for collecting all of the prefixes used in the document)
     */
    public static final Pattern NAMESPACE_PREFIX = Pattern.compile(
            "<([a-z]+):[^ ]+ |<([a-z]+):[^>]+>| ([a-z]+):[^=]+=\"");

    private final WebserviceParams params;

    private final String docPid;

    /**
     * was the full document requested?
     */
    private boolean isFullDocument;

    /**
     * Should an XML declaration be prepended to the results?
     */
    private boolean mustOutputXmlDeclaration;

    private String content;

    private Set<String> namespaces;

    private Set<String> anonNamespaces;

    ResultDocContents(WebserviceParams params) throws BlsException, InvalidQuery {
        this.params = params;
        this.docPid = params.getDocPid();
        getDocContents();
    }

    public boolean isFullDocument() {
        return isFullDocument;
    }

    public boolean needsXmlDeclaration() {
        return mustOutputXmlDeclaration;
    }

    public String getContent() {
        return content;
    }

    public Set<String> getNamespaces() {
        return namespaces;
    }

    public Set<String> getAnonNamespaces() {
        return anonNamespaces;
    }

    private void getDocContents() throws BlsException, InvalidQuery {
        if (docPid.isEmpty())
            throw new BadRequest("NO_DOC_ID", "Specify document pid.");

        isFullDocument = false;
        int startAtWord = params.getWordStart();
        int endAtWord = params.getWordEnd();
        if (startAtWord < -1 || endAtWord < -1 || (endAtWord >= 0 && endAtWord <= startAtWord)) {
            // Illegal value. Error will be thrown.
            throw new BadRequest("ILLEGAL_BOUNDARIES", "Illegal word boundaries specified. Please check parameters.");
        } else {
            if (startAtWord == -1 && endAtWord == -1) {
                // Full document; no need for another root element
                isFullDocument = true;
            }
        }

        BlackLabIndex index = params.blIndex();
        int docId = BlsUtils.getDocIdFromPid(index, docPid);
        if (!index.docExists(docId))
            throw new NotFound("DOC_NOT_FOUND", "Document with pid '" + docPid + "' not found.");
        Document document = index.luceneDoc(docId);
        if (document == null)
            throw new InternalServerError("Couldn't fetch document with pid '" + docPid + "'.",
                    "INTERR_FETCHING_DOCUMENT_CONTENTS");
        if (!index.mayView(document))
            throw new NotAuthorized(
                    "Viewing the full contents of this document is not allowed. For more information, read about 'contentViewable': https://blacklab.ivdnt.org/how-to-configure-indexing.html.");

        Hits hits = null;
        if (params.hasPattern()) {
            hits = params.hitsSample().execute();
        }

        // Note: we use the highlighter regardless of whether there's hits because
        // it makes sure our document fragment is well-formed.
        Hits hitsInDoc;
        AnnotatedField fieldToShow = params.getAnnotatedField();
        if (hits == null) {
            hitsInDoc = Hits.empty(QueryInfo.create(index, fieldToShow));
        } else {
            hitsInDoc = hits.getHitsInDoc(docId);
        }
        if (isFullDocument) {
            // Whole document. Use the highlightDocument method, which takes document versions in
            // a parallel corpus into account (cuts out part of the original input file).
            AnnotatedField field;
            field = hits == null ? fieldToShow : hits.queryInfo().field();
            content = DocUtil.highlightDocument(index, field, docId, hitsInDoc);
        } else {
            // Part of the document by token positions.
            content = DocUtil.highlightContent(index, docId, hitsInDoc, startAtWord, endAtWord);
        }

        // Make sure we have exactly one XML declaration
        Matcher m = XML_DECL.matcher(content);
        boolean hasXmlDeclaration = m.find();
        mustOutputXmlDeclaration = false;
        if (hasXmlDeclaration && !isFullDocument) {
            // We don't want another XML declaration; strip it
            content = content.substring(m.end());
        } else if (!hasXmlDeclaration && isFullDocument) {
            // Make sure there's an XML declaration
            mustOutputXmlDeclaration = true;
        }

        if (!isFullDocument) {
            // Fix namespaces for partial documents.

            Matcher cm = NAMESPACE_PREFIX.matcher(content);
            Set<String> prefixes = new HashSet<>(2);
            while (cm.find()) {
                // collect unique prefixes that need to be bound
                String prefix = cm.group(1) == null ? cm.group(2) == null ? cm.group(3) : cm.group(2) : cm.group(1);
                if (!prefixes.contains(prefix) && !prefix.equals("xml")) { // ignore special xml prefix
                    prefixes.add(prefix);
                }
            }
            // here we may need to include namespace declarations
            // retrieve the first bit of the document, try to find namespaces
            String root = DocUtil.contentsByCharPos(index, docId, document,
                    fieldToShow, 0, 1024);
            m = NAMED_NAMESPACE.matcher(root);
            namespaces = new HashSet<>();
            while (m.find()) {
                //collect namespaces that bind prefixes
                namespaces.add(m.group());
            }
            // see if a prefix isn't bound
            if (prefixes.stream().noneMatch(s -> namespaces.stream().anyMatch(s1 -> s1.startsWith(" xmlns:" + s)))) {
                String msg = String.format(
                        "some namespace prefixes (%s) in doc %s are not declared on the document root element, only %s.",
                        prefixes, docPid, namespaces);
                logger.warn(msg);
                //throw new InternalServerError(msg);
            }
            // Handle any anonymous namespace on the root
            m = ANON_NAMESPACE.matcher(root);
            anonNamespaces = new HashSet<>();
            if (m.find()) {
                anonNamespaces.add(m.group());
            }
        }
    }
}
