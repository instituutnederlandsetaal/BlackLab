package nl.inl.blacklab.indexers.config.saxon;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.util.Optional;

import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.io.IOUtils;

import net.sf.saxon.Configuration;
import net.sf.saxon.lib.ResourceRequest;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.trans.XPathException;
import nl.inl.blacklab.exceptions.ErrorIndexingFile;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.plugins.IndexSourceType;
import nl.inl.blacklab.plugins.PluginManager;
import nl.inl.blacklab.plugins.param.PluginParams;
import nl.inl.util.ObjectCache;
import nl.inl.util.fileprocessor.FileIterator;
import nl.inl.util.fileprocessor.FileReference;

/**
 * A helper for indexing using Saxon.
 */
public class SaxonHelper {

    static Processor saxonProcessor = new Processor(false);

    static {
        // Configure the shared processor
        Configuration config = saxonProcessor.getUnderlyingConfiguration();
        // Custom "URI resolver" (actually resource resolver) for doc() function
        config.setResourceResolver(SaxonHelper::resolve);
        // Enable line numbering for all documents built with this configuration
        config.setLineNumbering(true);
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
            FileIterator fileIt = indexSourceType.get().get(restOfUri, PluginParams.NONE).filesToIndex();
            FileReference file = fileIt.next();
            if (file == FileReference.DUMMY)
                throw new IllegalArgumentException("doc() URI resolves to FileReference.DUMMY: " + uri);
            if (fileIt.hasNext()) // must match single file
                throw new IllegalArgumentException("doc() URI matches multiple files: " + uri);
            // Read the file and return its contents as a StreamSource
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
        // Return a StreamSource with the contents for Saxon to parse
        return new StreamSource(new StringReader(contents), req.uri);
    }

    private SaxonHelper() {}

    public static XPathCompiler newXPathFactory() {
        return saxonProcessor.newXPathCompiler();
    }

    public static SaxonDocumentWithElementOffsets parseDocument(Reader reader, boolean namespaceAware) throws XPathException, XMLStreamException, IOException {
        return new SaxonDocumentWithElementOffsets(reader, saxonProcessor.getUnderlyingConfiguration());
    }

    public static Processor getProcessor() {
        return saxonProcessor;
    }
}
