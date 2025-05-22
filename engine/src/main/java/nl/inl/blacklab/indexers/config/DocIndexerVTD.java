package nl.inl.blacklab.indexers.config;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import com.ximpleware.AutoPilot;
import com.ximpleware.BookMark;
import com.ximpleware.NavException;
import com.ximpleware.VTDException;
import com.ximpleware.VTDGen;
import com.ximpleware.VTDNav;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.MalformedInputFile;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.index.annotated.AnnotatedFieldWriter;
import nl.inl.blacklab.index.annotated.AnnotationWriter;
import nl.inl.blacklab.indexers.config.InlineObject.InlineObjectType;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.util.FileReference;
import nl.inl.util.StringUtil;
import nl.inl.util.TextContent;
import nl.inl.util.XmlUtil;

/**
 * An indexer configured using full XPath 1.0 expressions.
 */
public class DocIndexerVTD extends DocIndexerXPath<VTDNav> {

    public static final String PROCESSOR_NAME = "vtd";

    public static final String FT_OPT_RESOLVE_NAMED_ENTITY_REFERENCES = "resolveNamedEntityReferences";

    public enum FragmentPosition {
        BEFORE_OPEN_TAG,
        AFTER_OPEN_TAG,
        BEFORE_CLOSE_TAG,
        AFTER_CLOSE_TAG
    }

    /** Our input document */
    private byte[] inputDocument;

    /** What was the byte offset of the last char position we determined? */
    private int lastCharPositionByteOffset;

    /** What was the last character position we determined? */
    private int lastCharPosition;

    /** Byte position at which the document started */
    private int documentByteOffset;

    /** Length of the document in bytes */
    private int documentLengthBytes;

    /** VTD parser (generator?) */
    private VTDGen vg;

    /** XPath util functions and caching of XPathExpressions */
    private XpathFinderVTD finder;

    public void setDocument(byte[] contents, Charset defaultCharset) {
        boolean resolveNamedEntityReferences = Boolean.parseBoolean(
                config.getFileTypeOptions().getOrDefault(FT_OPT_RESOLVE_NAMED_ENTITY_REFERENCES, "false"));
        if (resolveNamedEntityReferences) {
            // Document contains old DTD-style named entity declarations. Resolve them because VTD-XML can't deal with these.
            String doc = XmlUtil.readXmlAndResolveReferences(
                    new BufferedReader(new InputStreamReader(new ByteArrayInputStream(contents), defaultCharset)));
            contents = doc.getBytes(defaultCharset);
        }
        this.inputDocument = contents;
    }

    @Override
    public void setDocument(FileReference file) {
        super.setDocument(file);
        // VTD always needs a byte array
        setDocument(file.getBytes(), file.getCharSet());
    }

    @Override
    protected void xpathForEach(String xPath, VTDNav node, NodeHandler<VTDNav> handler) {
        finder.xpathForEach(xPath, node, handler);
    }

    @Override
    protected void xpathForEachStringValue(String xPath, VTDNav node, StringValueHandler handler) {
        finder.xpathForEachStringValue(xPath, node, handler);
    }

    @Override
    protected String xpathValue(String xpath, VTDNav context) {
        return finder.xpathValue(xpath, context);
    }

    @Override
    protected String xpathXml(String xpath, VTDNav context) {
        return finder.xpathXml(xpath, context);
    }

    @Override
    protected String currentNodeXml(VTDNav node) {
        return finder.currentNodeXml(node);
    }

    @Override
    protected VTDNav contextNodeWholeDocument() {
        return finder.getNav(); // VTD indexer keeps current context globally
    }

    @Override
    public void index() throws MalformedInputFile, PluginException, IOException {
        super.index();
        if (inputDocument.length == 0) { // VTD doesn't like empty documents
            warn("Document is empty, skipping: " + documentName);
            return;
        }
        parseFile();

        // Index all documents in the file
        indexParsedFile(config.getDocumentPath(), false);
    }

    private void parseFile() {
        // Parse use VTD-XML
        vg = new VTDGen();
        vg.setDoc(inputDocument);
        // Whitespace in between elements is normally ignored,
        // but we explicitly allow whitespace in between elements to be collected here.
        // This allows punctuation xpath to match this whitespace, in case punctuation/whitespace in the document isn't contained in a dedicated element or attribute.
        // This doesn't mean that this whitespace is always used, it just enables the punctuation xpath to find this whitespace if it explicitly matches it.
        vg.enableIgnoredWhiteSpace(true);
        try {
            vg.parse(config.isNamespaceAware());
            finder = new XpathFinderVTD(vg.getNav(), config.getNamespaces());
        } catch (VTDException e) {
            throw new MalformedInputFile("Error indexing file: " + documentName, e);
        }
    }

    // process annotated field

    protected void processAnnotatedFieldContainer(VTDNav container, ConfigAnnotatedField annotatedField, Map<String, Span> tokenPositionsMap) {

        if (AnnotatedFieldNameUtil.isParallelField(annotatedField.getName())) {
            warnOnce("Parallel corpora not supported with VTD indexer! Results will be undefined. Use 'processor: saxon' in your config file.");
        }

        // First we find all inline elements (stuff like s, p, b, etc.) and store
        // the locations of their start and end tags in a sorted list.
        // This way, we can keep track of between which words these tags occur.
        // For end tags, we will update the payload of the start tag when we encounter it,
        // just like we do in our SAX parsers.

        List<InlineObject> inlineObjects = new ArrayList<>();
        collectInlineTags(annotatedField, inlineObjects);
        collectPunctuation(annotatedField, inlineObjects);
        inlineObjects.sort(Comparator.naturalOrder());
        Iterator<InlineObject> inlineObjectIt = inlineObjects.iterator();
        InlineObject currentInlineObject = inlineObjectIt.hasNext() ? inlineObjectIt.next() : null;

        VTDNav nav = finder.getNav();

        // Now, find all words, keeping track of what inline objects occur in between.

        // first find all words and sort the list -- words are returned out of order when they
        // are at different nesting levels
        // since the xpath spec doesn't enforce any order, there's nothing we can do
        // so record their positions, sort the list, then restore the position and carry on
        List<Pair<Integer, BookMark>> words = new ArrayList<>();
        xpathForEach(annotatedField.getWordsPath(), container, (word) -> {
            BookMark b = new BookMark(nav);
            b.setCursorPosition();
            words.add(Pair.of(nav.getCurrentIndex(), b));
        });
        words.sort(Entry.comparingByKey());

        AutoPilot apTokenId = annotatedField.getTokenIdPath() == null ? null :
                finder.acquireExpression(annotatedField.getTokenIdPath());

        AnnotatedFieldWriter annotatedFieldWriter = getAnnotatedField(annotatedField.getName());
        finder.navpush();
        Span tokenPosition = Span.token(0);
        for (Pair<Integer, BookMark> word: words) {
            word.getValue().setCursorPosition();

            // Capture tokenId for this token position?
            tokenPosition.setTokenPosition(getCurrentTokenPosition());
            if (apTokenId != null) {
                String tokenId = apTokenId.evalXPathToString();
                tokenPositionsMap.put(tokenId, tokenPosition.copy());
            }

            // Does an inline object occur before this word?
            long wordFragment = 0;
            try {
                wordFragment = nav.getContentFragment();
                if (wordFragment < 0) {
                    // Self-closing tag; use the element fragment instead
                    wordFragment = nav.getElementFragment();
                }
            } catch (NavException e) {
                throw new BlackLabRuntimeException(e);
            }
            int wordOffset = (int) wordFragment;
            // Handle punct and inline objects before this word
            while (currentInlineObject != null && wordOffset >= currentInlineObject.getOffset()) {
                handleInlineObject(currentInlineObject, tokenPositionsMap, tokenPosition);
                currentInlineObject = inlineObjectIt.hasNext() ? inlineObjectIt.next() : null;
            }

            // Index our word
            finder.setFragPos(FragmentPosition.BEFORE_OPEN_TAG);
            beginWord();

            // For each configured annotation...
            int lastValuePositionAllAnnots = -1; // keep track of last value position so we can update lagging annotations
            for (ConfigAnnotation annotation: annotatedField.getAnnotations().values()) {
                AnnotationWriter annotWriter = getAnnotation(annotation.getName());
                processAnnotation(annotation, nav, tokenPosition);

                // last value position
                int lvp = annotWriter.lastValuePosition();
                if (lastValuePositionAllAnnots < lvp) {
                    lastValuePositionAllAnnots = lvp;
                }
            }

            finder.setFragPos(FragmentPosition.AFTER_CLOSE_TAG);
            endWord();

            // Add empty values to all lagging annotations with a forward index
            for (AnnotationWriter prop: annotatedFieldWriter.annotationWriters()) {
                if (prop.hasForwardIndex() || prop == annotatedFieldWriter.mainAnnotation()) {
                    while (prop.lastValuePosition() < lastValuePositionAllAnnots) {
                        prop.addValue("");
                        if (prop.hasPayload())
                            prop.addPayload(null);
                        if (prop == annotatedFieldWriter.mainAnnotation())
                            annotatedFieldWriter.addFinalStartEndChars();
                    }
                }
            }
        }
        if (apTokenId != null)
            finder.releaseExpression(apTokenId);
        finder.navpop();

        // Index any inline objects after last word
        tokenPosition.setTokenPosition(getCurrentTokenPosition());
        while (currentInlineObject != null) {
            handleInlineObject(currentInlineObject, tokenPositionsMap, tokenPosition);
            currentInlineObject = inlineObjectIt.hasNext() ? inlineObjectIt.next() : null;
        }
    }

    private void collectPunctuation(ConfigAnnotatedField annotatedField, List<InlineObject> inlineObjects) {
        setAddDefaultPunctuation(true);
        if (annotatedField.getPunctPath() != null) {
            // We have punctuation occurring between word tags (as opposed to
            // punctuation that is tagged as a word itself). Collect this punctuation.
            setAddDefaultPunctuation(false);
            xpathForEach(annotatedField.getPunctPath(), null, (punct) -> {
                String punctStr = finder.currentNodeToString(punct);
                // If punctPath matches an empty tag, replace it with a space.
                // Deals with e.g. <lb/> (line break) tags in TEI.
                if (punctStr.isEmpty())
                    punctStr = " ";
                String text = punctStr;
                VTDNav nav = finder.getNav();
                int i = nav.getCurrentIndex();
                int offset = nav.getTokenOffset(i);

                // Make sure we only keep 1 copy of identical punct texts in memory
                text = StringUtil.normalizeWhitespace(text).intern();

                // Add the punct to the list
                inlineObjects.add(new InlineObject(text, offset, InlineObjectType.PUNCTUATION, null));
            });
        }
    }

    private void collectInlineTags(ConfigAnnotatedField annotatedField, List<InlineObject> inlineObjects) {
        int i = 0;
        for (ConfigInlineTag inlineTag : annotatedField.getInlineTags()) {
            if (inlineTag.hasDetailedAttributeConfig())
                warn("Detailed inline tag attribute configuration not supported in VTD indexer. Ignoring for tag: " +
                        inlineTag.getPath() + " (for support, add 'processor: saxon' to .blf.yaml file)");
            // Collect the occurrences of this inline tag
            String tokenIdXPath = inlineTag.getTokenIdPath();
            // We want to capture token ids for this inline tag. Create the AutoPilot.
            xpathForEach(inlineTag.getPath(), null, (tag) -> {
                collectInlineTag(inlineObjects, tokenIdXPath);
            });
            i++;
        }
    }

    /**
     * Add open and close InlineObject objects for the current element to the list.
     *
     * @param inlineObjects   list to add the new open/close tag objects to
     * @param tokenIdXPath    for capturing tokenId, or null if we don't want to capture token id
     */
    private void collectInlineTag(List<InlineObject> inlineObjects, String tokenIdXPath) {

        VTDNav nav = finder.getNav();

        // Get the element and content fragments
        // (element fragment = from start of start tag to end of end tag;
        //  content fragment = from end of start tag to start of end tag)
        long elementFragment;
        long contentFragment;
        String elementName;
        try {
            elementFragment = nav.getElementFragment();
            contentFragment = nav.getContentFragment();
            elementName = nav.toString(nav.getCurrentIndex()).intern();
        } catch (NavException e) {
            throw new BlackLabRuntimeException(e);
        }
        int startTagOffset = (int) elementFragment; // 32 least significant bits are the start offset
        int endTagOffset;
        if (contentFragment == -1) {
            // Empty (self-closing) element.
            endTagOffset = startTagOffset;
        } else {
            // Regular element with separate open and close tags.
            int contentOffset = (int) contentFragment;
            int contentLength = (int) (contentFragment >> 32);
            endTagOffset = contentOffset + contentLength;
        }

        // Capture tokenId if needed
        String tokenId = tokenIdXPath == null ? null : xpathValue(tokenIdXPath, nav);

        // Add the inline tags to the list
        InlineObject openTag = new InlineObject(elementName, startTagOffset, InlineObjectType.OPEN_TAG,
                finder.getAttributes(), tokenId);
        InlineObject closeTag = new InlineObject(elementName, endTagOffset, InlineObjectType.CLOSE_TAG,
                null);
        inlineObjects.add(openTag);
        inlineObjects.add(closeTag);
    }

    private void handleInlineObject(InlineObject inlineObject, Map<String, Span> tokenPositionsMap, Span tokenPosition) {
        if (inlineObject.type() == InlineObjectType.PUNCTUATION) {
            punctuation(inlineObject.getText());
        } else {
            inlineTag(inlineObject.getText(), inlineObject.type() == InlineObjectType.OPEN_TAG,
                    inlineObject.getAttributes());
            if (inlineObject.getTokenId() != null) {
                // Add this open tag's token position (position of the token after the open tag, actually)
                // to the tokenPositionsMap so we can refer to this position later. Useful for e.g. tei:anchor.
                tokenPositionsMap.put(inlineObject.getTokenId(), tokenPosition.copy());
            }
        }
    }

    //---- PROCESS ANNOTATION

    /**
     * Process an annotation at the current position.
     *
     * If this is a span annotation (spanEndPos >= 0), and the span looks like this:
     * <code>&lt;named-entity type="person"&gt;Santa Claus&lt;/named-entity&gt;</code>,
     * then spanName should be "named-entity" and annotation name should be "type" (and
     * its XPath expression should evaluate to "person", obviously).
     *
     * @param annotation   annotation to process.
     * @param positionSpanEndOrSource     position to index at
     * @param spanEndOrRelTarget   if >= 0, index as a span annotation with this end position (exclusive)
     * @param handler      call handler for each value found, including that of subannotations
     */
    protected void processAnnotation(ConfigAnnotation annotation, VTDNav word,
            Span positionSpanEndOrSource, Span spanEndOrRelTarget,
            AnnotationHandler handler) {
        if (StringUtils.isEmpty(annotation.getValuePath()))
            return; // assume this will be captured using forEach

        String basePath = annotation.getBasePath();
        try {
            if (basePath != null) {
                // Basepath given. Navigate to the (first) matching element and evaluate the other XPaths from there.
                // FIXME (?) why only first? shouldn't we process all matches?
                finder.navpush();
                AutoPilot apBase = finder.acquireExpression(basePath);
                apBase.evalXPath();
                finder.releaseExpression(apBase);
            }
            try {
                processAnnotationWithinBasePath(annotation, word, positionSpanEndOrSource, spanEndOrRelTarget, handler);
            } finally {
                if (basePath != null) {
                    // We pushed when we navigated to the base element; pop now.
                    finder.navpop();
                }
            }
        } catch (VTDException e) {
            throw new BlackLabRuntimeException(e);
        }
    }

    @Override
    public void indexSpecificDocument(String documentXPath) {
        if (inputDocument.length > 0) { // VTD doesn't like empty documents
            parseFile();
            super.indexSpecificDocument(documentXPath);
        }
    }

    @Override
    protected void startDocument() {
        super.startDocument();

        try {
            long fragment = finder.getNav().getElementFragment();
            documentByteOffset = (int) fragment;
            documentLengthBytes = (int) (fragment >> 32);
        } catch (NavException e) {
            throw BlackLabRuntimeException.wrap(e);
        }

        lastCharPosition = 0;
        lastCharPositionByteOffset = documentByteOffset;
    }

    @Override
    protected void storeDocument() {
        storeWholeDocument(TextContent.from(inputDocument, documentByteOffset,
                documentLengthBytes, StandardCharsets.UTF_8));
    }

    @Override
    protected int getCharacterPosition() {
        // VTD-XML provides no way of getting the current character position,
        // only the byte position.
        // In order to keep track of character position (which we need for Lucene's term vector),
        // we fetch the bytes processed since this method was last called, convert them to a String,
        // and use the string length to adjust the character position.
        // Note that this only works if this method is called for increasing byte positions,
        // which is true because we only use it for word tags.
        int currentByteOffset = finder.getCurrentByteOffset();
        if (currentByteOffset > lastCharPositionByteOffset) {
            int length = currentByteOffset - lastCharPositionByteOffset;
            String str = new String(inputDocument, lastCharPositionByteOffset, length, StandardCharsets.UTF_8);
            lastCharPosition += str.length();
            lastCharPositionByteOffset = currentByteOffset;
        }
        return lastCharPosition;
    }
}
