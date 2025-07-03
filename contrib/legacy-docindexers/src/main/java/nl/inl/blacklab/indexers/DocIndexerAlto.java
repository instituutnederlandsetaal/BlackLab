package nl.inl.blacklab.indexers;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.xml.sax.Attributes;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.index.DocIndexerXmlHandlers;
import nl.inl.blacklab.index.DocWriter;
import nl.inl.blacklab.index.HookableSaxHandler.ElementHandler;
import nl.inl.util.FileUtil;
import nl.inl.util.StringUtil;

/**
 * Index a Alto (OCR'ed text) file. For information about Alto, see
 * http://www.loc.gov/standards/alto/techcenter/elementSet/index.php
 */
public class DocIndexerAlto extends DocIndexerXmlHandlers {

    public static String getDisplayName() {
        return "ALTO (Analyzed Layout and Text Object)";
    }

    public static String getDescription() {
        return "An XML format for the description of text OCR and layout information of pages for digitized material.";
    }

    /**
     * Remove any punctuation and whitespace at the start and end of input.
     *
     * @param input the input string
     * @return the string without punctuation or whitespace at the edges.
     */
    public static String trimWhitespaceAndPunctuation(String input) {
        return PATT_WS_PUNCT_AT_START_OR_END.matcher(input).replaceAll("");
    }

    /**
     * Contains the image file name found in Description so we can add it to Page
     * (NOTE: do we still use this now that we index the whole XML file?)
     */
    String imageFileName = "?";

    @SuppressWarnings("deprecation")
    public DocIndexerAlto(DocWriter indexer, final String fileName, Reader reader) {
        super(indexer, fileName, reader);
        registerContentsField();
        
        // Document element
        addHandler("/alto", new DocumentElementHandler());

        // Description block: capture fileName
        addHandler("/alto/Description/*", true, new ElementHandler() {

            String metadataField = null;

            @Override
            public void startElement(String uri, String localName, String qName,
                    Attributes attributes) {
                super.startElement(uri, localName, qName, attributes);
                if (localName.equals("fileName")) {
                    metadataField = "imageFileName";
                }
                consumeCharacterContent(); // clear
            }

            @Override
            public void endElement(String uri, String localName, String qName) {
                if (metadataField != null) {
                    String value = StringUtil.normalizeWhitespace(consumeCharacterContent().trim());
                    if (metadataField.equals("imageFileName")) {
                        imageFileName = value;

                        if (externalMetadataAvailable) {
                            // Process filename to get information from it
                            // and cross-reference it with external document index
                            String[] infoFromFileName = getInfoFromImageFileName(imageFileName);
                            addMetadataField("title", infoFromFileName[0]);
                            addMetadataField("author", infoFromFileName[1]);
                            addMetadataField("year", infoFromFileName[2]);
                            addMetadataField("page", infoFromFileName[3]);
                        }
                    }
                    addMetadataField(metadataField, value);
                    metadataField = null;
                }
                super.endElement(uri, localName, qName);
            }

        });

        // Page element: inject extra tag; clear content buffer
        addHandler("/alto/Layout/Page", new ElementHandler() {
            @Override
            public void startElement(String uri, String localName, String qName,
                    Attributes attributes) {

                // Inject an extra element into the stored content at this position.
                processContent("<imageFileName>" + imageFileName + "</imageFileName>");

                // Clear content buffer for capturing punctuation
                consumeCharacterContent();
            }
        });

        // Word elements: index as main contents
        addHandler("//String", new WordInAttributeHandler("CONTENT") {
            @Override
            protected String getWord() {
                // In Alto, punctuation and/or whitespace may be part of the word token.
                // Strip it off before indexing the word.
                // (instead of stripping it off, better to add it to punctuation index..?)
                return trimWhitespaceAndPunctuation(super.getWord());
            }
        });

        // Named entity tags: index as tags in the content
        addHandler("//NE", new InlineTagHandler());

        // Optionally read external metadata
        // (OLD; replaced by MetadataFetcher system. This will be removed soon)
        if (!externalMetadataAvailable) {
            String file = getParameter("metadataFile");
            if (file != null && file.length() > 0)
                readMetadata(new File(file));
        }

    }

    /* ---- NOTE: all the following is only used for a specific dataset, to merge external metadata
     *      with the input XML. Can safely be ignored. ---- */

    /**
     * Document titles by DPO num (OLD; replaced by MetadataFetcher system. This
     * will be removed soon)
     */
    static Map<String, String> titles;
    static Map<String, String> dates;
    static Map<String, String> authors;

    /**
     * Read external metadatafile (OLD; replaced by MetadataFetcher system. This
     * will be removed soon)
     * 
     * @param metadataFile the file
     */
    private static void readMetadata(File metadataFile) {
        titles = new HashMap<>();
        dates = new HashMap<>();
        authors = new HashMap<>();
        // File metadataFile = new File("c:\\temp\\dpo_metadata.txt");
        try (BufferedReader r = FileUtil.openForReading(metadataFile)) {
            String l;
            while (true) {
                try {
                    l = r.readLine();
                } catch (IOException e) {
                    throw BlackLabException.wrapRuntime(e);
                }
                if (l == null)
                    break;
                if (l.length() == 0)
                    continue;
                String[] fields = l.split("\t", -1);
                titles.put(fields[0].trim(), fields[1].trim());
                dates.put(fields[0].trim(), fields[3].trim());
                authors.put(fields[0].trim(), fields[4].trim());
            }
        } catch (IOException e) {
            throw BlackLabException.wrapRuntime(e);
        }
        externalMetadataAvailable = true;
    }

    /**
     * Pattern for getting DPO number and page number from image file name (OLD.
     * Will be removed soon)
     */
    private final static Pattern PATT_DPO_AND_PAGE = Pattern.compile("^dpo_(\\d+)_(\\d+)_");

    /**
     * Whether or not the external metadata is available (OLD. Will be removed soon)
     */
    static boolean externalMetadataAvailable = false;

    /**
     * Get metadata from filename (OLD. Will be removed soon)
     * 
     * @param imageFileName the file name
     * @return the metadata (title, author, date, page)
     */
    static String[] getInfoFromImageFileName(String imageFileName) {
        Matcher m = PATT_DPO_AND_PAGE.matcher(imageFileName);
        String dpo;
        String page;
        if (m.find()) {
            dpo = m.group(1);
            page = m.group(2);
        } else {
            dpo = "?";
            page = "?";
            System.err.println("No DPO/page found: " + imageFileName);
        }
        return new String[] {
                titles.getOrDefault(dpo, "?"),
                authors.getOrDefault(dpo, "?"),
                dates.getOrDefault(dpo, "?"),
                page
        };
    }

}
