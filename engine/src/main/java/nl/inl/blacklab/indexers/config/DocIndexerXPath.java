package nl.inl.blacklab.indexers.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.lucene.util.BytesRef;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.exceptions.ErrorIndexingFile;
import nl.inl.blacklab.exceptions.InvalidConfiguration;
import nl.inl.blacklab.index.annotated.AnnotationWriter;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.RelationUtil;
import nl.inl.blacklab.search.indexmetadata.RelationsStrategy;
import nl.inl.blacklab.search.indexmetadata.RelationsStrategySeparateTerms;
import nl.inl.util.StringUtil;

public abstract class DocIndexerXPath<T> extends DocIndexerConfig {

    private static final Set<String> reportedWarnings = new HashSet<>();

    public static final String FT_OPT_PROCESSOR = "processor";

    private static final String FT_OPT_PROCESSING = "processing"; // old key

    private static final String REGEX_PROCESSOR_SAXON = "saxon(ica)?";

    /** When referring to unresolved token ids, what prefix should we use to avoid too many similar warnings? */
    private static final int TOKEN_ID_PREFIX_LENGTH = 7;

    /** Create a new XPath-based indexer.
     * <p>
     * Chooses XML processor based on the fileTypeOptions.
     *
     * @param fileTypeOptions options, including what processor to use
     * @return indexer
     */
    public static DocIndexerXPath<?> create(Map<String, String> fileTypeOptions) {
        String xmlProcessorName = fileTypeOptions.getOrDefault(FT_OPT_PROCESSOR, "");
        if (xmlProcessorName.isEmpty()) {
            // See if the older version of the key is being used
            xmlProcessorName = fileTypeOptions.getOrDefault(FT_OPT_PROCESSING,
                    BlackLab.featureFlag(BlackLab.FEATURE_DEFAULT_XML_PROCESSOR));
        }
        if (xmlProcessorName.toLowerCase().matches(REGEX_PROCESSOR_SAXON))
            return new DocIndexerSaxon();
        return new DocIndexerVTD(); // VTD was the original default (v2 will automatically set Saxon as default)
    }

    /** Don't issue warnings again if they starts with the same prefix. */
    static synchronized void warnOnce(String message, String prefix) {
        if (!reportedWarnings.contains(prefix)) {
            logger.warn(message);
            logger.warn("  (above warning is only issued once)");
            reportedWarnings.add(prefix);
        }
    }

    /** Don't issue warning again if the message is the same */
    static synchronized void warnOnce(String message) {
        warnOnce(message, message);
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

    protected String optSanitizeFieldName(String origFieldName) {
        String fieldName = AnnotatedFieldNameUtil.sanitizeXmlElementName(origFieldName, disallowDashInname());
        if (!origFieldName.equals(fieldName)) {
            warnOnce("Name '" + origFieldName + "' is not a valid XML element name; sanitized to '" + fieldName + "'");
        }
        return fieldName;
    }

    public interface NodeHandler<T> {
        void handle(T node);
    }

    public interface StringValueHandler {
        void handle(String value);
    }

    @Override
    public void close() {
        // NOP, we already closed our input after we read it
    }

    protected abstract String currentNodeXml(T node);

    protected abstract void xpathForEach(String xPath, T context, NodeHandler<T> handler);

    protected abstract void xpathForEachStringValue(String xPath, T context, StringValueHandler handler);

    protected abstract String xpathValue(String xpath, T context);

    protected abstract String xpathXml(String xpath, T context);

    /**
     * Process an annotation at the current position.
     *
     * @param annotation   annotation to process.
     * @param word         current node in the document
     * @param positionSpanStartOrRelSource     position to index at
     */
    protected void processAnnotation(ConfigAnnotation annotation, T word, Span positionSpanStartOrRelSource) {
        processAnnotation(annotation, word, positionSpanStartOrRelSource, null, this::indexAnnotationValues);
    }

    protected abstract void processAnnotation(ConfigAnnotation annotation, T word,
            Span positionSpanStartOrRelSource, Span spanEndOrRelTarget,
            AnnotationHandler handler);

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
    protected void processStandoffSpan(T standoffNode, AnnotationType type,
            Span sourceSpan, Span targetSpan, Collection<ConfigAnnotation> standoffAnnotations,
            String spanOrRelType) {
        String name = AnnotatedFieldNameUtil.relationAnnotationName(getIndexType());
        AnnotationWriter annotationWriter = getAnnotation(name);
        if (getIndexType() == BlackLabIndex.IndexType.EXTERNAL_FILES) {
            // Classic external index format. Span name and attributes indexed separately.

            assert type != AnnotationType.RELATION; // not supported in classic external index

            // First index the span name at this position, then any configured
            // annotations as attributes.
            // (we pass null for the annotation name to indicate that this is the tag name we're indexing,
            //  not an attribute)
            int indexAtPosition = sourceSpan.start();
            BytesRef payload = getPayload(sourceSpan, targetSpan, type, false, indexAtPosition);
            annotationValue(annotationWriter.name(), spanOrRelType, indexAtPosition, payload);
            for (ConfigAnnotation annotation: standoffAnnotations) {
                processAnnotation(annotation, standoffNode, sourceSpan, targetSpan, this::indexAnnotationValues);
            }
        } else {
            // Integrated index format.

            // Collect any attribute values
            Map<String, List<String>> attributes = new HashMap<>();
            for (ConfigAnnotation annotation: standoffAnnotations) {
                // NOTE: we pass invalid values for the positions because they don't matter here; we're not indexing,
                //       just finding XPath matches for the attributes and collecting them.
                processAnnotation(annotation, standoffNode, null, null,
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
    }

    protected abstract void processAnnotatedFieldContainer(T nav, ConfigAnnotatedField annotatedField,
            Map<String, Span> tokenPositionsMap);

    protected void processAnnotatedFieldContainerStandoff(T container, ConfigAnnotatedField annotatedField, Map<String, Span> tokenPositionsMap) {

        // (separate method because we only run these once all token positions for all fields have been collected,
        //  so parallel corpora can refer to token positions in other fields)

        // Process standoff annotations
        for (ConfigStandoffAnnotations standoff: annotatedField.getStandoffAnnotations()) {
            processStandoffAnnotation(standoff, container, tokenPositionsMap);
        }
    }

    protected void processStandoffAnnotation(ConfigStandoffAnnotations standoff, T container, Map<String, Span> tokenPositionsMap) {
        // For each instance of this standoff annotation..
        AnnotationType type = standoff.getType();
        xpathForEach(standoff.getPath(), container, (standoffNode) -> {
            // Determine what token positions to index these values at
            List<Span> indexAtPositions = new ArrayList<>();
            xpathForEachStringValue(standoff.getTokenRefPath(), standoffNode, (tokenPositionId) -> {
                if (!tokenPositionId.isEmpty()) {
                    Span span = tokenPositionsMap.get(tokenPositionId);
                    if (span == null) {
                        warnUnresolvedTokenId(tokenPositionId,
                                "Standoff annotation contains unresolved reference to token position");
                    } else
                        indexAtPositions.add(span);
                }
            });

            Collection<ConfigAnnotation> standoffAnnotations = standoff.getAnnotations().values();
            if (type == AnnotationType.TOKEN) {
                // "Regular" standoff annotation for a single token.
                // Index annotation values at the position(s) indicated
                for (ConfigAnnotation annotation: standoffAnnotations) {
                    for (Span position: indexAtPositions) {
                        processAnnotation(annotation, standoffNode, position);
                    }
                }
            } else {

                // Standoff span or relation annotation. Try to find end/target and type.

                // end/target
                Span endOrTarget = Span.invalid();
                Span[] endOrTargetArr = new Span[] { endOrTarget };
                xpathForEachStringValue(standoff.getSpanEndPath(), standoffNode, (tokenId) -> {
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
                    String spanOrRelType = xpathValue(standoff.getValuePath(), standoffNode);
                    if (type == AnnotationType.RELATION && !spanOrRelType.contains(RelationUtil.CLASS_TYPE_SEPARATOR)) {
                        // If no relation class specified, prepend the configured default relation class.
                        String targetVersion = standoff.getTargetVersionPath().isEmpty() ? "" :
                                xpathValue(standoff.getTargetVersionPath(), standoffNode);
                        String relationClass = standoff.resolveRelationClass(currentAnnotatedField.name(), targetVersion);
                        spanOrRelType = RelationUtil.fullType(relationClass, spanOrRelType);
                    }

                    if (indexAtPositions.isEmpty()) {
                        if (type != AnnotationType.RELATION) {
                            warn("Standoff annotation for inline tag has end but no start: "
                                    + standoffNode);
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

    private static void warnUnresolvedTokenId(String tokenId, String baseMessage) {
        // Warn about unresolved reference, but only once per token id prefix.
        // (so e.g. missing document isn't reported a million times)
        String tokenIdPrefix = tokenId.length() > TOKEN_ID_PREFIX_LENGTH ? tokenId.substring(0, TOKEN_ID_PREFIX_LENGTH) : tokenId;
        String tokenIdRest = tokenId.length() > TOKEN_ID_PREFIX_LENGTH ? tokenId.substring(TOKEN_ID_PREFIX_LENGTH) : "";
        String prefix = baseMessage + ": '" + tokenIdPrefix;
        String message = prefix + tokenIdRest + "'";
        warnOnce(message, prefix);
    }

    protected void processSubannotations(ConfigAnnotation parentAnnot, T context,
            Span positionSpanEndOrSource, Span spanEndOrRelTarget,
            AnnotationHandler handler, List<String> parentAnnotValues) {
        // For each configured subannotation...
        for (ConfigAnnotation subannot : parentAnnot.getSubAnnotations()) {
            // Subannotation configs without a valuePath are just for
            // adding information about subannotations captured in forEach's,
            // such as extra processing steps
            if (subannot.getValuePath() == null || subannot.getValuePath().isEmpty())
                continue;

            // Capture this subannotation value
            if (subannot.isForEach()) {
                // "forEach" subannotation specification
                // (allows us to capture multiple subannotations with 3 XPath expressions)
                xpathForEach(subannot.getForEachPath(), context, (match) -> {
                    // Find the name and value for this forEach match
                    String name = xpathValue(subannot.getName(), match);
                    String subannotationName = parentAnnot.getName() + AnnotatedFieldNameUtil.SUBANNOTATION_FIELD_PREFIX_SEPARATOR + name;
                    ConfigAnnotation declSubannot = parentAnnot.getSubAnnotation(subannotationName);

                    // It's not possible to create annotation on the fly at the moment.
                    // So since this was not declared in the config file, emit a warning and skip.
                    if (declSubannot == null) {
                        if (!skippedAnnotations.contains(subannotationName)) {
                            skippedAnnotations.add(subannotationName);
                            logger.error(documentName + ": skipping undeclared annotation " + name + " (" + "as declaredSubannot of forEachPath " + subannot.getName() + ")");
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

    protected void findAndIndexSubannotation(ConfigAnnotation toIndex, T context, ConfigAnnotation indexAs,
            Span positionSpanEndOrSource, Span spanEndOrRelTarget, AnnotationHandler handler,
            ConfigAnnotation parent, List<String> parentValues) {
        List<String> unprocessed = !toIndex.isForEach() && canReuseParentValues(indexAs, toIndex.getValuePath(), parent) ?
                parentValues :
                findAnnotationMatches(indexAs, toIndex.getValuePath(), context);
        List<String> processedValues = processAnnotationValues(indexAs, unprocessed);
        handler.values(indexAs, positionSpanEndOrSource, spanEndOrRelTarget, processedValues);
    }

    protected List<String> findAnnotationMatches(ConfigAnnotation annotation, String valuePath, T context) {
        // Not the same values as the parent annotation; we have to find our own.
        List<String> values = new ArrayList<>();
        // Multiple matches will be indexed at the same position.
        if (annotation.isCaptureXml()) {
            xpathForEach(valuePath, context, (value) -> values.add(currentNodeXml(value)));
        } else {
            xpathForEachStringValue(valuePath, context, values::add);
        }
        // No annotations have been added, the result of the xPath query must have been empty.
        if (values.isEmpty())
            values.add("");
        return values;
    }

    protected void processAnnotationWithinBasePath(ConfigAnnotation annotation, T word,
            Span positionSpanEndOrSource, Span spanEndOrRelTarget, AnnotationHandler handler) {
        String valuePath = determineValuePath(annotation, word);
        if (valuePath != null) {
            // Find annotation matches, process and dedupe and index them.
            List<String> unprocessedValues = findAnnotationMatches(annotation, valuePath, word);
            List<String> processedValues = processAnnotationValues(annotation, unprocessedValues);
            handler.values(annotation, positionSpanEndOrSource, spanEndOrRelTarget, processedValues);
            processSubannotations(annotation, word, positionSpanEndOrSource, spanEndOrRelTarget, handler, unprocessedValues);
        } else {
            // No valuePath given. Assume this will be captured using forEach.
        }
    }

    /**
     * Determine the valuePath for an annotation at the current word.
     * <p>
     * The reason this can vary per word is the captureValuePath feature.
     * This will capture a value from the current word and substitute it
     * into the valuePath, allowing us to look up information elsewhere in the
     * document.
     * <p>
     * This is probably no longer needed when using Saxon, which has better
     * support for advanced XPath features.
     *
     * @param annotation annotation we're processing
     * @return the valuePath with any substitutions made
     */
    protected String determineValuePath(ConfigAnnotation annotation, T context) {
        String valuePath = annotation.getValuePath();
        // TODO: warn about eventual deprecation
        // Substitute any captured values into the valuePath?
        int i = 1;
        for (String captureValuePath : annotation.getCaptureValuePaths()) {
            String value = xpathValue(captureValuePath, context);
            valuePath = valuePath.replace("$" + i, value);
            i++;
        }
        return valuePath;
    }

    /**
     * Index document from the current node.
     */
    protected void indexDocument(T doc) {
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
        for (ConfigMetadataBlock b : config.getMetadataBlocks()) {
            processMetadataBlock(doc, b);
        }

        // For each linked document...
        for (ConfigLinkedDocument ld : config.getLinkedDocuments().values()) {
            processLinkedDocument(ld, xpath -> xpathValue(xpath, doc));
        }

        endDocument();
    }

    @Override
    public void indexSpecificDocument(String documentXPath) {
        super.indexSpecificDocument(documentXPath);

        final AtomicBoolean docDone = new AtomicBoolean(false);
        try {
            if (documentXPath != null) {
                indexParsedFile(documentXPath, true);
                // Find our specific document in the file
                xpathForEach(documentXPath, contextNodeWholeDocument(), (doc) -> {
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
    }

    protected void processMetadataBlock(T doc, ConfigMetadataBlock metaBlock) {
        // For each instance of this metadata block...
        xpathForEach(metaBlock.getContainerPath(), doc, (block) -> {
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
                    processMetadataValue(block, field);
                }
            }
        });
    }

    protected void processMetaForEach(ConfigMetadataBlock metaBlock, T block, ConfigMetadataField forEach) {
        xpathForEach(forEach.getForEachPath(), block, (match) -> {
            // Find the fieldName and value for this forEach match
            String origFieldName = xpathValue(forEach.getName(), match);
            String fieldName = optSanitizeFieldName(origFieldName);
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

    protected void processMetadataValue(T header, ConfigMetadataField field) {
        indexMetadataFieldMatches(header, field, field.getName(), null);
    }

    protected void indexMetadataFieldMatches(T forEach, ConfigMetadataField forEachField, String indexAsFieldName,
            ConfigMetadataField indexAsFieldConfig) {
        xpathForEachStringValue(forEachField.getValuePath(), forEach, (unprocessedValue) -> {
            unprocessedValue = StringUtil.sanitizeAndNormalizeUnicode(unprocessedValue);
            for (String value: processStringMultipleValues(unprocessedValue, forEachField.getProcess())) {
                if (indexAsFieldConfig == null) {
                    addMetadataField(indexAsFieldName, value);
                } else {
                    // Also execute process defined for named metadata field, if any
                    for (String processedValue: processStringMultipleValues(value,
                            indexAsFieldConfig.getProcess())) {
                        addMetadataField(indexAsFieldName, processedValue);
                    }
                }
            }
        });
    }

    protected void processAnnotatedField(T document, ConfigAnnotatedField annotatedField, Map<String, Span> tokenPositionsMap) {
        // Determine some useful stuff about the field we're processing
        // and store in instance variables so our methods can access them
        setCurrentAnnotatedFieldName(annotatedField.getName());

        // For each container (e.g. "text" or "body" element) ...
        xpathForEach(annotatedField.getContainerPath(), document,
                (container) -> processAnnotatedFieldContainer(container, annotatedField, tokenPositionsMap));
    }

    protected void processAnnotatedFieldStandoff(T document, ConfigAnnotatedField annotatedField, Map<String, Span> tokenPositionsMap) {

        // (separate method because we only run these once all token positions for all fields have been collected,
        //  so parallel corpora can refer to token positions in other fields)

        // Determine some useful stuff about the field we're processing
        // and store in instance variables so our methods can access them
        setCurrentAnnotatedFieldName(annotatedField.getName());

        // For each container (e.g. "text" or "body" element) ...
        xpathForEach(annotatedField.getContainerPath(), document,
                (container) -> processAnnotatedFieldContainerStandoff(container, annotatedField, tokenPositionsMap));
    }

    protected boolean indexParsedFile(String docXPath, boolean mustBeSingleDocument) {
        try {
            AtomicBoolean docDone = new AtomicBoolean(false); // any doc(s) processed?
            xpathForEach(docXPath, contextNodeWholeDocument(),(doc) -> {
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

    protected abstract T contextNodeWholeDocument();

    protected interface AnnotationHandler {
        void values(ConfigAnnotation annotation, Span positionSpanEndOrSource, Span spanEndOrRelTarget, List<String> values);
    }
}
