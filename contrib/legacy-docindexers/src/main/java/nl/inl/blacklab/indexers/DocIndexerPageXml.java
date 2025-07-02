package nl.inl.blacklab.indexers;

import java.io.Reader;

import nl.inl.blacklab.index.DocIndexerXmlHandlers;
import nl.inl.blacklab.index.DocWriter;
import nl.inl.blacklab.index.HookableSaxHandler.ElementHandler;

/**
 * Index a PageXML (OCR'ed text) file.
 */
public class DocIndexerPageXml extends DocIndexerXmlHandlers {

    public static String getDisplayName() {
        return "PageXML, an OCR file format";
    }

    public static String getDescription() {
        return "";
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

    public DocIndexerPageXml(DocWriter indexer, String fileName, Reader reader) {
        super(indexer, fileName, reader);
        
        registerContentsField();

        // Document element
        addHandler("/PcGts", new DocumentElementHandler());

        // Page element: store attributes as metadata fields
        addHandler("/PcGts/Page", new MetadataAttributesHandler());

        // Metadata elements in the header
        addHandler("/PcGts/Page/header/*", new MetadataElementHandler());

        // Clear character content after header, so it doesn't mess with punctuation
        addHandler("/PcGts/Page/header", new ElementHandler() {
            @Override
            public void endElement(String uri, String localName, String qName) {
                consumeCharacterContent();
            }
        });

        // Word elements: index as main contents
        addHandler("//Word", new DefaultWordHandler() {
            @Override
            protected String getWord() {
                // In PageXML, punctuation and/or whitespace may be part of the word token.
                // Strip it off before indexing the word.
                // (instead of stripping it off, better to add it to punctuation index..?)
                return trimWhitespaceAndPunctuation(super.getWord());
            }
        });

        // Named entity tags: index as tags in the content
        addHandler("//NE", new InlineTagHandler());

    }

}
