package nl.inl.blacklab.indexers;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.io.input.TeeInputStream;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.index.BLInputDocument;
import nl.inl.blacklab.index.DocIndexerLegacy;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.index.MetadataFetcher;
import nl.inl.util.TextContent;

/**
 * Example of a metadata fetcher, a class used to fetch metadata from an
 * external source in case it's not included in the documents.
 *
 * This class fetches the metadata from a ZIP file with a certain structure
 * (specific to the OpenSonar project), but it should be easy to adapt to your
 * own needs.
 *
 * Note that for accessing large ZIP files, you need Java 7 which supports the
 * ZIP64 format, otherwise you'll get the "invalid CEN header (bad signature)"
 * error)
 */
public class MetadataFetcherSonarCmdi extends MetadataFetcher {

    private static final int INITIAL_CMDI_BYTEBUFFER_SIZE = 1000;

    @SuppressWarnings("deprecation")
    private static void init(DocIndexerLegacy docIndexer) {
        String zipFilePath = docIndexer.getParameter("metadataZipFile");
        if (zipFilePath == null) {
            zipFilePath = docIndexer.getParameter("metadataDir");
            if (zipFilePath == null)
                throw new BlackLabRuntimeException(
                        "For OpenSonar metadata, specify metadataZipFile or metadataDir in indexer.properties!");
            metadataDir = new File(zipFilePath);
        } else {
            try {
                metadataZipFile = new ZipFile(new File(zipFilePath));
            } catch (IOException e) {
                throw BlackLabRuntimeException.wrap(e);
            }
        }
    }

    static private ZipFile metadataZipFile = null;

    static private File metadataDir = null;

    private String metadataPathInZip;

    @SuppressWarnings("deprecation")
    public MetadataFetcherSonarCmdi(DocIndexerLegacy docIndexer) {
        super(docIndexer);

        if (metadataZipFile == null)
            init(docIndexer);

        metadataPathInZip = docIndexer.getParameter("metadataPath", "");
        if (metadataPathInZip.length() == 0)
            metadataPathInZip = docIndexer.getParameter("metadataPathInZip", "");
        if (metadataPathInZip.length() > 0 && !metadataPathInZip.endsWith("/"))
            metadataPathInZip += "/";
    }

    @Override
    public void close() {
        // make sure zip file is properly closed when done
        //   (change structure so metadata fetcher isn't instantiated for each document separately)
        //metadataZipFile.close();
    }

    @Override
    public void addMetadata() {

        String fromInputFile;
        BLInputDocument luceneDoc = docIndexer.getCurrentDoc();
        fromInputFile = luceneDoc.get("fromInputFile");

        docIndexer.addMetadataField("Corpus_title", "SoNaR");

        fromInputFile = fromInputFile.replaceAll("\\\\", "/");
        int lastSlash = fromInputFile.lastIndexOf('/');
        int penultimateSlash = fromInputFile.lastIndexOf("/", lastSlash - 1);
        String metadataFile = fromInputFile.substring(penultimateSlash + 1);
        metadataFile = metadataFile.replaceAll("\\.folia\\.", ".cmdi.");

        try {
            InputStream is;
            if (metadataZipFile != null) {
                ZipEntry e = metadataZipFile.getEntry(metadataPathInZip + metadataFile);
                if (e == null) {
                    //throw new BLRuntimeException("Entry in zip not found: " + metadataPathInZip + metadataFile);
                    System.err.println("*** ERROR, metadata entry not found: " + metadataPathInZip + metadataFile);
                    return;
                }
                is = metadataZipFile.getInputStream(e);
            } else {
                File f = new File(new File(metadataDir, metadataPathInZip), metadataFile);
                is = new FileInputStream(f);
            }

            ByteArrayOutputStream cmdiBuffer = new ByteArrayOutputStream(INITIAL_CMDI_BYTEBUFFER_SIZE);
            is = new TeeInputStream(is, cmdiBuffer);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, Indexer.DEFAULT_INPUT_ENCODING))) {
                SAXParserFactory factory = SAXParserFactory.newInstance();
                factory.setNamespaceAware(true);
                SAXParser parser;
                parser = factory.newSAXParser();
                parser.parse(new InputSource(reader), new MetadataParser());
            }

            // Combine AuthorName and Pseudonym fields into
            // fields AuthorNameOrPseudonym / AuthorNameOrPseudonymSearch
            String authorName = luceneDoc.get("AuthorName");
            if (authorName == null)
                authorName = "";
            String pseudonym = luceneDoc.get("Pseudonym");
            if (pseudonym == null)
                pseudonym = "";
            String authorNameAndPseudonym = authorName + " " + pseudonym;
            if (authorName.isEmpty()) {
                authorName = pseudonym;
            }
            docIndexer.addMetadataField("AuthorNameOrPseudonym", authorName);
            docIndexer.addMetadataField("AuthorNameOrPseudonymSearch", authorNameAndPseudonym);

            // Store metadata XML in content store and corresponding id in Lucene document
            TextContent document = TextContent.from(cmdiBuffer, Indexer.DEFAULT_INPUT_ENCODING);
            docIndexer.getDocWriter().storeInContentStore(luceneDoc, document, "metadataCid", "metadata");

            if (metadataZipFile == null)
                is.close();
        } catch (SAXException | ParserConfigurationException | IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    /**
     * Handles the metadata XML and adds it to the Lucene document
     */
    class MetadataParser extends DefaultHandler {

        private final StringBuilder textContent = new StringBuilder();

        private boolean hasChild = false;

        final Map<String, String> indexFieldAs = new HashMap<>();

        final List<String> elementStack = new ArrayList<>();

        /**
         * Push the current element name onto the element stack
         * 
         * @param localName the current element name
         */
        private void stackPush(String localName) {
            elementStack.add(localName);
        }

        /**
         * Pop the current element name off of the element stack
         */
        private void stackPop() {
            elementStack.remove(elementStack.size() - 1);
        }

        /**
         * Get the name of the current element's parent element from the element stack.
         * 
         * @return the parent element name
         */
        private String getParentElName() {
            if (elementStack.size() < 2)
                return "";
            return elementStack.get(elementStack.size() - 2);
        }

        public MetadataParser() {
            indexFieldAs.put("iso-639-3-code", "Language-iso-code");
            indexFieldAs.put("Name", "AuthorName");
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            stackPush(localName);
            hasChild = false; // we haven't seen a child for this element yet
            textContent.setLength(0); // clear buffer
        }

        @Override
        public void endElement(String uri, String localName, String qName) {

            if (!hasChild) {
                // See if we captured any text content
                String content = textContent.toString().trim();
                if (content.length() > 0) {
                    // Yes, leaf element with text content.
                    // Index the value of this element as a metadata field.

                    // Check the parent element name to see if we need to
                    // add a prefix to distinguish elements with the same name.
                    // (e.g. Source/Country and ResidencePlace/Country)
                    String parentElName = getParentElName();
                    if (localName.equals("Country") && !parentElName.equals("Source")) {
                        // Add prefix to distinguish Country under Source from other Country els
                        // (so Country under ResidencePlace becomes ResidencePlace_Country, etc.)
                        localName = parentElName + "_Country";
                    }

                    // See if we want to index this element under a different name.
                    String indexAs = indexFieldAs.get(localName);
                    if (indexAs == null || indexAs.length() == 0)
                        indexAs = localName;

                    // Leaf node with content; store as metadata field.
                    docIndexer.addMetadataField(indexAs, content);
                }
            }

            hasChild = true; // our parent has at least one child
            stackPop();
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (!hasChild)
                textContent.append(ch, start, length);
        }
    }

}
