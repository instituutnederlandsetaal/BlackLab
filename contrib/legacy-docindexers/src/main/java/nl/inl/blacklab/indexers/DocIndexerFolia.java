package nl.inl.blacklab.indexers;

import java.io.Reader;

import org.xml.sax.Attributes;

import nl.inl.blacklab.index.DocIndexerXmlHandlers;
import nl.inl.blacklab.index.DocWriter;
import nl.inl.blacklab.index.HookableSaxHandler.ContentCapturingHandler;
import nl.inl.blacklab.index.HookableSaxHandler.ElementHandler;
import nl.inl.blacklab.index.annotated.AnnotationWriter;

/**
 * Index a FoLiA file. For information about FoLiA, see
 * http://proycon.github.io/folia/
 */
public class DocIndexerFolia extends DocIndexerXmlHandlers {

    public static final String ATTR_NAME_CLASS = "class";

    public static String getDisplayName() {
        return "FoLiA-DocIndexer (alternative indexer)";
    }

    public static String getDescription() {
        return "DocIndexerFolia, less flexible than config-based one but a bit faster.";
    }

    String wordform;

    String pos;

    String lemma;

    public DocIndexerFolia(DocWriter indexer, String fileName, Reader reader) {
        super(indexer, fileName, reader);

        // Get handles to the default annotations (the main one & punct)
        final AnnotationWriter annotMain = mainAnnotation();
        final AnnotationWriter annotPunct = punctAnnotation();

        // Add some extra annotations
        final AnnotationWriter annotLemma = addAnnotation("lemma");
        final AnnotationWriter annotPartOfSpeech = addAnnotation("pos");

        registerContentsField();
        
        // Doc element: the individual documents to index
        addHandler("/FoLiA", new DocumentElementHandler());

        // Word elements: index as main contents
        addHandler("w", new WordHandlerBase() {

            @Override
            public void startElement(String uri, String localName, String qName,
                    Attributes attributes) {
                super.startElement(uri, localName, qName, attributes);
                wordform = "";
                pos = "";
                lemma = "";
            }

            @Override
            public void endElement(String uri, String localName, String qName) {
                super.endElement(uri, localName, qName);
                if (wordform.length() > 0) {
                    annotMain.addValue(wordform);
                    annotPartOfSpeech.addValue(pos);
                    annotLemma.addValue(lemma);
                    annotPunct.addValue(" ");
                }
            }
        });

        // lemma element: contains lemma
        addHandler("lemma", new ElementHandler() {
            @Override
            public void startElement(String uri, String localName, String qName,
                    Attributes attributes) {
                super.startElement(uri, localName, qName, attributes);
                lemma = attributes.getValue(ATTR_NAME_CLASS);
                if (lemma == null)
                    lemma = "";
            }
        });

        // pos element: contains part of speech
        addHandler("pos", new ElementHandler() {
            @Override
            public void startElement(String uri, String localName, String qName,
                    Attributes attributes) {
                super.startElement(uri, localName, qName, attributes);
                pos = attributes.getValue(ATTR_NAME_CLASS);
                if (pos == null)
                    pos = "";
            }
        });

        // t (token) element directly under w (word) element: contains the word form
        addHandler("w/t", new ContentCapturingHandler() {

            /**
             * Tokens with a class attribute are (usually?) the original scanned token
             * before correction, so we skip them
             */
            boolean isOcr;

            @Override
            public void startElement(String uri, String localName, String qName,
                    Attributes attributes) {
                super.startElement(uri, localName, qName, attributes);
                isOcr = attributes.getValue(ATTR_NAME_CLASS) != null;
            }

            @Override
            public void endElement(String uri, String localName, String qName) {
                super.endElement(uri, localName, qName);
                if (!isOcr)
                    wordform = getElementContent();
            }
        });

        // Sentence tags: index as tags in the content
        addHandler("s", new InlineTagHandler());

        // Paragraph tags: index as tags in the content
        addHandler("p", new InlineTagHandler());

        // meta elements: metadata fields
        addHandler("meta", new ContentCapturingHandler() {

            private String metadataFieldName;

            /** Open tag: add metadata field */
            @Override
            public void startElement(String uri, String localName, String qName, Attributes attributes) {
                super.startElement(uri, localName, qName, attributes);
                metadataFieldName = attributes.getValue("id");
            }

            @Override
            public void endElement(String uri, String localName, String qName) {
                super.endElement(uri, localName, qName);
                if (metadataFieldName != null)
                    addMetadataField(metadataFieldName, getElementContent());
            }
        });
    }
}
