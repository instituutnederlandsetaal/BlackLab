package nl.inl.blacklab.indexers.config;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.lucene.util.BytesRef;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.exceptions.InvalidInputFormatConfig;
import nl.inl.blacklab.exceptions.MalformedInputFile;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.index.annotated.AnnotatedFieldWriter;
import nl.inl.blacklab.index.annotated.AnnotationSensitivities;
import nl.inl.blacklab.index.annotated.AnnotationWriter;
import nl.inl.blacklab.indexers.config.process.ProcessingStep;
import nl.inl.blacklab.indexers.config.process.ProcessingStepIdentity;
import nl.inl.blacklab.indexers.preprocess.DocIndexerConvertAndTag;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.RelationsStrategyNaiveSeparateTerms;
import nl.inl.util.StringUtil;

/**
 * A DocIndexer configured using a ConfigInputFormat structure.
 */
public abstract class DocIndexerConfig extends DocIndexerBase {

    protected static String replaceDollarRefs(String pattern, List<String> replacements) {
        if (pattern != null) {
            int i = 1;
            for (String replacement : replacements) {
                pattern = pattern.replace("$" + i, replacement);
                i++;
            }
        }
        return pattern;
    }

    public static DocIndexerConfig fromConfig(ConfigInputFormat config) {
        DocIndexerConfig docIndexer;
        Map<String, String> options = config.getFileTypeOptions();
        String docIndexerClass = options.get("docIndexerClass");
        if (docIndexerClass != null) {
            // A custom DocIndexer class was specified in the fileTypeOptions;
            // instantiate that using reflection.
            try {
                Class<? extends DocIndexerConfig> clz = (Class<? extends DocIndexerConfig>)Class.forName(docIndexerClass);
                docIndexer = getWithCustomDocIndexerClass(clz, options);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Custom docIndexerClass not found: " + docIndexerClass, e);
            }
        } else {
            docIndexer = switch (config.getFileType()) {
                case XML -> DocIndexerXPath.create(config.getFileTypeOptions());
                case TABULAR -> new DocIndexerTabular();
                case TEXT -> new DocIndexerPlainText();
                case CHAT -> new DocIndexerChat();
                case CONLL_U -> new DocIndexerCoNLLU();
                default -> throw new InvalidInputFormatConfig(
                        "Unknown file type: " + config.getFileType() + " (use xml, tabular, text or chat)");
            };
        }

        docIndexer.setConfigInputFormat(config);

        if (config.getConvertPluginId() != null || config.getTagPluginId() != null) {
            try {
                return new DocIndexerConvertAndTag(docIndexer, config);
            } catch (Exception e) {
                throw BlackLabException.wrapRuntime(e);
            }
        } else {
            return docIndexer;
        }
    }

    public static DocIndexerConfig getWithCustomDocIndexerClass(Class<? extends DocIndexerConfig> clz, Map<String, String> fileTypeOptions) {
        try {
            try {
                // Try the constructor that takes fileTypeOptions
                Constructor<? extends DocIndexerConfig> constructor = clz.getConstructor(Map.class);
                return constructor.newInstance(fileTypeOptions);
            } catch (NoSuchMethodException e) {
                // Try the no-arg constructor instead
                return clz.getConstructor().newInstance();
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    /** Our input format */
    protected ConfigInputFormat config;

    boolean inited = false;

    protected final Map<String, Collection<String>> sortedMetadataValues = new HashMap<>();

    public void setConfigInputFormat(ConfigInputFormat config) {
        this.config = config;
    }

    @Override
    protected String optTranslateFieldName(String from) {
        if (config == null) // test
            return from;
        String to = config.getIndexFieldAs().get(from);
        return to == null ? from : to;
    }

    protected void ensureInitialized() {
        if (inited)
            return;
        inited = true;
        setStoreDocuments(config.shouldStore());
        for (ConfigAnnotatedField af: config.getAnnotatedFields().values()) {

            // Define the properties that make up our annotated field
            if (af.isDummyForStoringLinkedDocuments())
                continue;
            List<ConfigAnnotation> annotations = new ArrayList<>(af.getAnnotationsFlattened().values());
            if (annotations.isEmpty())
                throw new InvalidInputFormatConfig("No annotations defined for field " + af.getName());
            ConfigAnnotation mainAnnotation = annotations.get(0);
            boolean needsPrimaryValuePayloads = getDocWriter().needsPrimaryValuePayloads();
            AnnotatedFieldWriter fieldWriter = new AnnotatedFieldWriter(getDocWriter(), af.getName(),
                    mainAnnotation.getName(), mainAnnotation.getSensitivitySetting(), false,
                    needsPrimaryValuePayloads);

            String relAnnotName = AnnotatedFieldNameUtil.relationAnnotationName(getIndexType());
            AnnotationSensitivities relAnnotSensitivity = AnnotationSensitivities.defaultForAnnotation(relAnnotName, config.getVersion());
            AnnotationWriter annotRelation = fieldWriter.addAnnotation(relAnnotName, relAnnotSensitivity, true, false);
            annotRelation.setHasForwardIndex(false);

            // Create properties for the other annotations
            for (int i = 1; i < annotations.size(); i++) {
                ConfigAnnotation annot = annotations.get(i);
                if (!annot.isForEach())
                    fieldWriter.addAnnotation(annot.getName(), annot.getSensitivitySetting(), false,
                            annot.createForwardIndex());
            }
            for (ConfigStandoffAnnotations standoff: af.getStandoffAnnotations()) {
                for (ConfigAnnotation annot: standoff.getAnnotations().values()) {
                    fieldWriter.addAnnotation(annot.getName(), annot.getSensitivitySetting(), false,
                            annot.createForwardIndex());
                }
            }
            if (!fieldWriter.hasAnnotation(AnnotatedFieldNameUtil.PUNCTUATION_ANNOT_NAME)) {
                // Hasn't been created yet. Create it now.
                fieldWriter.addAnnotation(AnnotatedFieldNameUtil.PUNCTUATION_ANNOT_NAME,
                        AnnotationSensitivities.ONLY_INSENSITIVE, false, true);
            }
            addAnnotatedField(fieldWriter);
        }
    }

    @Override
    public void index() throws IOException, MalformedInputFile, PluginException {
        ensureInitialized();
    }

    protected void linkPathMissing(ConfigLinkedDocument ld, String path) {
        switch (ld.getIfLinkPathMissing()) {
            case IGNORE:
                break;
            case WARN:
                getDocWriter().listener()
                        .warning("Link path " + path + " not found in document " + documentName);
                break;
            case FAIL:
                throw new RuntimeException("Link path " + path + " not found in document " + documentName);
        }
    }

    protected List<String> processAnnotationValues(ConfigAnnotation annotation, Collection<String> values) {
        ProcessingStep processing = annotation.getProcess();
        boolean hasProcessing = processing != null;
        boolean processingMultiple = hasProcessing && processing.canProduceMultipleValues();

        // Do we have anything to do?
        if (!hasProcessing || values.isEmpty()) {
            // No processing or deduplication to do; just return the values as-is (but sanitized/normalized)
            return values.stream().map(StringUtil::sanitizeAndNormalizeUnicode).toList();
        }

        // Apply processing steps
        List<String> results = new ArrayList<>();
        if (processingMultiple || values.size() > 1) {
            // Could there be multiple values here? (either there already are, or a processing step might create them)
            // (this is to prevent allocating a set if we don't have to)

            // If duplicates are not allowed, keep track of values we've already added
            for (String rawValue: values) {
                rawValue = StringUtil.sanitizeAndNormalizeUnicode(rawValue);
                results.addAll(processStringMultipleValues(rawValue, processing));
            }
        } else {
            // Single value (the collection should only contain one entry)
            // (if multiple were matched, we only index the first one)
            String rawValue = values.iterator().next();
            rawValue = StringUtil.sanitizeAndNormalizeUnicode(rawValue);
            results = new ArrayList<>();
            results.add(processing.performSingle(rawValue, this));
        }
        return results;
    }

    protected void indexAnnotationValues(ConfigAnnotation annotation, Span positionSpanEndOrSource, Span spanEndOrRelTarget,
            Collection<String> valuesToIndex) {
        if (Span.isValid(spanEndOrRelTarget)) {
            // Relation (span) attributes in classic external index
            assert getIndexType() == BlackLabIndex.IndexType.EXTERNAL_FILES;
            indexAnnotationValuesSpanExternal(annotation, positionSpanEndOrSource, spanEndOrRelTarget, valuesToIndex);
        } else {
            indexAnnotationValuesNoRelation(annotation, positionSpanEndOrSource.start(), valuesToIndex);
        }
    }

    private void indexAnnotationValuesSpanExternal(ConfigAnnotation annotation, Span positionSpanEndOrSource, Span spanEndOrRelTarget,
            Collection<String> valuesToIndex) {
        // For attributes to span annotations in classic external index (which are all added to the same annotation),
        // the span name has already been indexed at this position with an increment of 1,
        // so the attribute values we're indexing here should all get position increment 0.
        String name = AnnotatedFieldNameUtil.relationAnnotationName(BlackLabIndex.IndexType.EXTERNAL_FILES);
        // Now add values to the index
        for (String value: valuesToIndex) {
            // External index, attribute values are indexed separately from the tag name
            // For the external index format (annotation "starrtag"), we index several terms:
            // // one for the span name, and one for each attribute name and value.
            value = RelationsStrategyNaiveSeparateTerms.tagAttributeIndexValue(annotation.getName(), value);

            int indexAtPosition = positionSpanEndOrSource.start();
            BytesRef payload = getPayload(positionSpanEndOrSource, spanEndOrRelTarget, AnnotationType.SPAN,
                    true, indexAtPosition);
            annotationValue(name, value, indexAtPosition, payload);
        }
    }

    private void indexAnnotationValuesNoRelation(ConfigAnnotation annotation, int indexAtPosition,
            Collection<String> valuesToIndex) {
        for (String value: valuesToIndex) {
            annotationValue(annotation.getName(), value, indexAtPosition, null);
        }
    }

    @Override
    public void indexSpecificDocument(String documentExpr) {
        ensureInitialized();
    }

    /**
     * process linked documents when configured. An xPath processor can be provided,
     * it will retrieve information from the document to construct a path to a linked document.
     */
    protected void processLinkedDocument(ConfigLinkedDocument ld, Function<String, String> xpathProcessor) {
        // Resolve linkPaths to get the information needed to fetch the document
        List<String> results = new ArrayList<>();
        for (ConfigLinkValue linkValue : ld.getLinkValues()) {
            String valuePath = linkValue.getValuePath();
            String valueField = linkValue.getValueField();
            if (valuePath != null) {
                // Resolve value using XPath
                String result = xpathProcessor.apply(valuePath);
                if (result == null || result.isEmpty()) {
                    linkPathMissing(ld, valuePath);
                }
                results.add(result);
            } else if (valueField != null) {
                // Fetch value from Lucene doc
                List<String> metadataField = getMetadataField(valueField);
                if (metadataField == null) {
                    throw new RuntimeException("Link value field " + valueField + " has no values (null)!");
                }
                results.addAll(metadataField);
            }
            List<String> resultAfterProcessing = new ArrayList<>();
            for (String inputValue : results) {
                inputValue = StringUtil.sanitizeAndNormalizeUnicode(inputValue);
                resultAfterProcessing.addAll(processStringMultipleValues(inputValue, linkValue.getProcess()));
            }
            results = resultAfterProcessing;
        }

        // Substitute link path results in inputFile, pathInsideArchive and documentPath
        String inputFile = replaceDollarRefs(ld.getInputFile(), results);
        String pathInsideArchive = replaceDollarRefs(ld.getPathInsideArchive(), results);
        String documentPath = replaceDollarRefs(ld.getDocumentPath(), results);

        try {
            // Fetch and index the linked document
            indexLinkedDocument(inputFile, pathInsideArchive, documentPath, ld.getInputFormatIdentifier(),
                    ld.shouldStore() ? ld.getName() : null);
        } catch (Exception e) {
            String moreInfo = "(inputFile = " + inputFile;
            if (pathInsideArchive != null)
                moreInfo += ", pathInsideArchive = " + pathInsideArchive;
            if (documentPath != null)
                moreInfo += ", documentPath = " + documentPath;
            moreInfo += ")";
            switch (ld.getIfLinkPathMissing()) {
                case IGNORE:
                case WARN:
                    getDocWriter().listener().warning("Could not find or parse linked document for " + documentName + moreInfo
                            + ": " + e.getMessage());
                    break;
                case FAIL:
                    throw new RuntimeException("Could not find or parse linked document for " + documentName + moreInfo, e);
            }
        }
    }

    protected List<String> processStringMultipleValues(String input, ProcessingStep process) {
        // If there's no processing to be done (the most common case), skip the list allocation.
        return process instanceof ProcessingStepIdentity ?
                List.of(input) :
                process.perform(Stream.of(input), this).toList();
    }

    /**
     * If any processing steps were defined for this metadata field, apply them now.
     *
     * This is used for non-XML formats, where we don't actively seek out the
     * metadata but encounter it as we go.
     *
     * @param name metadata field name
     * @param value metadata field value
     * @return processed value (or original value if not found / no processing steps
     *         defined)
     */
    protected String processMetadataValue(String name, String value) {
        ConfigMetadataField f = config.getMetadataField(name);
        if (f != null)
            value = f.getProcess().performSingle(value, this);
        return value;
    }

    /**
     * Add metadata field value.
     *
     * We first collect all metadata values before processing to ensure we have all of them
     * in the case of fields with multiple values and to be able to sort them so sorting/grouping
     * works correctly on these fields as well.
     *
     * @param name field name
     * @param value value to add
     */
    @Override
    public void addMetadataField(String name, String value) {
        assert name != null;
        assert value != null;
        if (name.isEmpty()) {
            warn("Tried to add metadata value but field name is empty, ignoring (value: " + value + ")");
            return;
        }
        final String indexAsName = optTranslateFieldName(name);
        this.sortedMetadataValues.computeIfAbsent(indexAsName, __ -> {
            ConfigMetadataField conf = this.config.getMetadataField(indexAsName);
            if (conf != null && conf.getSortValues()) {
                return new TreeSet<>(BlackLab.defaultCollator()::compare);
            } else {
                return new ArrayList<>();
            }
        }).add(value);
    }

    private static List<String> collectionToList(Collection<String> c) {
        return c == null ? null : c instanceof List ? (List<String>)c : new ArrayList<>(c);
    }

    /**
     * Get a metadata field value.
     *
     * Overridden because we collect them in sortedMetadataValues while parsing the document,
     * and if a value is needed while parsing (such as with linked metadata), we wouldn't
     * otherwise be able to return it.
     *
     * Note that these values aren't processed yet, so that's still an issue.
     *
     * @param name field name
     * @return value(s), or null if not defined
     */
    @Override
    public List<String> getMetadataField(String name) {
        List<String> v = super.getMetadataField(name);
        if (v != null)
            return v;
        v = collectionToList(sortedMetadataValues.get(name));
        if (v != null)
            return v;
        if (linkingIndexer != null) {
            // Get the value from the indexer that linked to us
            // (because it may already contain metadata values that have not been added to the Lucene doc yet)
            v = linkingIndexer.getMetadataField(name);
        }
        return v;
    }

    @Override
    protected void endDocument() {
        for (Map.Entry<String, Collection<String>> metadataValues : sortedMetadataValues.entrySet()) {
            String fieldName = metadataValues.getKey();
            for (String s : metadataValues.getValue()) {
                super.addMetadataField(fieldName, s);
            }
        }
        sortedMetadataValues.clear();
        super.endDocument();
        linkingIndexer = null; // help GC
    }
}
