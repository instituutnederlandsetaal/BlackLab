package nl.inl.blacklab.indexers;

import java.io.Reader;

import org.xml.sax.Attributes;

import nl.inl.blacklab.index.BLInputDocument;
import nl.inl.blacklab.index.DocIndexerXmlHandlers;
import nl.inl.blacklab.index.DocWriter;
import nl.inl.blacklab.index.HookableSaxHandler.ElementHandler;
import nl.inl.blacklab.index.annotated.AnnotationWriter;
import nl.inl.util.StringUtil;

/**
 * Index a TEI P4/P5 file. (Abstract base class for different TEI variants)
 */
public abstract class DocIndexerTeiBase extends DocIndexerXmlHandlers {

    /**
     * If true, we are inside a listBibl element where we should capture metadata
     * fields (interpGrp/interp)
     */
    boolean captureMetadata = false;

    /** Value of the type attribute of the interpGrp we're in (or null) */
    String interpGrpType;

    final boolean hasLemma;

    final String indexLemmaAs;

    final boolean hasType;

    final String indexTypeAs;

    final boolean hasFunction;

    final String indexFunctionAs;

    final String contentElement;

    @SuppressWarnings("deprecation")
    protected DocIndexerTeiBase(DocWriter indexer, String fileName, Reader reader, String contentElement,
            boolean defaultToPosInTypeAttribute) {
        super(indexer, fileName, reader);

        this.contentElement = contentElement;

        // Add some extra properties
        hasLemma = getParameter("hasAttr_lemma", true);
        indexLemmaAs = getParameter("attrPropName_lemma", "lemma");

        if (defaultToPosInTypeAttribute) {
            // Default to "type" attribute containing PoS (unless changed via parameters)
            hasType = getParameter("hasAttr_type", true);
            indexTypeAs = getParameter("attrPropName_type", "pos");
            hasFunction = getParameter("hasAttr_function", false);
            indexFunctionAs = getParameter("attrPropName_function", "function");
        } else {
            // Default to "function" attribute containing PoS (unless changed via parameters)
            hasType = getParameter("hasAttr_type", false);
            indexTypeAs = getParameter("attrPropName_type", "type");
            hasFunction = getParameter("hasAttr_function", true);
            indexFunctionAs = getParameter("attrPropName_function", "pos");
        }

        init();

    }

    public void init() {

        // Get handles to the default properties (the main one & punct)
        final AnnotationWriter propMain = mainAnnotation();
        final AnnotationWriter propPunct = punctAnnotation();

        final AnnotationWriter propLemma = hasLemma ? addAnnotation(indexLemmaAs) : null;
        final AnnotationWriter propType = hasType ? addAnnotation(indexTypeAs) : null;
        final AnnotationWriter propFunction = hasFunction ? addAnnotation(indexFunctionAs) : null;
        
        registerContentsField();

        // Doc element: the individual documents to index
        // Note that we add handlers for both TEI and TEI.2, to
        // handle both TEI P5 and P4 files.
        DocumentElementHandler documentElementHandler = new DocumentElementHandler() {

            @Override
            public void endElement(String uri, String localName, String qName) {

                // Combine levels 1 & 2 of author and title field for easier
                // searching and displaying
                combineAuthorAndTitleFields();

                super.endElement(uri, localName, qName);
            }

        };
        addHandler("TEI", documentElementHandler);
        addHandler("TEI.2", documentElementHandler);

        // Content element: the main text contents.
        // We use the body element by default, but a subclass can change this default by
        // calling superconstructor with extra param, see DocIndexerTeiText.
        //
        // This handler clears captured character content at the beginning to start afresh.
        final ElementHandler body = addHandler(contentElement, new ElementHandler() {
            @Override
            public void startElement(String uri, String localName, String qName,
                    Attributes attributes) {
                consumeCharacterContent(); // clear it to capture punctuation and words
            }

            @Override
            public void endElement(String uri, String localName, String qName) {

                // Before ending the document, add the final bit of punctuation.
                propPunct.addValue(StringUtil.normalizeWhitespace(consumeCharacterContent()));

                super.endElement(uri, localName, qName);
            }

        });

        // listBibl element (metadata): keep track of id attribute
        addHandler("listBibl", new ElementHandler() {

            @Override
            public void startElement(String uri, String localName, String qName,
                    Attributes attributes) {
                consumeCharacterContent(); // clear it to capture punctuation and words
                String listBiblId = attributes.getValue("id");
                @SuppressWarnings("deprecation")
                String listBiblIdToCapture = getParameter("listBiblIdToCapture", "inlMetadata");
                captureMetadata = listBiblId != null && listBiblId.equals(listBiblIdToCapture);
            }

            @Override
            public void endElement(String uri, String localName, String qName) {
                captureMetadata = false;
            }
        });

        // interpGrp element: metadata category
        addHandler("interpGrp", new ElementHandler() {
            @Override
            public void startElement(String uri, String localName, String qName,
                    Attributes attributes) {
                consumeCharacterContent(); // clear it to capture punctuation and words
                interpGrpType = attributes.getValue("type");
                if (interpGrpType == null)
                    interpGrpType = "";
            }

            @Override
            public void endElement(String uri, String localName, String qName) {
                interpGrpType = null;
            }
        });

        // interp element: metadata value
        addHandler("interp", new ElementHandler() {
            @Override
            public void startElement(String uri, String localName, String qName,
                    Attributes attributes) {
                if (!captureMetadata || interpGrpType == null)
                    return;
                String value = attributes.getValue("value");
                if (value == null)
                    value = "";
                addMetadataField(interpGrpType, value);
            }

        });

        // Word elements: index as main contents
        addHandler("w", new WordHandlerBase() {

            @Override
            public void startElement(String uri, String localName, String qName,
                    Attributes attributes) {
                if (!body.insideElement())
                    return;
                super.startElement(uri, localName, qName, attributes);

                // Determine headword and part of speech from the attributes
                if (hasLemma) {
                    String lemma = attributes.getValue("lemma");
                    if (lemma == null) {
                        lemma = "";
                    }
                    propLemma.addValue(lemma);
                }
                if (hasType) {
                    String pos = attributes.getValue("type");
                    if (pos == null)
                        pos = "?";
                    propType.addValue(pos);
                }
                if (hasFunction) {
                    String func = attributes.getValue("function");
                    if (func == null)
                        func = "?";
                    propFunction.addValue(func);
                }

                // Add punctuation
                propPunct.addValue(StringUtil.normalizeWhitespace(consumeCharacterContent()));
            }

            @Override
            public void endElement(String uri, String localName, String qName) {
                if (!body.insideElement())
                    return;
                super.endElement(uri, localName, qName);
                propMain.addValue(consumeCharacterContent());
            }

        });

        // Sentence tags: index as tags in the content (only inside body element)
        addHandler("s", new InlineTagHandler() {

            @Override
            public void startElement(String uri, String localName, String qName,
                    Attributes attributes) {
                if (body.insideElement())
                    super.startElement(uri, localName, qName, attributes);
            }

            @Override
            public void endElement(String uri, String localName, String qName) {
                if (body.insideElement())
                    super.endElement(uri, localName, qName);
            }

        });

    }

    /**
     * Make author and authorCombined fields, which allow easier searching thatn
     * with authorLevel1 and/or authorLevel2 separately.
     */
    void combineAuthorAndTitleFields() {
        BLInputDocument myLuceneDoc = getCurrentDoc();
        String author = myLuceneDoc.get("authorLevel1");
        String authorLevel2 = myLuceneDoc.get("authorLevel2");
        if (author != null || authorLevel2 != null) {
            // Make author field, which is authorLevel1 or authorLevel2 if the first is empty
            // Also make authorCombined, which is an indexed field combining the two levels (for searching).
            if (author == null)
                author = "";
            if (authorLevel2 == null)
                authorLevel2 = "";
            if (author.isEmpty()) {
                author = authorLevel2;
                authorLevel2 = "";
            }
            String authorCombined = author + " " + authorLevel2;
            addMetadataField("author", author);
            addMetadataField("authorCombined", authorCombined);
        }

        String title = myLuceneDoc.get("titleLevel1");
        String titleLevel2 = myLuceneDoc.get("titleLevel2");
        if (title != null || titleLevel2 != null) {
            // Make title field, which is titleLevel1 or titleLevel2 if the first is empty
            // Also make titleCombined, which is an indexed field combining the two levels (for searching).
            if (title == null)
                title = "";
            if (titleLevel2 == null)
                titleLevel2 = "";
            if (title.isEmpty()) {
                title = titleLevel2;
                titleLevel2 = "";
            }
            String titleCombined = title + " " + titleLevel2;
            addMetadataField("title", title);
            addMetadataField("titleCombined", titleCombined);
        }
    }

}
