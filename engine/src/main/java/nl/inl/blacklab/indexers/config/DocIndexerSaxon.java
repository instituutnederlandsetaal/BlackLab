package nl.inl.blacklab.indexers.config;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.xml.sax.SAXException;

import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.TreeInfo;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.exceptions.MalformedInputFile;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.indexers.config.saxon.CharPosTrackingContentHandler;
import nl.inl.blacklab.indexers.config.saxon.CharPosTrackingReader;
import nl.inl.blacklab.indexers.config.saxon.SaxonHelper;
import nl.inl.blacklab.indexers.config.saxon.XPathFinder;
import nl.inl.blacklab.indexers.config.saxon.XmlDocRef;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.util.FileReference;

/**
 * An indexer capable of XPath version supported by the provided saxon library.
 */
public class DocIndexerSaxon extends DocIndexerXPath<NodeInfo> {

    public static final String PROCESSOR_NAME = "saxon";

    public static final int INITIAL_LIST_SIZE_INLINE_TAGS = 500;

    public static final int INITIAL_CAPACITY_PER_WORD_COLLECTIONS = 3;

    /**
     * How we collect inline tags and (optionally) their token ids (for standoff annotations)
     */
    private record InlineInfo(NodeInfo nodeInfo, String tokenId, ConfigInlineTag config)
        implements Comparable<InlineInfo> {

        @Override
        public int compareTo(InlineInfo o) {
            return nodeInfo.compareOrder(o.nodeInfo);
        }

        public int compareOrder(NodeInfo word) {
            return nodeInfo.compareOrder(word);
        }

        public boolean indexAttribute(String name) {
            ConfigAttribute attr = config.getAttributes().get(name);
            if (attr != null)
                return !attr.isExclude();
            return config.isDefaultIndexAttributes();
        }
    }

    /** Our document (in memory or on disk). */
    private XmlDocRef document;

    /** The parsed document. */
    private TreeInfo contents;

    /** Can calculate character position for a given line/column position. */
    private CharPosTrackingReader charPositions;

    /** Current character position in the current document */
    private long charPos = 0;

    /** Start character position of the current document (within the input file).
     *  Only really relevant if input file contains multiple documents to be indexed.
     */
    private long docStartPos = 0;

    /** End character position of the current document (within the input file).
     *  Only relevant if input file contains multiple documents to be indexed.
     */
    private long docEndPos = -1;

    /** Start position of the current doc version we're indexing,
     *  relative to docStartPos. */
    private long docVersionStartPos = 0;

    /** XPath util functions and caching of XPathExpressions */
    private XPathFinder finder;

    /** Directory from which to resolve relative XIncludes. */
    private File currentXIncludeDir = new File(".");

    @Override
    public void setDocumentDirectory(File dir) {
        this.currentXIncludeDir = dir.getAbsoluteFile();
    }

    @Override
    public void setDocument(FileReference file) {
        cleanupPreviousInputFile();
        super.setDocument(file);
        document = XmlDocRef.fromFileReference(file);
    }

    private void readDocument() {
        try {
            // Should we enable (primitive) XInclude processing?
            // Note that our support is not standards compliant; we just
            // recognize xi:include elements using regex and substitute the
            // referenced file, all before XML parsing happens.
            // XInclude processing incurs an overhead, so it's best to only enable it when needed.
            if (config.getFileTypeOptions().getOrDefault("enableXInclude", "").equalsIgnoreCase("true"))
                document.setXIncludeDirectory(currentXIncludeDir);

            // Now parse the document
            // (our special reader will capture the character positions for each tag while parsing)
            try (Reader reader = document.getDocumentReader()) {
                try {
                    this.charPositions = new CharPosTrackingReader(reader);
                    contents = SaxonHelper.parseDocument(charPositions, new CharPosTrackingContentHandler(charPositions));
                } finally {
                    this.charPositions.close();
                }
            }
            XPath xPath = SaxonHelper.getXPathFactory().newXPath();
            finder = new XPathFinder(xPath,
                    config.isNamespaceAware() ? config.getNamespaces() : null);
        } catch (IOException | XPathException | SAXException | ParserConfigurationException e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    @Override
    protected void xpathForEach(String xPath, NodeInfo context, NodeHandler<NodeInfo> handler) {
        finder.xpathForEach(xPath, context, handler);
    }

    @Override
    protected void xpathForEachStringValue(String xPath, NodeInfo context, StringValueHandler handler) {
        finder.xpathForEachStringValue(xPath, context, handler);
    }

    @Override
    protected String xpathValue(String xpath, NodeInfo context) {
        return finder.xpathValue(xpath, context);
    }

    @Override
    protected String xpathXml(String xpath, NodeInfo context) {
        return finder.xpathXml(xpath, context);
    }

    @Override
    protected String currentNodeXml(NodeInfo node) {
        return finder.currentNodeXml(node);
    }

    @Override
    protected NodeInfo contextNodeWholeDocument() {
        return contents.getRootNode();
    }

    @Override
    public void index() throws MalformedInputFile, PluginException, IOException {
        super.index();
        indexParsedFile(config.getDocumentPath(), false);
    }

    @Override
    protected boolean indexParsedFile(String docXPath, boolean mustBeSingleDocument) {
        readDocument();
        return super.indexParsedFile(docXPath, mustBeSingleDocument);
    }

    // process annotated field

    Map<ConfigAnnotatedField, Pair<Long, Long>> docStartEndOffsetsPerField = new HashMap<>();

    @Override
    protected void indexDocument(NodeInfo doc) {
        CharPosTrackingReader.StartEndPos nodeStartEnd = charPositions.getNodeStartEnd(doc);
        docStartPos = nodeStartEnd.getStartPos();
        docEndPos = nodeStartEnd.getEndPos();
        super.indexDocument(doc);
    }

    @Override
    protected void startDocument() {
        super.startDocument();
        docStartEndOffsetsPerField.clear();
    }

    protected void processAnnotatedFieldContainer(NodeInfo container, ConfigAnnotatedField annotatedField,
            Map<String, Span> tokenPositionsMap) {

        // Is this a parallel corpus annotated field?
        docVersionStartPos = 0;
        if (AnnotatedFieldNameUtil.isParallelField(annotatedField.getName())) {
            // Yes; determine boundaries of this annotated field container so we can later store
            // this version of the document in the field's content store.
            // (so we can retrieve only the desired version of the document later, e.g. only the Dutch version)
            CharPosTrackingReader.StartEndPos nodeStartEnd = charPositions.getNodeStartEnd(container);
            docVersionStartPos = nodeStartEnd.getStartPos() - docStartPos;
            long docVersionEndPos = nodeStartEnd.getEndPos() - docStartPos;
            docStartEndOffsetsPerField.put(annotatedField, Pair.of(docVersionStartPos, docVersionEndPos));
        }

        // Collect information outside word tags:

        // - Punctuation may occur between word tags, which we want to capture
        Iterator<NodeInfo> punctIt = collectPunctuation(container, annotatedField).iterator();
        NodeInfo currentPunct = punctIt.hasNext() ? punctIt.next() : null;

        // - "inline tags" (e.g. b, i, named-entity) can occur between words
        Iterator<InlineInfo> inlineIt = collectInlineTags(container, annotatedField).iterator();
        InlineInfo currentInline = inlineIt.hasNext() ? inlineIt.next() : null;

        // Keep track of where we need to close inline tags we've opened.
        Map<Span, List<NodeInfo>> inlinesToClose = new HashMap<>();

        // For each word...
        Span tokenPosition = Span.token(0);
        List<NodeInfo> words = finder.findNodes(annotatedField.getWordsPath(), container);
        words.sort(NodeInfo::compareOrder); // (or does Saxon guarantee that matching nodes are already in order? maybe check)
        for (NodeInfo word: words) {
            // Index any punctuation occurring before this word
            while (currentPunct != null) {
                if (currentPunct.compareOrder(word) != -1)
                    break; // follows word, we'll index it later
                handlePunct(currentPunct);
                currentPunct = punctIt.hasNext() ? punctIt.next() : null;
            }

            // Index any inline open tags occurring before this word
            while (currentInline != null) {
                if (currentInline.compareOrder(word) != -1)
                    break; // follows word, we'll index it later
                handleInlineOpenTag(annotatedField, inlinesToClose, currentInline, tokenPosition, word, tokenPositionsMap);
                currentInline = inlineIt.hasNext() ? inlineIt.next() : null;
            }

            // Index our word
            CharPosTrackingReader.StartEndPos nodeStartEnd = charPositions.getNodeStartEnd(word);
            charPos = nodeStartEnd.getStartPos() - docStartPos;
            beginWord();

            // For each configured annotation...
            for (ConfigAnnotation annotation: annotatedField.getAnnotations().values()) {
                processAnnotation(annotation, word, tokenPosition);
            }

            charPos = nodeStartEnd.getEndPos() - docStartPos;
            endWord();

            // Make sure we close inline tags at the correct position
            List<NodeInfo> closeHere = inlinesToClose.getOrDefault(tokenPosition, Collections.emptyList());
            for (int i = closeHere.size() - 1; i >= 0; i--) {
                NodeInfo inlineTag = closeHere.get(i);
                inlineTag(inlineTag.getDisplayName(), false, null);
            }
            inlinesToClose.remove(tokenPosition);

            // Capture token id if needed (for standoff annotations)
            if (annotatedField.getTokenIdPath() != null) {
                String tokenId = xpathValue(annotatedField.getTokenIdPath(), word);
                if (tokenId != null)
                    tokenPositionsMap.put(tokenId, tokenPosition.copy());
            }

            tokenPosition.increment();
        }
        if (!inlinesToClose.isEmpty()) {
            throw new RuntimeException(String.format("unclosed inlines left: %s ", inlinesToClose.values()));
        }
        // Index any punctuation occurring after last word
        while (currentPunct != null) {
            handlePunct(currentPunct);
            currentPunct = punctIt.hasNext() ? punctIt.next() : null;
        }
    }

    private List<NodeInfo> collectPunctuation(NodeInfo container, ConfigAnnotatedField annotatedField) {
        setAddDefaultPunctuation(true);
        if (annotatedField.getPunctPath() != null) {
            // We have punctuation occurring between word tags (as opposed to
            // punctuation that is tagged as a word itself). Collect this punctuation.
            setAddDefaultPunctuation(false);
            List<NodeInfo> puncts = finder.findNodes(annotatedField.getPunctPath(), container);
            puncts.sort(NodeInfo::compareOrder);
            return puncts;
        }
        return Collections.emptyList();
    }

    private List<InlineInfo> collectInlineTags(NodeInfo container, ConfigAnnotatedField annotatedField) {
        List<InlineInfo> inlines = new ArrayList<>(INITIAL_LIST_SIZE_INLINE_TAGS);
        for (ConfigInlineTag inlineTag: annotatedField.getInlineTags()) {
            String tokenIdXPath = inlineTag.getTokenIdPath();
            xpathForEach(inlineTag.getPath(), container, (tag) -> {
                String tokenId = tokenIdXPath == null ? null : xpathValue(tokenIdXPath, tag);
                inlines.add(new InlineInfo(tag, tokenId, inlineTag));
            });
        }
        Collections.sort(inlines);
        return inlines;
    }

    private void handlePunct(NodeInfo currentPunct) {
        // Punct precedes word
        String punct = currentPunct.getStringValue();
        punctuation(punct == null ? " " : punct);
    }

    private void handleInlineOpenTag(ConfigAnnotatedField annotatedField, Map<Span, List<NodeInfo>> inlinesToClose,
            InlineInfo currentInline, Span position, NodeInfo word, Map<String, Span> tokenPositionsMap) {
        /*
        - index open tag
        - remember after which word the close tag occurs
        - index word(s)
        - index closing tags(s) at the right position
         */

        // Check if this word is within the inline, if so this word will always be the first word in
        // the inline because we only process each inline once.
        NodeInfo nodeInfo = currentInline.nodeInfo();
        boolean isDescendant = false;
        NodeInfo next;
        try (AxisIterator descendants = nodeInfo.iterateAxis(Axis.DESCENDANT.getAxisNumber())) {
            while ((next = descendants.next()) != null) {
                if (next.equals(word)) {
                    isDescendant = true;
                    break;
                }
            }
        }
        int firstWordOutsideInline;
        if (isDescendant) {
            // Yes, word is a descendant.   (i.e. not a self-closing inline tag?)
            // Find the attributes and index the tag.
            Map<String, List<String>> atts = new HashMap<>(INITIAL_CAPACITY_PER_WORD_COLLECTIONS);
            try (AxisIterator attributes = nodeInfo.iterateAxis(Axis.ATTRIBUTE.getAxisNumber())) {
                while ((next = attributes.next()) != null) {
                    if (currentInline.indexAttribute(next.getDisplayName())) {
                        atts.put(next.getLocalPart(), List.of(next.getStringValue()));
                    }
                }
            }
            // Index any extra attributes using the provided XPath expressions.
            for (ConfigAttribute attribute: currentInline.config.getAttributes().values()) {
                if (attribute.isExclude())
                    continue;
                String value;
                if (atts.containsKey(attribute.getName())) {
                    // Actual attribute on tag. Apply any processing steps now.
                    value = atts.get(attribute.getName()).get(0);
                } else {
                    // Extra attribute, not on tag. Evaluate XPath expression.
                    value = xpathValue(attribute.getValuePath(), nodeInfo);
                }
                List<String> values = processStringMultipleValues(value, attribute.getProcess());
                if (!values.isEmpty()) {
                    atts.put(attribute.getName(), values);
                } else {
                    // Remove attribute if it was already present but now has no values.
                    atts.remove(attribute.getName());
                }
            }
            inlineTag(nodeInfo.getDisplayName(), true, atts);

            // Add tag to the list of tags to close at the correct position.
            // (calculate word position by determining the number of word tags inside this element)
            String xpNumberOfWordsInsideTag = "count(" + annotatedField.getWordsPath() + ")";
            int numberOfWordsInsideTag = Integer.parseInt(xpathValue(xpNumberOfWordsInsideTag, nodeInfo));
            // close inline after the last word that's contained in it (position + numberOfWordsInsideTag - 1)
            inlinesToClose.computeIfAbsent(position.plus(numberOfWordsInsideTag - 1),
                            k -> new ArrayList<>(INITIAL_CAPACITY_PER_WORD_COLLECTIONS))
                    .add(nodeInfo);
            firstWordOutsideInline = position.start() + numberOfWordsInsideTag;
        } else {
            // Word is not a descendant, so this inline must be self-closing.
            // In other words, the length of the inline is 0.
            firstWordOutsideInline = position.start();
        }

        if (currentInline.tokenId() != null)
            tokenPositionsMap.put(currentInline.tokenId(), Span.between(position.start(), firstWordOutsideInline));
    }


    //---- PROCESS ANNOTATION

    /**
     * Process an annotation at the current position.
     * <p>
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
    protected void processAnnotation(ConfigAnnotation annotation, NodeInfo word,
            Span positionSpanEndOrSource, Span spanEndOrRelTarget,
            AnnotationHandler handler) {
        if (StringUtils.isEmpty(annotation.getValuePath()))
            return; // assume this will be captured using forEach

        if (annotation.getBasePath() != null) {
            for (NodeInfo baseNode: finder.findNodes(annotation.getBasePath(), word)) {
                processAnnotationWithinBasePath(annotation, baseNode, positionSpanEndOrSource, spanEndOrRelTarget, handler);
            }
        } else {
            processAnnotationWithinBasePath(annotation, word, positionSpanEndOrSource, spanEndOrRelTarget, handler);
        }
    }

    @Override
    protected void storeDocument() {
        if (docStartEndOffsetsPerField.isEmpty()) {
            // Regular, non-parallel corpus. Store whole document.
            storeWholeDocument(document.getTextContent(docStartPos, docEndPos));
        } else {
            // Parallel corpus. Store each version of the document with its field.
            docStartEndOffsetsPerField.entrySet().stream()
                    .sorted(Comparator.comparing(a -> a.getValue().getLeft()))
                    .forEach(entry -> {
                        Long startOffset = entry.getValue().getLeft() + docStartPos;
                        Long endOffset = entry.getValue().getRight() + docStartPos;
                        storeContent(entry.getKey(), document.getTextContent(startOffset, endOffset));
                    });
        }
    }

    private void cleanupPreviousInputFile() {
        if (document != null) {
            document.clean();
            document = null;
        }
        // make sure we don't hold on to memory needlessly
        charPositions = null;
        contents = null;
    }

    @Override
    public void close() {
        cleanupPreviousInputFile();
        super.close();
    }

    @Override
    protected int getCharacterPosition() {
        return (int)charPos;
    }

    @Override
    protected int getCharacterPositionWithinVersion() {
        return (int)(charPos - docVersionStartPos);
    }

}
