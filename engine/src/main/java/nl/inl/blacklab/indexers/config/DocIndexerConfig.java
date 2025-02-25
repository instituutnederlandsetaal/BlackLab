package nl.inl.blacklab.indexers.config;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.util.BytesRef;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.InvalidInputFormatConfig;
import nl.inl.blacklab.exceptions.MalformedInputFile;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.index.annotated.AnnotatedFieldWriter;
import nl.inl.blacklab.index.annotated.AnnotationSensitivities;
import nl.inl.blacklab.index.annotated.AnnotationWriter;
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
            switch (config.getFileType()) {
            case XML:
                docIndexer = DocIndexerXPath.create(config.getFileTypeOptions());
                break;
            case TABULAR:
                docIndexer = new DocIndexerTabular();
                break;
            case TEXT:
                docIndexer = new DocIndexerPlainText();
                break;
            case CHAT:
                docIndexer = new DocIndexerChat();
                break;
            case CONLL_U:
                docIndexer = new DocIndexerCoNLLU();
                break;
            default:
                throw new InvalidInputFormatConfig(
                        "Unknown file type: " + config.getFileType() + " (use xml, tabular, text or chat)");
            }
        }

        docIndexer.setConfigInputFormat(config);

        if (config.getConvertPluginId() != null || config.getTagPluginId() != null) {
            try {
                return new DocIndexerConvertAndTag(docIndexer, config);
            } catch (Exception e) {
                throw BlackLabRuntimeException.wrap(e);
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
            AnnotationSensitivities relAnnotSensitivity = AnnotationSensitivities.defaultForAnnotation(relAnnotName);
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
                throw new BlackLabRuntimeException("Link path " + path + " not found in document " + documentName);
        }
    }

    protected Collection<String> processAnnotationValues(ConfigAnnotation annotation, Collection<String> values) {
        List<ConfigProcessStep> processingSteps = annotation.getProcess();
        boolean hasProcessing = !processingSteps.isEmpty();

        // Do we have anything to do?
        boolean nothingToProcess = !hasProcessing || values.isEmpty();
        boolean noDupesToEliminate = values.size() <= 1 || annotation.isAllowDuplicateValues();
        if (nothingToProcess && noDupesToEliminate) {
            // No processing or deduplication to do; just return the values as-is (but sanitized/normalized)
            return values.stream().map(StringUtil::sanitizeAndNormalizeUnicode).collect(Collectors.toList());
        }

        Collection<String> results = new ArrayList<>();

        // Apply processing steps
        if (annotation.isMultipleValues()) {
            // Could there be multiple values here? (either there already are, or a processing step might create them)
            // (this is to prevent allocating a set if we don't have to)

            // If duplicates are not allowed, keep track of values we've already added
            Set<String> valuesSeen = annotation.isAllowDuplicateValues() ? null : new HashSet<>();
            for (String rawValue: values) {
                rawValue = StringUtil.sanitizeAndNormalizeUnicode(rawValue);
                for (String processedValue: processStringMultipleValues(rawValue, processingSteps, null)) {
                    if (annotation.isAllowDuplicateValues() || !valuesSeen.contains(processedValue)) {
                        // Not a duplicate, or we don't care about duplicates. Add it.
                        results.add(processedValue);
                        if (valuesSeen != null)
                            valuesSeen.add(processedValue);
                    }
                }
            }
        } else {
            // Single value (the collection should only contain one entry)
            for (String rawValue: values) {
                rawValue = StringUtil.sanitizeAndNormalizeUnicode(rawValue);
                results.add(processString(rawValue, processingSteps, null));
                break; // if multiple were matched, only index the first one
            }
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

    protected String processString(String result, List<ConfigProcessStep> process, Map<String, String> mapValues) {
        for (ConfigProcessStep step : process) {
            String method = step.getMethod();
            Map<String, String> param = step.getParam();
            switch (method) {
            case "replace":
                if (param.getOrDefault("keep", "replaced").equals("all"))
                    warn("'replace' processing step with 'keep: all', but multiple values not allowed");
                result = opReplace(result, param);
                break;
            case "default":
                result = opDefault(result, param);
                break;
            case "append":
                result = opAppend(result, param);
                break;
            case "split":
                result = opSplit(result, param).get(0);
                break;
            case "chatFormatAgeToMonths":
                result = opChatFormatAgeToMonths(result);
                break;
            case "strip":
                result = opStrip(result, param);
                break;
             case "concatDate":
                result = opConcatDate(param);
                break;
            case "parsePos":
            {
                // Get individual feature out of a part of speech string like "NOU(gender=f,number=p)"
                String field = param.getOrDefault("field", "_");
                result = opParsePartOfSpeech(result, field);
                break;
            }
            default:
                // In the future, we'll support user plugins here
                throw new UnsupportedOperationException("Unknown processing step method " + method);
            }
        }
        if (mapValues != null && !mapValues.isEmpty()) {
            // Finally, apply any value mappings specified.
            String mappedResult = mapValues.get(result);
            if (mappedResult != null)
                result = mappedResult;
        }
        return result;
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
                    throw new BlackLabRuntimeException("Link value field " + valueField + " has no values (null)!");
                }
                results.addAll(metadataField);
            }
            List<String> resultAfterProcessing = new ArrayList<>();
            for (String inputValue : results) {
                inputValue = StringUtil.sanitizeAndNormalizeUnicode(inputValue);
                resultAfterProcessing.addAll(processStringMultipleValues(inputValue, linkValue.getProcess(), null));
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
                    throw new BlackLabRuntimeException("Could not find or parse linked document for " + documentName + moreInfo, e);
            }
        }
    }

    protected List<String> processStringMultipleValues(String input, List<ConfigProcessStep> process, Map<String, String> mapValues) {
        // If there's no processing to be done (the most common case), skip the list allocation.
        if (process.isEmpty() && (mapValues == null || mapValues.isEmpty()))
            return List.of(input);

        List<String> result = new ArrayList<>();
        result.add(input);

        for (ConfigProcessStep step : process) {
            String method = step.getMethod();
            Map<String, String> param = step.getParam();

            switch (method) {
            case "replace":
                // keep only replaced strings, or originals as well?
                boolean keepAll = param.getOrDefault("keep", "replaced").equals("all");
                for (int i = 0; i < result.size(); ++i) {
                    String afterReplace = opReplace(result.get(i), param);
                    if (keepAll) {
                        // We want to keep the original and add the result as well.
                        // Note that we insert it after the original to keep things in a nice order.
                        result.add(i + 1, afterReplace);
                        i++;
                    } else {
                        // Replace the original version with the result.
                        result.set(i, afterReplace);
                    }
                }
                break;
            case "default":
                for (int i = 0; i < result.size(); ++i) {
                    result.set(i, opDefault(result.get(i), param));
                }
                break;
            case "append":
                for (int i = 0; i < result.size(); ++i) {
                    result.set(i, opAppend(result.get(i), param));
                }
                break;
            case "split": {
                ArrayList<String> r = new ArrayList<>();
                for (String s : result) {
                    r.addAll(opSplit(s, param));
                }
                result = r;
                break;
            }
            case "chatFormatAgeToMonths":
                for (int i = 0; i < result.size(); ++i) {
                    result.set(i, opChatFormatAgeToMonths(result.get(i)));
                }
                break;
            case "strip":
                for (int i = 0; i < result.size(); ++i) {
                    result.set(i, opStrip(result.get(i), param));
                }
                break;
            case "parsePos":
            {
                // Get individual feature out of a part of speech string like "NOU(gender=f,number=p)"
                String field = param.getOrDefault("field", "_");
                for (int i = 0; i < result.size(); ++i) {
                    result.set(i, opParsePartOfSpeech(result.get(i), field));
                }
                break;
            }
            case "concatDate":
                String s = opConcatDate(param);
                if (!s.isEmpty()) {
                    result.set(0, s);
                }
                break;
            default:
                // In the future, we'll support user plugins here
                throw new UnsupportedOperationException("Unknown processing step method " + method);
            }
        }
        if (mapValues != null && !mapValues.isEmpty()) {
            // Finally, apply any value mappings specified.
            for (int i = 0; i < result.size(); ++i) {
                String mappedResult = mapValues.get(result.get(i));
                if (mappedResult != null)
                    result.set(i, mappedResult);
            }
        }
        return result;
    }

    static final Pattern MAIN_POS_PATTERN = Pattern.compile("^([^(]+)(\\s*\\(.*\\))?$");

    static final Pattern FEATURE_PATTERN = Pattern.compile("^[^(]+(\\s*\\((.*)\\))?$");

    static String opParsePartOfSpeech(String result, String field) {
        // Trim character/string from beginning and end
        result = StringUtil.trimWhitespace(result);
        if (field.equals("_")) {
            //  Get main pos: A(b=c,d=e) -> A
            return MAIN_POS_PATTERN.matcher(result).replaceAll("$1");
        } else {
            //  Get feature: A(b=c,d=e) -> e  (if field == d)
            String featuresString = FEATURE_PATTERN.matcher(result).replaceAll("$2");
            return Arrays.stream(featuresString.split(","))
                .map(feat -> feat.split("="))
                .filter(featParts -> StringUtil.trimWhitespace(featParts[0]).equals(field))
                .map(featParts -> StringUtil.trimWhitespace(featParts[1]))
                .findFirst()
                .orElse("");
        }
    }

    static String opStrip(String result, Map<String, String> param) {
        // Trim character/string from beginning and end
        String stripChars = param.getOrDefault("chars", " ");
        result = StringUtils.strip(result, stripChars);
        return result;
    }
    
    /**
     * Concatenate 3 separate date fields into one. 
     * E.g. 
     * Year: 2000
     * Month: 10
     * Day: 19
     * 
     * Result: "20001019"
     *  
     * @param param operation parameters
     * @return resulting value
     */
    protected String opConcatDate(Map<String, String> param) {
        String yearField = param.get("yearField");
        String monthField = param.get("monthField");
        String dayField = param.get("dayField");
        String mode = param.get("autofill");
        if (yearField == null || monthField == null || dayField == null || mode == null || !(mode.equalsIgnoreCase("start")||mode.equalsIgnoreCase("end")))
            throw new InvalidInputFormatConfig("concatDate needs parameters yearField, monthField, dayField, and autoFill ('start' or 'end')");
        
        boolean isStart = mode.equalsIgnoreCase("start");
        Integer y, m, d;
        try { y = Integer.parseInt(getMetadataField(yearField).get(0)); } catch (Exception e) { y = null; }
        try { m = Integer.parseInt(getMetadataField(monthField).get(0)); } catch (Exception e) { m = null; }
        try { d = Integer.parseInt(getMetadataField(dayField).get(0)); } catch (Exception e) { d = null; }
        
        if (y == null) return "";
        if (m == null || m > 12 || m < 1) m = isStart ? 1 : 12;
        int maxDay = YearMonth.of(y, m).lengthOfMonth();
        if (d == null || d > maxDay || d < 1) d = isStart ? 1 : maxDay; 
        
        return StringUtils.leftPad(y.toString(), 4, '0') + StringUtils.leftPad(m.toString(), 2, '0') + StringUtils.leftPad(d.toString(), 2, '0'); 
    }
    
    protected String opChatFormatAgeToMonths(String result) {
        // 1;10.30 => 1 jaar, 10 maanden, 30 dagen => afgerond 23 maanden?
        String[] parts = result.split("[;.]", 3);
        int years = 0;
        int months = 0;
        int days = 0;
        try {
            years = Integer.parseInt(parts[0]);
            months = parts.length <= 1 ? 0 : Integer.parseInt(parts[1]);
            days = parts.length <= 2 ? 0 : Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            warn("action 'opChatFormatAgeToMonths': illegal value " + result);
        }
        return Integer.toString(years * 12 + months + (days > 14 ? 1 : 0) );
    }

    /**
     * Split the result string on a separator and return one or all parts.
     *
     * @param result the string to split
     * @param param
     * <pre>
     * - "separator" for the separator (defaults to ;),
     * - "keep" for the keep index, accepts numbers or the special string "all" (defaults to -1)
     *      if "keep" <= 0 returns the first part.
     *      if "keep" > number of splits return empty string.
     *      if "keep" == "all" return all parts
     *      if "keep" == "both" return both the original (unsplit) string and all parts
     * </pre>
     */
    private List<String> opSplit(String result, Map<String, String> param) {
        // Split on a separator regex and keep one or all parts (first part by default)
        String separator = param.getOrDefault("separator", ";");
        String keep = param.getOrDefault("keep", "-1").toLowerCase();
        String[] parts = result.split(separator, -1);

        if (keep.equals("all")) {
            return Arrays.asList(parts);
        }
        if (keep.equals("both")) {
            ArrayList<String> r = new ArrayList<>();
            r.add(result);
            Collections.addAll(r, parts);
            return r;
        }

        int i = -1;
        try { i = Integer.parseInt(keep); } catch (NumberFormatException e) { /* handled below */ }
        if (i < 0) {
            warn("action 'split', parameter 'keep': must be at least 0");
            i = 0;
        }

        return List.of(i < parts.length ? parts[i] : "");
    }

    /**
     * Appends a constant value, or the value of a metadata field to the result string.
     *
     * @param result the input string
     * @param param
     * <pre>
     * - "separator" for the separator (defaults to " ")
     * - "field" for the metadata field whose value will be appended
     * - "value" for a constant value ("field" takes precedence if it exists)
     * </pre>
     */
    private String opAppend(String result, Map<String, String> param) {
        String separator = param.getOrDefault("separator", " ");
        String field = param.get("field");
        String value;
        if (field != null)
            value = StringUtils.join(getMetadataField(field), separator);
        else
            value = param.get("value");
        if (value != null && value.length() > 0) {
            if (result.length() > 0)
                result += separator;
            result += value;
        }
        return result;
    }

    /**
     * Optionally replace an empty result with a constant value, or the value of a metadata field.
     *
     * @param result the input string
     * @param param
     * <pre>
     * - "field" for the metadata field whose value will be used
     * - "separator" to join the metadata field if it contains multiple values (defaults to ;)
     * - "value" for a constant value ("field" takes precedence if it exists)
     * </pre>
     */
    private String opDefault(String result, Map<String, String> param) {
        if (result.length() == 0) {
            String field = param.get("field");
            String value;
            String sep = param.getOrDefault("separator", ";");
            if (field != null)
                value = StringUtils.join(getMetadataField(field), sep);
            else
                value = param.get("value");
            if (value != null)
                result = value;
        }
        return result;
    }

    /**
     * Perform a regex replace on result. Allows group references.
     *
     * @param param
     * <pre>
     * - "find" for the regex
     * - "replace" the replacement string
     * </pre>
     */
    private static String opReplace(String result, Map<String, String> param) {
        String find = param.get("find");
        String replace = param.get("replace");
        if (find == null || replace == null)
            throw new InvalidInputFormatConfig("replace needs parameters find and replace");
        try {
            result = result.replaceAll(find, replace);
        } catch (PatternSyntaxException e) {
            throw new InvalidInputFormatConfig("Syntax error in replace regex: " + find);
        }
        return result;
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
        if (f != null) {
            value = processString(value, f.getProcess(), f.getMapValues());
        }
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
