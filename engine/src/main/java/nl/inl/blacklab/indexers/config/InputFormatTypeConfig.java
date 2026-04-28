package nl.inl.blacklab.indexers.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Stream;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.exceptions.ErrorIndexingFile;
import nl.inl.blacklab.exceptions.InvalidInputFormatConfig;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.index.DocWriter;
import nl.inl.blacklab.index.IndexerStats;
import nl.inl.blacklab.index.InputFormat;
import nl.inl.blacklab.index.annotated.AnnotatedFieldWriter;
import nl.inl.blacklab.index.annotated.AnnotationSensitivities;
import nl.inl.blacklab.index.annotated.AnnotationWriter;
import nl.inl.blacklab.indexers.config.process.ProcessingInstructionIdentity;
import nl.inl.blacklab.indexers.config.process.ProcessingStep;
import nl.inl.blacklab.plugins.FileConverter;
import nl.inl.blacklab.plugins.InputFormatType;
import nl.inl.blacklab.plugins.PluginManager;
import nl.inl.blacklab.plugins.param.PluginParams;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.util.StringUtil;
import nl.inl.util.fileprocessor.FileReference;

/**
 * Input formats configured using a ConfigInputFormat structure.
 */
public abstract class InputFormatTypeConfig extends InputFormatTypeBase {

    protected static String replaceDollarRefs(String pattern, List<String> replacements) {
        if (pattern != null) {
            int i = 1;
            for (String replacement: replacements) {
                pattern = pattern.replace("$" + i, replacement);
                i++;
            }
        }
        return pattern;
    }

    @Override
    public InputFormat createInputFormat(ConfigInputFormat config, PluginParams params) {
        if (config == null)
            throw new IllegalArgumentException("No config provided for input format");
        return createInputFormat(config);
    }

    public abstract InputFormat createInputFormat(ConfigInputFormat config);

    public static InputFormat fromConfig(ConfigInputFormat config) {
        InputFormatType inputFormatType = getInputFormatType(config);
        InputFormat inputFormat = inputFormatType.createInputFormat(config, PluginParams.NONE);
        if (config.hasFileConverters()) {
            try {
                InputFormatTypeWithConverters docIndexerConvertAndTag = new InputFormatTypeWithConverters();
                List<FileConverter.Parameterized> converters = config.getConverters().stream()
                        .map(FileConverter::fromConfig).toList();
                return docIndexerConvertAndTag.createInputFormat(inputFormat, converters);
            } catch (Exception e) {
                throw BlackLabException.wrapRuntime(e);
            }
        } else {
            return inputFormat;
        }
    }

    private static InputFormatType getInputFormatType(ConfigInputFormat config) {
        InputFormatType inputFormatType;
        Map<String, String> fileTypeOptions = config.getFileTypeOptions();
        // Was an explicit input format type class specified?
        String inputFormatTypeClass = fileTypeOptions.get("inputFormatTypeClass");
        if (inputFormatTypeClass == null) {
            inputFormatTypeClass = fileTypeOptions.get("docIndexerClass"); // old name
            if (inputFormatTypeClass != null)
                logger.warn("fileTypeOptions.docIndexerClass in .blf.yaml is deprecated, renamed to inputFormatTypeClass.");
        }
        try {
            if (inputFormatTypeClass == null) {
                // Determine input format type based on file type
                Class<? extends InputFormatTypeConfig> clz = switch (config.getFileType()) {
                    case XML -> InputFormatTypeXml.class;
                    case TABULAR -> InputFormatTypeTabular.class;
                    case TEXT -> InputFormatTypePlainText.class;
                    case CHAT -> InputFormatTypeChat.class;
                    case CONLL_U -> InputFormatTypeCoNLLU.class;
                };
                inputFormatTypeClass = clz.getName();
            }
            inputFormatType = PluginManager.type(InputFormatType.class).get(inputFormatTypeClass);
        } catch (PluginException e) {
            throw new InvalidInputFormatConfig(e);
        }
        return inputFormatType;
    }

    protected static abstract class InputFormatConfig extends InputFormatBase {
        /**
         * Our input format
         */
        protected ConfigInputFormat config;

        public InputFormatConfig(ConfigInputFormat config) {
            this.config = config;
            setStoreDocuments(config.shouldStore());
        }

        protected abstract class DocConfig extends DocBase {

            public DocConfig(DocWriter docWriter, FileReference file) {
                super(docWriter, file);
            }

            boolean inited = false;

            protected void ensureInitialized() {
                if (inited)
                    return;
                inited = true;
                for (ConfigAnnotatedField af: config.getAnnotatedFields().values()) {

                    // Define the properties that make up our annotated field
                    if (af.isDummyForStoringLinkedDocuments())
                        continue;
                    List<ConfigAnnotation> annotations = af.getAnnotationsFlattened();
                    if (annotations.isEmpty())
                        throw new InvalidInputFormatConfig("No annotations defined for field " + af.getName());
                    ConfigAnnotation mainAnnotation = annotations.stream()
                            .filter(a -> !a.isForEach())
                            .findFirst()
                            .orElseThrow(() -> new InvalidInputFormatConfig(
                                    "No main annotation defined for field " + af.getName()));
                    boolean needsPrimaryValuePayloads = getDocWriter().needsPrimaryValuePayloads();
                    AnnotatedFieldWriter fieldWriter = new AnnotatedFieldWriter(getDocWriter(), af.getName(),
                            mainAnnotation.getName(), mainAnnotation.getSensitivitySetting(), false,
                            needsPrimaryValuePayloads);

                    String relAnnotName = AnnotatedFieldNameUtil.RELATIONS_ANNOT_NAME;
                    AnnotationSensitivities relAnnotSensitivity = AnnotationSensitivities.defaultForAnnotation(
                            relAnnotName);
                    AnnotationWriter annotRelation = fieldWriter.addAnnotation(relAnnotName, relAnnotSensitivity, true,
                            false);
                    annotRelation.setHasForwardIndex(false);

                    // Create properties for the other annotations
                    for (int i = 1; i < annotations.size(); i++) {
                        ConfigAnnotation annot = annotations.get(i);
                        if (!annot.isForEach())
                            fieldWriter.addAnnotation(annot.getName(), annot.getSensitivitySetting(), false,
                                    annot.isForwardIndex());
                    }
                    for (ConfigStandoffAnnotations standoff: af.getStandoffAnnotations()) {
                        for (ConfigAnnotation annot: standoff.getAnnotations()) {
                            if (!annot.isForEach())
                                fieldWriter.addAnnotation(annot.getName(), annot.getSensitivitySetting(), false,
                                    annot.isForwardIndex());
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
            public IndexerStats index() throws ErrorIndexingFile {
                ensureInitialized();
                return getStats();
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
                    throw new ErrorIndexingFile("Link path " + path + " not found in document " + documentName);
                }
            }

            protected List<String> processValues(ProcessingStep processing, Collection<String> values) {
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
                    results.add(processing.performSingle(rawValue, metadataFieldValues));
                }
                return results;
            }

            protected void indexAnnotationValues(ConfigAnnotation annotation, Span positionSpanEndOrSource,
                    Span spanEndOrRelTarget,
                    Collection<String> valuesToIndex) {
                indexAnnotationValuesNoRelation(annotation, positionSpanEndOrSource.start(), valuesToIndex);
            }

            private void indexAnnotationValuesNoRelation(ConfigAnnotation annotation, int indexAtPosition,
                    Collection<String> valuesToIndex) {
                for (String value: valuesToIndex) {
                    annotationValue(annotation.getName(), value, indexAtPosition, null);
                }
            }

            @Override
            public IndexerStats indexSpecificDocument(String documentExpr, Doc linkingDoc, String storeWithName) {
                ensureInitialized();
                return super.indexSpecificDocument(documentExpr, linkingDoc, storeWithName);
            }

            /**
             * process linked documents when configured. An xPath processor can be provided,
             * it will retrieve information from the document to construct a path to a linked document.
             */
            protected void processLinkedDocument(ConfigLinkedDocument ld, Function<String, String> xpathProcessor) {
                // Resolve linkPaths to get the information needed to fetch the document
                List<String> results = new ArrayList<>();
                for (ConfigLinkValue linkValue: ld.getLinkValues()) {
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
                        Collection<String> metadataField = getMetadataField(valueField);
                        if (metadataField == null) {
                            throw new ErrorIndexingFile("Link value field " + valueField + " has no values (null)!");
                        }
                        results.addAll(metadataField);
                    }
                    List<String> resultAfterProcessing = new ArrayList<>();
                    for (String inputValue: results) {
                        inputValue = StringUtil.sanitizeAndNormalizeUnicode(inputValue);
                        resultAfterProcessing.addAll(processStringMultipleValues(inputValue, linkValue.getCompiledProcessSteps()));
                    }
                    results = resultAfterProcessing;
                }

                // Substitute link path results in inputFile, pathInsideArchive and documentPath
                String inputFile = replaceDollarRefs(ld.getInputFile(), results);
                String pathInsideArchive = replaceDollarRefs(ld.getPathInsideArchive(), results);
                String documentPath = replaceDollarRefs(ld.getDocumentPath(), results);

                try {
                    // Fetch and index the linked document
                    indexLinkedDocument(inputFile, pathInsideArchive, documentPath, ld.getInputFormat(),
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
                        getDocWriter().listener()
                                .warning("Could not find or parse linked document for " + documentName + moreInfo
                                        + ": " + e.getMessage());
                        break;
                    case FAIL:
                        throw new ErrorIndexingFile(
                                "Could not find or parse linked document for " + documentName + moreInfo, e);
                    }
                }
            }

            protected List<String> processStringMultipleValues(String input, ProcessingStep process) {
                // If there's no processing to be done (the most common case), skip the list allocation.
                return process instanceof ProcessingInstructionIdentity.ProcessingStepIdentity ?
                        List.of(input) :
                        process.perform(Stream.of(input), metadataFieldValues).toList();
            }

            /**
             * If any processing steps were defined for this metadata field, apply them now.
             * This is used for non-XML formats, where we don't actively seek out the
             * metadata but encounter it as we go.
             *
             * @param name  metadata field name
             * @param value metadata field value
             * @return processed value (or original value if not found / no processing steps
             * defined)
             */
            protected String processMetadataValue(String name, String value) {
                ConfigMetadataField f = config.getMetadataField(name);
                if (f != null)
                    value = f.getCompiledProcessSteps().performSingle(value, metadataFieldValues);
                return value;
            }

            /**
             * Add metadata field value.
             * We first collect all metadata values before processing to ensure we have all of them
             * in the case of fields with multiple values and to be able to sort them so sorting/grouping
             * works correctly on these fields as well.
             *
             * @param name  field name
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
                final String indexAsName = optTranslateMetadataFieldName(name);
                value = StringUtil.trimWhitespace(value);
                if (!value.isEmpty()) {
                    metadataFieldValues.computeIfAbsent(indexAsName, __ -> {
                        ConfigMetadataField conf = config.getMetadataField(indexAsName);
                        if (conf != null && conf.getSortValues()) {
                            return new TreeSet<>(BlackLab.defaultCollator()::compare);
                        } else {
                            return new ArrayList<>();
                        }
                    }).add(value);
                    getDocWriter().metadata().registerMetadataField(indexAsName);
                }
            }

            @Override
            protected String optTranslateMetadataFieldName(String from) {
                if (config == null) // test
                    return from;
                String to = config.getIndexFieldAs().get(from);
                return to == null ? from : to;
            }

            /**
             * Get a metadata field value.
             * Overridden because we might need to get the value from document that linked to us
             * (via the deprecated linkedDocuments system).
             *
             * @param name field name
             * @return value(s), or null if not defined
             */
            @Override
            public Collection<String> getMetadataField(String name) {
                Collection<String> v = super.getMetadataField(name);
                if (v != null)
                    return v;
                if (linkingDoc != null) {
                    // Get the value from the indexer that linked to us
                    // (because it may already contain metadata values that have not been added to the Lucene doc yet)
                    v = linkingDoc.getMetadataField(name);
                }
                return v;
            }

            @Override
            protected void endDocument() {
                super.endDocument();
                linkingDoc = null; // help GC
            }
        }
    }

    @Override
    public boolean isWebSafe() {
        return true;
    }
}
