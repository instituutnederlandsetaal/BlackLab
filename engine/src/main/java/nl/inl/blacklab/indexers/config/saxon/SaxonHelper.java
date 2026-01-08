package nl.inl.blacklab.indexers.config.saxon;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.util.Optional;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;

import org.apache.commons.io.IOUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import net.sf.saxon.Configuration;
import net.sf.saxon.lib.ResourceRequest;
import net.sf.saxon.om.TreeInfo;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.trans.XPathException;
import nl.inl.blacklab.exceptions.ErrorIndexingFile;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.plugins.IndexSourceType;
import nl.inl.blacklab.plugins.PluginManager;
import nl.inl.util.ObjectCache;
import nl.inl.util.fileprocessor.FileIterator;
import nl.inl.util.fileprocessor.FileReference;

/**
 * A helper for indexing using Saxon.
 */
public class SaxonHelper {

    static Processor saxonProcessor = new Processor(false);

    static {
        // Custom "URI resolver" (actually resource resolver) for doc() function
        saxonProcessor.getUnderlyingConfiguration().setResourceResolver(SaxonHelper::resolve);
    }

    /** Cache contents of linked documents for a short time, so repeated references to the same document are fast */
    private static ObjectCache<String, String> uriContentsCache;

    public static final int CACHE_CONTENTS_SEC = 600;

    public static final int CACHE_CONTENTS_NUM = 10;

    static {
        uriContentsCache = new ObjectCache<>(SaxonHelper::getContentsForUri, s -> {},
                CACHE_CONTENTS_NUM, CACHE_CONTENTS_SEC);
    }

    private static String getContentsForUri(String uri) {
        try {
            URI theUri = URI.create(uri);
            String scheme = theUri.getScheme();
            // Interpret the URI as an IndexSource (the same way IndexTool does)
            Optional<IndexSourceType> indexSourceType = PluginManager.type(IndexSourceType.class)
                    .getIfExists(scheme); // ensure plugins are loaded.
            if (indexSourceType.isEmpty()) {
                // Not a known IndexSourceType scheme; let Saxon handle it
                return null;
            }
            String restOfUri = uri.substring(scheme.length() + 1);
            FileIterator fileIt = indexSourceType.get().get(restOfUri).filesToIndex();
            FileReference file = fileIt.next();
            if (file == FileReference.DUMMY)
                throw new IllegalArgumentException("doc() URI resolves to FileReference.DUMMY: " + uri);
            if (fileIt.hasNext()) // must match single file
                throw new IllegalArgumentException("doc() URI matches multiple files: " + uri);
            // Read the file and return its contents as a StreamSource
            StringReader reader;
            try (BufferedReader r = file.getSinglePassReader()) {
                return IOUtils.toString(r);
            } catch (IOException e) {
                throw new ErrorIndexingFile("Error reading linked file: " + uri, e);
            }
        } catch (PluginException e) {
            throw new ErrorIndexingFile("Error with linked file: " + uri, e);
        }
    }

    private static Source resolve(ResourceRequest req) {
        String contents = uriContentsCache.acquire(req.uri);
        uriContentsCache.releaseObject(contents); // we can release it right away, it's just a string reference
        StringReader reader = new StringReader(contents);
        try {
            // reader will never be closed but that's okay
            CharPosTrackingReader charPositions = new CharPosTrackingReader(reader);
            return getSaxSource(charPositions, true); // namespace-aware configurable?
        } catch (ParserConfigurationException | SAXException e) {
            throw new ErrorIndexingFile(e);
        }
    }

    private static SAXSource getSaxSource(CharPosTrackingReader charPositions, boolean namespaceAware)
            throws ParserConfigurationException, SAXException {
        CharPosTrackingContentHandler handler = new CharPosTrackingContentHandler(charPositions);
        XMLReader trackingReader = getXmlReader(handler, namespaceAware);
        InputSource inputSrc = new InputSource(charPositions);
        return new SAXSource(trackingReader, inputSrc);
    }

    private static XMLReader getXmlReader(ContentHandler handler, boolean namespaceAware)
            throws ParserConfigurationException, SAXException {
        // make sure our content handler doesn't get overwritten by saxon
        SAXParserFactory parserFactory = SAXParserFactory.newInstance();
        parserFactory.setXIncludeAware(true);
        if (!namespaceAware) {
            // FIXME: this doesn't seem to work; Saxon still sees the namespaces?
            //   (we need to call XPathCompiler::setUnprefixedElementMatchingPolicy(UnprefixedElementMatchingPolicy.ANY_NAMESPACE,
            //    but how do we get the XPathCompiler here?)
            parserFactory.setNamespaceAware(false);
        }
        SAXParser parser = parserFactory.newSAXParser();
        XMLReader xmlReader = parser.getXMLReader();
        xmlReader.setContentHandler(handler);
        return new CharPosTrackingXMLReader(xmlReader);
    }

    private SaxonHelper() {}

    public static XPathCompiler newXPathFactory() {
        return saxonProcessor.newXPathCompiler();
    }

    /** Parse the document, using the given content handler.
     *
     * @param reader document to parse
     * @param namespaceAware whether to be namespace-aware
     * @return parsed document
     */
    public static TreeInfo parseDocument(CharPosTrackingReader reader, boolean namespaceAware) throws ParserConfigurationException,
            SAXException, XPathException {
        Source source = getSaxSource(reader, namespaceAware);
        Configuration config = newXPathFactory().getUnderlyingStaticContext().getConfiguration();
        config.setLineNumbering(true);
        return config.buildDocumentTree(source);
    }

    public static Processor getProcessor() {
        return saxonProcessor;
    }
}
