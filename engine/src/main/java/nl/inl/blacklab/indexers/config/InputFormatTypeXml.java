package nl.inl.blacklab.indexers.config;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.lucene.util.BytesRef;

import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.AxisIterator;
import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.exceptions.ErrorIndexingFile;
import nl.inl.blacklab.exceptions.InvalidConfiguration;
import nl.inl.blacklab.index.DocWriter;
import nl.inl.blacklab.index.IndexerStats;
import nl.inl.blacklab.index.InputFormat;
import nl.inl.blacklab.index.annotated.AnnotationWriter;
import nl.inl.blacklab.indexers.config.process.ProcessingStep;
import nl.inl.blacklab.indexers.config.saxon.SaxonDocumentWithElementOffsets;
import nl.inl.blacklab.indexers.config.saxon.SaxonHelper;
import nl.inl.blacklab.indexers.config.saxon.XPathFinder;
import nl.inl.blacklab.indexers.config.saxon.XmlDocRef;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.RelationUtil;
import nl.inl.blacklab.search.indexmetadata.RelationsStrategy;
import nl.inl.blacklab.search.indexmetadata.RelationsStrategySeparateTerms;
import nl.inl.util.StringUtil;
import nl.inl.util.fileprocessor.FileReference;

public class InputFormatTypeXml extends InputFormatTypeConfig {

    public static final String FT_OPT_PROCESSOR = "processor";
    public static final String PROCESSOR_NAME = "saxon";
    public static final int INITIAL_LIST_SIZE_INLINE_TAGS = 500;
    public static final int INITIAL_CAPACITY_PER_WORD_COLLECTIONS = 3;

    /** When referring to unresolved token ids, what prefix should we use to avoid too many similar warnings? */
    private static final int TOKEN_ID_PREFIX_LENGTH = 7;

    public interface NodeHandler {
        void handle(NodeInfo node);
    }

    public interface XdmValueHandler {
        void handle(XdmValue value);
    }

    public interface StringValueHandler {
        void handle(String value);
    }

    /**
     * Can this subannotation reuse values from its parent?
     * This is often the case with part of speech annotations, where the parent might
     * capture an expression describing all the features, and subannotations might isolate
     * individual features from this expression using processing steps.
     * This only works if all options are the same.
     *
     * @param subannotation    the subannotation to index to
     * @param valuePath        XPath expression for value to index
     * @param parentAnnotation the parent annotation
     */
    protected static boolean canReuseParentValues(ConfigAnnotation subannotation, String valuePath,
            ConfigAnnotation parentAnnotation) {
        return valuePath.equals(parentAnnotation.getValuePath()) &&
                subannotation.isCaptureXml() == parentAnnotation.isCaptureXml();
    }

    @Override
    public InputFormat createInputFormat(ConfigInputFormat config) {
        return new InputFormatXPath(config);
    }

    public class InputFormatXPath extends InputFormatConfig {

        public InputFormatXPath(ConfigInputFormat config) {
            super(config);
        }

        @Override
        protected Doc createDoc(DocWriter docWriter, FileReference file) {
            return new DocXPath(docWriter, file);
        }

        protected class DocXPath extends DocConfig {

            /** Start and end character offsets in the document for each annotated field */
            Map<ConfigAnnotatedField, Pair<Long, Long>> docStartEndOffsetsPerField = new HashMap<>();

            /** Our document (in memory or on disk). */
            private XmlDocRef document;

            private SaxonDocumentWithElementOffsets parsedDocument;
           
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

            public DocXPath(DocWriter docWriter, FileReference file) {
                super(docWriter, file);
            }

            /**
             * Process an annotation at the current position.
             * <p>
             * If this is a span annotation (spanEndPos >= 0), and the span looks like this:
             * <code>&lt;named-entity type="person"&gt;Santa Claus&lt;/named-entity&gt;</code>,
             * then spanName should be "named-entity" and annotation name should be "type" (and
             * its XPath expression should evaluate to "person", obviously).
             *
             * @param annotation   annotation to process.
             * @param word         the context node or value for XPath evaluation
             * @param positionSpanEndOrSource     position to index at
             * @param spanEndOrRelTarget   if >= 0, index as a span annotation with this end position (exclusive)
             * @param handler      call handler for each value found, including that of subannotations
             */
            protected void processAnnotation(ConfigAnnotation annotation, XdmValue word,
                    Span positionSpanEndOrSource, Span spanEndOrRelTarget,
                    AnnotationHandler handler) {
                if (StringUtils.isEmpty(annotation.getValuePath()))
                    return; // assume this will be captured using forEach

                if (annotation.getBasePath() != null) {
                    for (XdmItem item : finder.find (annotation.getBasePath(), word)) {
                        processAnnotationWithinBasePath(annotation, XdmValue.wrap(item.getUnderlyingValue()), positionSpanEndOrSource, spanEndOrRelTarget, handler);
                    }
                } else {
                    processAnnotationWithinBasePath(annotation, word, positionSpanEndOrSource, spanEndOrRelTarget, handler);
                }
            }

            @Override
            public void storeDocument() {
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
                parsedDocument = null;
            }

            @Override
            public void close() {
                cleanupPreviousInputFile();
            }

            @Override
            protected int getCharacterPosition() {
                return (int)charPos;
            }

            @Override
            protected int getCharacterPositionWithinVersion() {
                return (int)(charPos - docVersionStartPos);
            }

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
                    
                    // Use the shared Saxon configuration from SaxonHelper so that the document tree
                    // is compatible with XPath expressions compiled using the same processor.
                    this.parsedDocument = SaxonHelper.parseDocument(document.getDocumentReader(), config.isNamespaceAware());

                    Map<String, String> vars = Map.of("inputFilePath", documentName);
                    finder = new XPathFinder(config.isNamespaceAware() ? config.getNamespaces() : null, vars);
                } catch (IOException | XPathException | XMLStreamException e) {
                    throw BlackLabException.wrapRuntime(e);
                }
            }

            /**
             * Process an annotation at the current position.
             *
             * @param annotation   annotation to process.
             * @param word         context for XPath evaluation
             * @param positionSpanStartOrRelSource     position to index at
             */
            protected void processAnnotation(ConfigAnnotation annotation, XdmValue word, Span positionSpanStartOrRelSource) {
                processAnnotation(annotation, word, positionSpanStartOrRelSource, null, this::indexAnnotationValues);
            }

            protected void processAnnotatedFieldContainer(NodeInfo container, ConfigAnnotatedField annotatedField,
                    Map<String, Span> tokenPositionsMap) {

                // Is this a parallel corpus annotated field?
                docVersionStartPos = 0;
                if (AnnotatedFieldNameUtil.isParallelField(annotatedField.getName())) {
                    // Yes; determine boundaries of this annotated field container so we can later store
                    // this version of the document in the field's content store.
                    // (so we can retrieve only the desired version of the document later, e.g. only the Dutch version)
                    long nodeStart = parsedDocument.getElementStartCharOffset(container);
                    long nodeEnd = parsedDocument.getElementEndCharOffset(container);
                    
                    docVersionStartPos = nodeStart - docStartPos;
                    long docVersionEndPos = nodeEnd - docStartPos;
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
                List<NodeInfo> words = finder.findNodes(annotatedField.getWordPath(), container);
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
                    charPos = parsedDocument.getElementStartCharOffset(word) - docStartPos;
                    beginWord();

                    // For each configured annotation...
                    for (ConfigAnnotation annotation: annotatedField.getAnnotations()) {
                        processAnnotation(annotation, XdmValue.wrap(word), tokenPosition);
                    }

                    charPos = parsedDocument.getElementEndCharOffset(word) - docStartPos;
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
                        String tokenId = finder.xpathValue(annotatedField.getTokenIdPath(), word);
                        if (tokenId != null)
                            tokenPositionsMap.put(tokenId, tokenPosition.copy());
                    }

                    tokenPosition.increment();
                }
                if (!inlinesToClose.isEmpty()) {
                    throw new IllegalStateException(String.format("unclosed inlines left: %s ", inlinesToClose.values()));
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
                    finder.xpathForEach(inlineTag.getPath(), container, (tag) -> {
                        String tokenId = tokenIdXPath == null ? null : finder.xpathValue(tokenIdXPath, tag);
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
                        List<String> values = new ArrayList<>();
                        ProcessingStep processSteps = attribute.getCompiledProcessSteps();
                        if (atts.containsKey(attribute.getName())) {
                            // Actual attribute on tag. Apply any processing steps now.
                            String attributeValue = atts.get(attribute.getName()).get(0);
                            values.addAll(processStringMultipleValues(attributeValue, processSteps));
                        } else {
                            // Extra attribute, not on tag. Evaluate XPath expression.
                            finder.xpathForEachStringValue(attribute.getValuePath(), nodeInfo, matchedValue -> {
                                values.addAll(processStringMultipleValues(matchedValue, processSteps));
                            });
                        }
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
                    String xpNumberOfWordsInsideTag = "count(" + annotatedField.getWordPath() + ")";
                    int numberOfWordsInsideTag = Integer.parseInt(finder.xpathValue(xpNumberOfWordsInsideTag, nodeInfo));
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

            /**
             * Process a standoff annotation at the current position.
             *
             * @param standoffNode current node
             * @param type        the type of the standoff annotation (token, span or relation)
             * @param sourceSpan if this is a relation, this span is the source of that relation; otherwise,
             *                   the .start() of this span object is the start of the span to index
             * @param targetSpan if this is a relation, this span is the target of that relation; otherwise,
             *                   the .start() of this span object is the end of the span to index
             * @param standoffAnnotations any annotations to index as attributes of the span or relation
             * @param spanOrRelType the type name of the span or relation (e.g. "named-entity", "nsubj", etc.)
             */
            protected void processStandoffSpan(NodeInfo standoffNode, AnnotationType type,
                    Span sourceSpan, Span targetSpan, Collection<ConfigAnnotation> standoffAnnotations,
                    String spanOrRelType) {
                String name = AnnotatedFieldNameUtil.RELATIONS_ANNOT_NAME;
                AnnotationWriter annotationWriter = getAnnotation(name);
                // Integrated index format.

                // Collect any attribute values
                Map<String, List<String>> attributes = new HashMap<>();
                for (ConfigAnnotation annotation: standoffAnnotations) {
                    if (annotation.isForEach()) {
                        warnOnce().warn("Ignoring forEach annotation '" + annotation.getNamePath()
                                + "' in standoff span/relation annotation; only regular annotations are supported here for now.");
                        continue; // forEach annotations are not indexed as attributes (for now)
                    }
                    // NOTE: we pass invalid values for the positions because they don't matter here; we're not indexing,
                    //       just finding XPath matches for the attributes and collecting them.
                    processAnnotation(annotation, XdmValue.wrap(standoffNode), null, null,
                            (annot, pos, spanEndPos, values) -> attributes.put(annot.getName(), values));
                }

                // Determine the full type name to index (relation class and relation type, e.g. "__tag::s" or "dep::nmod")
                String fullType = spanOrRelType;
                if (type == AnnotationType.SPAN)
                    fullType = RelationUtil.fullType(RelationUtil.CLASS_INLINE_TAG, spanOrRelType);

                // Actually index the relation according to our relation indexing strategy (single term / multiple terms)
                // Start of positionSpan gives the position where this will be indexed, unless it's a root relation,
                // which has no source, so we index it at its target.
                int indexAtPosition = sourceSpan.start() >= 0 ? sourceSpan.start() : targetSpan.start();
                RelationsStrategy relationsStrategy = getDocWriter().getRelationsStrategy();
                // For separate terms: always assign relationId even if no attributes, because it is needed for matching
                boolean maybeExtraInfo = relationsStrategy instanceof RelationsStrategySeparateTerms || !attributes.isEmpty();
                BytesRef payload = getPayload(sourceSpan, targetSpan, type, maybeExtraInfo, indexAtPosition);
                relationsStrategy.indexRelationTerms(fullType, attributes, payload,
                        (String valueToIndex, BytesRef payloadThisToken) ->
                                annotationValue(name, valueToIndex, indexAtPosition, payloadThisToken));
            }

            @Override
            protected void startDocument() {
                super.startDocument();
                docStartEndOffsetsPerField.clear();
            }

            protected void processAnnotatedFieldContainerStandoff(NodeInfo container, ConfigAnnotatedField annotatedField, Map<String, Span> tokenPositionsMap) {

                // (separate method because we only run these once all token positions for all fields have been collected,
                //  so parallel corpora can refer to token positions in other fields)

                // Process standoff annotations
                for (ConfigStandoffAnnotations standoff: annotatedField.getStandoffAnnotations()) {
                    processStandoffAnnotation(standoff, container, tokenPositionsMap);
                }
            }

            protected void processStandoffAnnotation(ConfigStandoffAnnotations standoff, NodeInfo container, Map<String, Span> tokenPositionsMap) {
                // For each instance of this standoff annotation..
                AnnotationType type = standoff.getType();
                finder.xpathForEach(standoff.getPath(), container, (standoffNode) -> {
                    // Determine what token positions to index these values at
                    List<Span> indexAtPositions = new ArrayList<>();
                    finder.xpathForEachStringValue(standoff.getTokenRefPath(), standoffNode, (tokenPositionId) -> {
                        if (!tokenPositionId.isEmpty()) {
                            Span span = tokenPositionsMap.get(tokenPositionId);
                            if (span == null) {
                                warnUnresolvedTokenId(tokenPositionId,
                                        "Standoff annotation contains unresolved reference to token position");
                            } else
                                indexAtPositions.add(span);
                        }
                    });

                    Collection<ConfigAnnotation> standoffAnnotations = standoff.getAnnotations();
                    if (type == AnnotationType.TOKEN) {
                        // "Regular" standoff annotation for a single token.
                        // Index annotation values at the position(s) indicated
                        for (ConfigAnnotation annotation: standoffAnnotations) {
                            for (Span position: indexAtPositions) {
                                processAnnotation(annotation, XdmValue.wrap(standoffNode), position);
                            }
                        }
                    } else {

                        // Standoff span or relation annotation. Try to find end/target and type.

                        // end/target
                        Span endOrTarget = Span.invalid();
                        Span[] endOrTargetArr = new Span[] { endOrTarget };
                        finder.xpathForEachStringValue(standoff.getSpanEndPath(), standoffNode, (tokenId) -> {
                            Span tokenPos = tokenPositionsMap.get(tokenId);
                            if (tokenPos == null) {
                                warnUnresolvedTokenId(tokenId,
                                        "Standoff annotation contains unresolved reference to span end token");
                            } else {
                                endOrTargetArr[0] = tokenPos;
                            }
                        });
                        endOrTarget = endOrTargetArr[0];

                        if (Span.isValid(endOrTarget)) {
                            // span end
                            if (type != AnnotationType.RELATION && standoff.isSpanEndIsInclusive()) {
                                // The matched token should be included in the span, but we always store
                                // the first token outside the span as the end. Adjust the position accordingly.
                                endOrTarget = endOrTarget.plus(1); // copy because we mustn't change tokenPositionsMap
                            }

                            // type
                            String spanOrRelType = finder.xpathValue(standoff.getValuePath(), standoffNode);
                            if (type == AnnotationType.RELATION && !spanOrRelType.contains(RelationUtil.CLASS_TYPE_SEPARATOR)) {
                                // If no relation class specified, prepend the configured default relation class.
                                String targetVersion = standoff.getTargetVersionPath().isEmpty() ? "" :
                                        finder.xpathValue(standoff.getTargetVersionPath(), standoffNode);
                                String relationClass = standoff.resolveRelationClass(currentAnnotatedField.name(), targetVersion);
                                spanOrRelType = RelationUtil.fullType(relationClass, spanOrRelType);
                            }

                            if (indexAtPositions.isEmpty()) {
                                if (type != AnnotationType.RELATION) {
                                    warn("Standoff annotation for inline tag has end but no start: "
                                            + standoff.getPath());
                                } else {
                                    // Standoff root relation
                                    processStandoffSpan(standoffNode, type, Span.invalid(), endOrTarget,
                                            standoffAnnotations,
                                            spanOrRelType);
                                }
                            } else {
                                // Standoff annotation to index a relation (or inline tag).
                                for (Span position: indexAtPositions) {
                                    processStandoffSpan(standoffNode, type, position, endOrTarget,
                                            standoffAnnotations,
                                            spanOrRelType);
                                }
                            }
                        }
                    }
                });
            }

            WarnOnce warnOnce() {
                return getDocWriter().warnOnce();
            }

            private void warnUnresolvedTokenId(String tokenId, String baseMessage) {
                // Warn about unresolved reference, but only once per token id prefix.
                // (so e.g. missing document isn't reported a million times)
                String tokenIdPrefix = tokenId.length() > TOKEN_ID_PREFIX_LENGTH ? tokenId.substring(0, TOKEN_ID_PREFIX_LENGTH) : tokenId;
                String tokenIdRest = tokenId.length() > TOKEN_ID_PREFIX_LENGTH ? tokenId.substring(TOKEN_ID_PREFIX_LENGTH) : "";
                warnOnce().warn(baseMessage + ": '" + tokenIdPrefix, tokenIdRest + "'");
            }

            protected void processSubannotations(ConfigAnnotation parentAnnot, XdmValue context,
                    Span positionSpanEndOrSource, Span spanEndOrRelTarget,
                    AnnotationHandler handler, List<String> parentAnnotValues) {
                // For each configured subannotation...
                for (ConfigAnnotation subannot : parentAnnot.getSubannotations()) {
                    // Subannotation configs without a valuePath are just for
                    // adding information about subannotations captured in forEach's,
                    // such as extra processing steps
                    if (subannot.getValuePath() == null || subannot.getValuePath().isEmpty())
                        continue;

                    // Capture this subannotation value
                    if (subannot.isForEach()) {
                        // "forEach" subannotation specification
                        // (allows us to capture multiple subannotations with 3 XPath expressions)
                        finder.xpathForEach(subannot.getForEachPath(), context, (match) -> {
                            // Find the name and value for this forEach match
                            String name = finder.xpathValue(subannot.getNamePath(), match);
                            List<String> result = processValues(subannot.getCompiledNameProcessSteps(),
                                    List.of(name));
                            if (result.size() != 1)
                                throw new InvalidConfiguration("forEach XPath '"
                                        + subannot.getNamePath() + "': nameProcess must return exactly one value; got "
                                        + result.size() + " values.");
                            name = result.get(0);

                            String subannotationName = parentAnnot.getName() + AnnotatedFieldNameUtil.SUBANNOTATION_FIELD_PREFIX_SEPARATOR + name;
                            ConfigAnnotation declSubannot = parentAnnot.getSubannotation(subannotationName);

                            // It's not possible to create annotation on the fly at the moment.
                            // So since this was not declared in the config file, emit a warning and skip.
                            if (declSubannot == null) {
                                if (!skippedAnnotations.contains(subannotationName)) {
                                    skippedAnnotations.add(subannotationName);
                                    logger.error(documentName + ": skipping undeclared annotation " + name + " (" + "as declaredSubannot of forEachPath " + subannot.getNamePath() + ")");
                                }
                                return;
                            }

                            // The forEach xpath matched an annotation that specifies its own valuepath
                            // Skip it as part of the forEach, because it will be processed by itself later.
                            if (declSubannot.getValuePath() != null && !declSubannot.getValuePath().isEmpty()) {
                                return;
                            }

                            // Find annotation matches, process and dedupe and index them.
                            // Can we reuse the values from our parent annotation? Only if all options are the same.
                            findAndIndexSubannotation(subannot, match, declSubannot, positionSpanEndOrSource, spanEndOrRelTarget,
                                    handler, parentAnnot, parentAnnotValues
                            );
                        });
                    } else {
                        // Regular subannotation; just the fieldName and an XPath expression for the value
                        // Find annotation matches, process and dedupe and index them.
                        // Can we reuse the values from our parent annotation? Only if all options are the same.
                        findAndIndexSubannotation(subannot, context, subannot, positionSpanEndOrSource, spanEndOrRelTarget,
                                handler, parentAnnot, parentAnnotValues);
                    }
                }
            }

            protected void findAndIndexSubannotation(ConfigAnnotation toIndex, XdmValue context, ConfigAnnotation indexAs,
                    Span positionSpanEndOrSource, Span spanEndOrRelTarget, AnnotationHandler handler,
                    ConfigAnnotation parent, List<String> parentValues) {
                List<String> unprocessed = !toIndex.isForEach() && canReuseParentValues(indexAs, toIndex.getValuePath(), parent) ?
                        parentValues :
                        findAnnotationMatches(indexAs, toIndex.getValuePath(), context);
                List<String> processedValues = processValues(indexAs.getCompiledProcessSteps(), unprocessed);
                handler.values(indexAs, positionSpanEndOrSource, spanEndOrRelTarget, processedValues);
            }

            protected List<String> findAnnotationMatches(ConfigAnnotation annotation, String valuePath, XdmValue context) {
                // Not the same values as the parent annotation; we have to find our own.
                List<String> values = new ArrayList<>();
                // Multiple matches will be indexed at the same position.
                if (annotation.isCaptureXml()) {
                    finder.xpathForEach(valuePath, context, (value) -> values.add(finder.currentNodeXml((NodeInfo) value.getUnderlyingValue())));
                } else {
                    finder.xpathForEachStringValue(valuePath, context, values::add);
                }
                // No annotations have been added, the result of the xPath query must have been empty.
                if (values.isEmpty())
                    values.add("");
                return values;
            }

            protected void processAnnotationWithinBasePath(ConfigAnnotation annotation, XdmValue word,
                    Span positionSpanEndOrSource, Span spanEndOrRelTarget, AnnotationHandler handler) {
                String valuePath = annotation.getValuePath();
                if (valuePath != null) {
                    // Find annotation matches, process and dedupe and index them.
                    List<String> unprocessedValues = findAnnotationMatches(annotation, valuePath, word);
                    List<String> processedValues = processValues(annotation.getCompiledProcessSteps(), unprocessedValues);
                    handler.values(annotation, positionSpanEndOrSource, spanEndOrRelTarget, processedValues);
                    processSubannotations(annotation, word, positionSpanEndOrSource, spanEndOrRelTarget, handler, unprocessedValues);
                } else {
                    // No valuePath given. Assume this will be captured using forEach.
                }
            }

            /**
             * Index document from the current node.
             */
            protected void indexDocument(NodeInfo doc) {
                docStartPos =  parsedDocument.getElementStartCharOffset(doc);
                docEndPos = parsedDocument.getElementEndCharOffset(doc);
                startDocument();

                // This is where we'll capture token ("word") ids and remember the position associated with each id.
                // In the case to <tei:anchor> between tokens, these are also stored here (referring to the token position after
                // the anchor).
                // This is used for standoff annotations, that refer back to the captured ids to add annotations later.
                // Standoff span annotations are also supported.
                // The full documentation is available here:
                // https://blacklab.ivdnt.org/guide/how-to-configure-indexing.html#standoff-annotations
                Map<String, Span> tokenPositionsMap = new HashMap<>();

                // For each configured annotated field...
                for (ConfigAnnotatedField annotatedField : config.getAnnotatedFields().values()) {
                    if (!annotatedField.isDummyForStoringLinkedDocuments()) {
                        processAnnotatedField(doc, annotatedField, tokenPositionsMap);
                    }
                }
                // Process all the standoffs last, so token positions for all fields have been collected.
                for (ConfigAnnotatedField annotatedField : config.getAnnotatedFields().values()) {
                    if (!annotatedField.isDummyForStoringLinkedDocuments()) {
                        processAnnotatedFieldStandoff(doc, annotatedField, tokenPositionsMap);
                    }
                }

                // For each configured metadata block..
                for (ConfigMetadataBlock b : config.getMetadata()) {
                    processMetadataBlock(doc, b);
                }

                // For each linked document...
                for (ConfigLinkedDocument ld : config.getLinkedDocuments().values()) {
                    processLinkedDocument(ld, xpath -> finder.xpathValue(xpath, doc));
                }

                endDocument();
            }

            @Override
            public IndexerStats indexSpecificDocument(String documentXPath, Doc linkingDoc, String storeWithName) {
                ensureInitialized();
                this.linkingDoc = linkingDoc;
                indexingIntoExistingDoc = true;
                linkedDocumentContentStoreName = storeWithName;
                resetStats();

                final AtomicBoolean docDone = new AtomicBoolean(false);
                try {
                    if (documentXPath != null) {
                        indexParsedFile(documentXPath, true);
                        // Find our specific document in the file
                        finder.xpathForEach(documentXPath, contextNodeWholeDocument(), (doc) -> {
                            if (docDone.get())
                                throw new ErrorIndexingFile(
                                        "Document link " + documentXPath + " matched multiple documents in "
                                                + documentName);
                            indexDocument(doc);
                            docDone.set(true);
                        });
                    } else {
                        // Process whole file; must be 1 document
                        docDone.set(indexParsedFile(config.getDocumentPath(), true));
                    }
                } catch (Exception e1) {
                    throw BlackLabException.wrapRuntime(e1);
                }
                if (!docDone.get())
                    throw new ErrorIndexingFile("Linked document not found in " + documentName);
                return getStats();
            }

            protected void processMetadataBlock(NodeInfo doc, ConfigMetadataBlock metaBlock) {
                // For each instance of this metadata block...
                finder.xpathForEach(metaBlock.getContainerPath(), doc, (block) -> {
                    // For each subblock... (for multiple levels of containerPaths)
                    for (ConfigMetadataBlock subBlock: metaBlock.getBlocks()) {
                        processMetadataBlock(block, subBlock);
                    }

                    // For each configured metadata field...
                    List<ConfigMetadataField> fields = metaBlock.getFields();
                    //noinspection ForLoopReplaceableByForEach
                    for (int i = 0; i < fields.size(); i++) { // NOTE: fields may be added during loop, so can't iterate
                        ConfigMetadataField field = fields.get(i);

                        // Metadata field configs without a valuePath are just for
                        // adding information about fields captured in forEach's,
                        // such as extra processing steps
                        if (field.getValuePath() == null || field.getValuePath().isEmpty())
                            continue;

                        // Capture whatever this configured metadata field points to
                        if (field.isForEach()) {
                            // "forEach" metadata specification
                            // (allows us to capture many metadata fields with 3 XPath expressions)
                            processMetaForEach(metaBlock, block, field);
                        } else {
                            // Regular metadata field; just the fieldName and an XPath expression for the value
                            // Multiple matches will be indexed at the same position.
                            indexMetadataFieldMatches(block, field, field.getName(), null);
                        }
                    }
                });
            }

            protected void processMetaForEach(ConfigMetadataBlock metaBlock, NodeInfo block, ConfigMetadataField forEach) {
                finder.xpathForEach(forEach.getForEachPath(), block, (match) -> {
                    // Find the fieldName and value for this forEach match
                    String fieldName = finder.xpathValue(forEach.getNamePath(), match);
                    List<String> result = processValues(forEach.getCompiledNameProcessSteps(),
                            List.of(fieldName));
                    if (result.size() != 1)
                        throw new InvalidConfiguration("forEach XPath '"
                                + forEach.getNamePath() + "': nameProcess must return exactly one value; got "
                                + result.size() + " values.");
                    fieldName = result.get(0);
                    ConfigMetadataField indexAsField = metaBlock.getOrCreateField(fieldName);

                    // This metadata field is matched by a for-each, but if it specifies its own xpath ignore it in the for-each section
                    // It will capture values on its own at another point in the outer loop.
                    // Note that we check whether there is any path at all: otherwise an identical path to the for-each would capture values twice.
                    if (indexAsField.getValuePath() != null && !indexAsField.getValuePath().isEmpty())
                        return;

                    // Multiple matches will be indexed at the same position.
                    indexMetadataFieldMatches(match, forEach, fieldName, indexAsField);
                });
            }

            protected void indexMetadataFieldMatches(NodeInfo node, ConfigMetadataField field,
                    String indexAsFieldName, ConfigMetadataField indexAsFieldConfig) {
                // NOTE: field may be a forEach, in which case indexAsFieldConfig is the actual field to index as
                finder.xpathForEachStringValue(field.getValuePath(), node, (unprocessedValue) -> {
                    unprocessedValue = StringUtil.sanitizeAndNormalizeUnicode(unprocessedValue);
                    for (String value: processStringMultipleValues(unprocessedValue, field.getCompiledProcessSteps())) {
                        if (indexAsFieldConfig == null) {
                            addMetadataField(indexAsFieldName, value);
                        } else {
                            // Also execute process defined for named metadata field, if any
                            for (String processedValue: processStringMultipleValues(value,
                                    indexAsFieldConfig.getCompiledProcessSteps())) {
                                addMetadataField(indexAsFieldName, processedValue);
                            }
                        }
                    }
                });
            }

            protected void processAnnotatedField(NodeInfo document, ConfigAnnotatedField annotatedField, Map<String, Span> tokenPositionsMap) {
                // Determine some useful stuff about the field we're processing
                // and store in instance variables so our methods can access them
                setCurrentAnnotatedFieldName(annotatedField.getName());

                // For each container (e.g. "text" or "body" element) ...
                finder.xpathForEach(annotatedField.getContainerPath(), document,
                        (container) -> processAnnotatedFieldContainer(container, annotatedField, tokenPositionsMap));
            }

            protected void processAnnotatedFieldStandoff(NodeInfo document, ConfigAnnotatedField annotatedField, Map<String, Span> tokenPositionsMap) {

                // (separate method because we only run these once all token positions for all fields have been collected,
                //  so parallel corpora can refer to token positions in other fields)

                // Determine some useful stuff about the field we're processing
                // and store in instance variables so our methods can access them
                setCurrentAnnotatedFieldName(annotatedField.getName());

                // For each container (e.g. "text" or "body" element) ...
                finder.xpathForEach(annotatedField.getContainerPath(), document,
                        (container) -> processAnnotatedFieldContainerStandoff(container, annotatedField, tokenPositionsMap));
            }

            @Override
            public IndexerStats index() throws ErrorIndexingFile {
                super.index();
                indexParsedFile(config.getDocumentPath(), false);
                return getStats();
            }

            protected boolean indexParsedFile(String docXPath, boolean mustBeSingleDocument) {
                readDocument();
                try {
                    AtomicBoolean docDone = new AtomicBoolean(false); // any doc(s) processed?
                    finder.xpathForEach(docXPath, contextNodeWholeDocument(),(doc) -> {
                        if (mustBeSingleDocument && docDone.get())
                            throw new ErrorIndexingFile(
                                    "Linked file contains multiple documents (and no document path given) in "
                                            + documentName);
                        indexDocument(doc);
                        docDone.set(true);
                    });
                    return docDone.get();
                } catch (InvalidConfiguration e) {
                    throw new InvalidConfiguration(e.getMessage() + String.format("; when indexing file: %s", documentName), e.getCause());
                }
            }

            protected NodeInfo contextNodeWholeDocument() {
                return parsedDocument.getDocument().getRootNode();
            }

            protected interface AnnotationHandler {
                void values(ConfigAnnotation annotation, Span positionSpanEndOrSource, Span spanEndOrRelTarget, List<String> values);
            }

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
        }
    }
}
