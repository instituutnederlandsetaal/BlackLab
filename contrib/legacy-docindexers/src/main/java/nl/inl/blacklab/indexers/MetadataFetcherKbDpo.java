package nl.inl.blacklab.indexers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.index.DocIndexerLegacy;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.index.MetadataFetcher;
import nl.inl.blacklab.indexers.MetadataFetcherKbDpo.GetKbMetadata.Metadata;
import nl.inl.util.XmlUtil;

/**
 * Metadata fetcher for KB DPO metadata.
 *
 * In order for this class to work correctly, you should add the following
 * libraries to the classpath (from Apache Http Components, version 4.1.2 or
 * up):
 * 
 * <p>
 * <ul>
 * <li>commons-codec
 * <li>commons-logging
 * <li>httpclient
 * <li>httpcore
 * </ul>
 */
public class MetadataFetcherKbDpo extends MetadataFetcher {

    /**
     * Retrieve metadata from KB.
     */
    public static class GetKbMetadata {

        public record Metadata(String title, String author, String date, String ppn) {}

        /** Has the HTTP client been initialized? */
        static boolean initialised = false;

        /* Xpath expressions used to get metadata fields from XML */
        static XPathExpression xpathPpn;
        static XPathExpression xpathTitle;
        static XPathExpression xpathAuthor;
        static XPathExpression xpathDate;

        private static Constructor<?> ctorHttpGetUrl;
        private static Method methHttpClientExecute;
        private static Method methHttpResponseGetStatusLine;
        private static Method methHttpResponseGetEntity;
        private static Method methStatusLineGetStatusCode;
        private static Method methStatusLineGetReasonPhrase;
        private static Method methHttpEntityGetContent;
        private static Method methEntityUtilsConsume;

        /** The HTTP client used to get metadata from webservices */
        static Object defaultHttpClient;

        /** Cached metadata (saves URL requests) */
        static final Map<String, Metadata> cached = new HashMap<>();

        private static void init() {

            // Init XPath
            XmlUtil.setNamespaceAware(true);
            XPathFactory factory = XPathFactory.newInstance();
            XPath xpath = factory.newXPath();
            xpath.setNamespaceContext(new NamespaceContext() {
                @Override
                public Iterator<String> getPrefixes(String arg0) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public String getPrefix(String arg0) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public String getNamespaceURI(String prefix) {
                    if (prefix == null)
                        throw new IllegalArgumentException("Null prefix");
                    return switch (prefix) {
                        case "xsi" -> "http://www.w3.org/2001/XMLSchema-instance";
                        case "dc" -> "http://purl.org/dc/elements/1.1/";
                        case "dcx" -> "http://krait.kb.nl/coop/tel/handbook/telterms.html";
                        case "" -> "http://www.openarchives.org/OAI/2.0/";
                        case "xml" -> XMLConstants.XML_NS_URI;
                        default -> XMLConstants.NULL_NS_URI;
                    };
                }
            });

            // Compile XPath expressions
            try {
                xpathPpn = xpath.compile("//dcx:recordIdentifier[@xsi:type='dcx:PPN']");
                xpathTitle = xpath.compile("//dc:title");
                xpathAuthor = xpath.compile("//dc:creator");
                xpathDate = xpath.compile("//dc:date");
            } catch (XPathExpressionException e) {
                throw BlackLabRuntimeException.wrap(e);
            }

            try {
                // Use reflection to get handle to classes, ctors and methods
                // (so we avoid static dependencies on the Apache HTTP libraries)
                // Apache HTTP components classes, ctors and methods (we use reflection to avoid
                // static dependency on these libs)
                Class<?> clsDefaultHttpClient = Class.forName("org.apache.http.impl.client.DefaultHttpClient");
                Class<?> clsHttpUriRequest = Class.forName("org.apache.http.client.methods.HttpUriRequest");
                Class<?> clsHttpGet = Class.forName("org.apache.http.client.methods.HttpGet");
                Class<?> clsHttpResponse = Class.forName("org.apache.http.HttpResponse");
                Class<?> clsStatusLine = Class.forName("org.apache.http.StatusLine");
                Class<?> clsHttpEntity = Class.forName("org.apache.http.HttpEntity");
                Class<?> clsEntityUtils = Class.forName("org.apache.http.util.EntityUtils");
                ctorHttpGetUrl = clsHttpGet.getConstructor(String.class);
                methHttpClientExecute = clsDefaultHttpClient.getMethod("execute", clsHttpUriRequest);
                methHttpResponseGetStatusLine = clsHttpResponse.getMethod("getStatusLine");
                methHttpResponseGetEntity = clsHttpResponse.getMethod("getEntity");
                methStatusLineGetStatusCode = clsStatusLine.getMethod("getStatusCode");
                methStatusLineGetReasonPhrase = clsStatusLine.getMethod("getReasonPhrase");
                methHttpEntityGetContent = clsHttpEntity.getMethod("getContent");
                methEntityUtilsConsume = clsEntityUtils.getMethod("consume", clsHttpEntity);

                // Instantiate HTTP client object
                defaultHttpClient = clsDefaultHttpClient.getConstructor().newInstance();

            } catch (Exception e) {
                throw new BlackLabRuntimeException("Error finding (some of the) Apache HTTP libraries."
                        + "Make sure Apache commons-codec, commons-logging, httpclient, httpcore (4.1.2 or higher) are on the classpath.",
                        e);
            }

            initialised = true;
        }

        private static Document getMetadataByDpo(String dpo) {
            String url = "http://services.kb.nl/mdo/oai?verb=GetRecord&identifier=DPO:dpo:" + dpo
                    + ":mpeg21&metadataPrefix=didl";
            return fetchDomDocument(url);
        }

        private static Document getMetadataByPpn(String ppn) {
            String url = "http://services.kb.nl/mdo/oai?verb=GetRecord&identifier=DPO:DPO:" + ppn
                    + "&metadataPrefix=dcx";
            return fetchDomDocument(url);
        }

        private static String fetchDocument(String url) {
            int code = -1;
            String reason;

            // HTTP
            try {

                // HttpGet httpGet = new HttpGet(url);
                Object httpGet = ctorHttpGetUrl.newInstance(url);

                // HttpResponse response = defaultHttpClient.execute(httpGet);
                Object httpResponse = methHttpClientExecute.invoke(defaultHttpClient, httpGet);

                // StatusLine statusLine = httpResponse.getStatusLine();
                Object statusLine = methHttpResponseGetStatusLine.invoke(httpResponse);

                // code = statusLine.getStatusCode();
                code = (Integer) methStatusLineGetStatusCode.invoke(statusLine);

                // reason = statusLine.getReasonPhrase();
                reason = (String) methStatusLineGetReasonPhrase.invoke(statusLine);

                // HttpEntity httpEntity = httpResponse.getEntity();
                Object httpEntity = methHttpResponseGetEntity.invoke(httpResponse);

                if (code == 200) {
                    // InputStream is = httpEntity.getContent();
                    InputStream is = (InputStream) methHttpEntityGetContent.invoke(httpEntity);

                    try (BufferedReader b = new BufferedReader(
                            new InputStreamReader(is, Indexer.DEFAULT_INPUT_ENCODING))) {
                        StringBuilder content = new StringBuilder();
                        while (true) {
                            String line = b.readLine();
                            if (line == null)
                                break;
                            content.append(line);
                        }
                        return content.toString();
                    }
                }

                try {
                    // Some HTTP error occurred (e.g. 403 / 404)
                    // Make sure the entity content is fully consumed and the content stream, if
                    // exists, is closed. The process is done, quietly , without throwing any
                    // IOException.
                    methEntityUtilsConsume.invoke(httpEntity);
                } catch (Exception e) {
                    // Not important, ignore
                }

            } catch (UnknownHostException e) {
                reason = "Unknown host";
            } catch (IOException e) {
                reason = "IOException: " + e.getMessage();
            } catch (Exception e) {
                e.printStackTrace();
                if (e.getCause() != null)
                    reason = e.getCause().getMessage();
                else
                    reason = e.getMessage();
            }
            if (code != 200)
                System.err.println("Could not fetch " + url + " (" + code + " " + reason + ")");

            return null;
        }

        private static Document fetchDomDocument(String url) {
            String content = fetchDocument(url);
            try {
                return XmlUtil.parseXml(new StringReader(content));
            } catch (SAXException e) {
                e.printStackTrace();
            }
            return null;
        }

        /**
         * Fetch metadata for a document from its DPO number
         *
         * @param dpo the DPO number
         *
         * @return the metadata fetched
         */
        public static Metadata getMetadataFieldsFromDpo(String dpo) {
            if (!initialised)
                init();

            if (cached.containsKey(dpo))
                return cached.get(dpo);

            // Retrieve and parse metadata
            Document doc = getMetadataByDpo(dpo);

            try {

                Node nodePpn = (Node) xpathPpn.evaluate(doc, XPathConstants.NODE);
                String ppn = nodePpn == null ? "?" : nodePpn.getTextContent();
                Document doc2 = getMetadataByPpn(ppn);

                Node nodeTitle = (Node) xpathTitle.evaluate(doc2, XPathConstants.NODE);
                Node nodeAuthor = (Node) xpathAuthor.evaluate(doc2, XPathConstants.NODE);
                Node nodeDate = (Node) xpathDate.evaluate(doc2, XPathConstants.NODE);
                String title = nodeTitle == null ? "?" : nodeTitle.getTextContent();
                String author = nodeAuthor == null ? "?" : nodeAuthor.getTextContent();
                String date = nodeDate == null ? "?" : nodeDate.getTextContent();

                Metadata metadata = new Metadata(title, author, date, ppn);
                cached.put(dpo, metadata); // save in cache so we don't query it twice
                return metadata;

            } catch (XPathExpressionException | DOMException e) {
                throw BlackLabRuntimeException.wrap(e);
            }
        }

        private GetKbMetadata() {
            // Cannot instantiate
        }

    }

    /** Pattern for getting DPO number from image file name */
    private final static Pattern PATT_DPO = Pattern.compile("^dpo_(\\d+)_");

    public MetadataFetcherKbDpo(DocIndexerLegacy docIndexer) {
        super(docIndexer);
    }

    @Override
    public void addMetadata() {
        String fileName;
        fileName = docIndexer.getCurrentDoc().get("imageFileName");

        Matcher m = PATT_DPO.matcher(fileName);
        if (m.find()) {
            String dpo;
            dpo = m.group(1);
            Metadata metadata = GetKbMetadata.getMetadataFieldsFromDpo(dpo);
            docIndexer.addMetadataField("title", metadata.title);
            docIndexer.addMetadataField("author", metadata.author);
            docIndexer.addMetadataField("date", metadata.date);
            docIndexer.addMetadataField("ppn", metadata.ppn);
        } else {
            System.err.println("DPO number not found for imageFileName " + fileName);
        }

    }

}
