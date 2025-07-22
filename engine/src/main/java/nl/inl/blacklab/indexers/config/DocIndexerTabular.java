package nl.inl.blacklab.indexers.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.exceptions.InvalidInputFormatConfig;
import nl.inl.blacklab.exceptions.MalformedInputFile;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.util.FileReference;
import nl.inl.util.StringUtil;

/**
 * An indexer for tabular file formats, such as tab-separated or comma-separated
 * values.
 */
public class DocIndexerTabular extends DocIndexerTabularBase {

    // Tabular fileTypeOption keys
    public static final String FT_OPT_TYPE = "type";
    public static final String FT_OPT_EXPECT_COLUMN_NAMES = "columnNames";
    public static final String FT_OPT_DELIMITER = "delimiter";
    public static final String FT_OPT_QUOTE = "quote";
    public static final String FT_OPT_ALLOW_SEPARATORS_AFTER_INLINE_TAGS = "allowSeparatorsAfterInlineTags";
    public static final String FT_OPT_HAS_INLINE_TAGS = "inlineTags";
    public static final String FT_OPT_HAS_GLUE_TAGS = "glueTags";
    public static final String FT_OPT_MULTIPLE_VALUES_SEPARATOR = "multipleValuesSeparator";

    /** Tabular types we support */
    private enum Type {
        CSV,
        TSV;

        public static Type fromStringValue(String str) {
            return switch (str.toUpperCase()) {
                case "TDF" -> TSV;
                case "EXCEL" -> CSV;
                default -> valueOf(str.toUpperCase());
            };
        }
    }

    private Iterable<CSVRecord> records;

    private CSVFormat tabularFormat = CSVFormat.EXCEL;

    private StringBuilder csvData;

    private boolean hasInlineTags;

    private boolean hasGlueTags;

    /**
     * After an inline tag such as {@code <s>}, may there be separator character(s)
     * like on the non-tag lines? By default, this is not allowed, but this option
     * can be turned on in the configuration file.
     */
    private boolean allowSeparatorsAfterInlineTags;

    private BufferedReader inputReader;

    public DocIndexerTabular() {
        super(";");
    }

    @Override
    public void setConfigInputFormat(ConfigInputFormat config) {
        if (config.getAnnotatedFields().size() > 1)
            throw new InvalidInputFormatConfig("Tabular type can only have 1 annotated field");
        super.setConfigInputFormat(config);
        Map<String, String> opt = config.getFileTypeOptions();
        Type type = opt.containsKey(FT_OPT_TYPE) ? Type.fromStringValue(opt.get(FT_OPT_TYPE)) : Type.CSV;
        switch (type) {
        case TSV:
            tabularFormat = CSVFormat.TDF;
            break;
        case CSV:
            tabularFormat = CSVFormat.EXCEL;
            break;
        default:
            throw new InvalidInputFormatConfig("Unknown tabular type " + opt.get(FT_OPT_TYPE) + " (use csv or tsv)");
        }
        if (opt.containsKey(FT_OPT_EXPECT_COLUMN_NAMES) && opt.get(FT_OPT_EXPECT_COLUMN_NAMES).equalsIgnoreCase("true"))
            tabularFormat = tabularFormat.withFirstRecordAsHeader();
        if (opt.containsKey(FT_OPT_DELIMITER) && !opt.get(FT_OPT_DELIMITER).isEmpty())
            tabularFormat = tabularFormat.withDelimiter(opt.get(FT_OPT_DELIMITER).charAt(0));
        if (opt.containsKey(FT_OPT_QUOTE) && !opt.get(FT_OPT_QUOTE).isEmpty())
            tabularFormat = tabularFormat.withQuote(opt.get(FT_OPT_QUOTE).charAt(0));
        else
            tabularFormat = tabularFormat.withQuote(null); // disable quotes altogether
        allowSeparatorsAfterInlineTags = opt.containsKey(FT_OPT_ALLOW_SEPARATORS_AFTER_INLINE_TAGS)
                && opt.get(FT_OPT_ALLOW_SEPARATORS_AFTER_INLINE_TAGS).equalsIgnoreCase("true");
        hasInlineTags = opt.containsKey(FT_OPT_HAS_INLINE_TAGS) && opt.get(FT_OPT_HAS_INLINE_TAGS).equalsIgnoreCase("true");
        hasGlueTags = opt.containsKey(FT_OPT_HAS_GLUE_TAGS) && opt.get(FT_OPT_HAS_GLUE_TAGS).equalsIgnoreCase("true");
        if (opt.containsKey(FT_OPT_MULTIPLE_VALUES_SEPARATOR)) {
            logger.warn("The option " + FT_OPT_MULTIPLE_VALUES_SEPARATOR + " is deprecated; use a processing step 'split' instead.");
            multipleValuesSeparatorRegex = opt.get(FT_OPT_MULTIPLE_VALUES_SEPARATOR);
        }
    }

    public void setDocument(Reader reader) {
        try {
            inputReader = reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader);
            records = tabularFormat.parse(inputReader);
        } catch (IOException e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    @Override
    public void setDocument(FileReference file) {
        super.setDocument(file);
        setDocument(file.getSinglePassReader());
    }

    @Override
    public void close() throws RuntimeException {
        try {
            if (inputReader != null)
                inputReader.close();
        } catch (IOException e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    @Override protected void endDocument() {
        super.endDocument();

        // Clear csvData (we use this to store the document)
        csvData.delete(0, csvData.length());
    }

    /**
     * Regex for recognizing open or close tag and capturing tag name and attributes
     * part
     */
    private static final Pattern REGEX_TAG = Pattern.compile("^\\s*<\\s*(/\\s*)?(\\w+)((\\b[^>]+)?)>\\s*$");

    /** Glue tag (if present) */
    private static final String GLUE_TAG_NAME = "g";

    @Override
    public void index() throws MalformedInputFile, PluginException, IOException {
        super.index();

        // If a documentPath was specified, look for that as the document tag
        boolean lookForDocumentTags = !config.getDocumentPath().equals("/");

        csvData = new StringBuilder();
        if (!lookForDocumentTags)
            startDocument();

        // Do we need to look for tags at all?
        boolean lookForTags = !lookForDocumentTags || hasInlineTags || hasGlueTags;

        // Are we inside a document now?
        boolean inDocument = !lookForDocumentTags;

        try (CSVPrinter p = new CSVPrinter(csvData, tabularFormat)) {
            // For the configured annotated field...
            for (ConfigAnnotatedField annotatedField : config.getAnnotatedFields().values()) {
                setCurrentAnnotatedFieldName(annotatedField.getName());

                // For each token position
                for (CSVRecord record : records) {
                    if (record.size() == 0)
                        continue; // skip empty lines

                    // If this format contains tags...
                    if (lookForTags && (record.size() == 1 || allowSeparatorsAfterInlineTags)) {
                        // Is this a tag line instead of a token line?
                        String possibleTag = record.get(0);
                        Matcher m = REGEX_TAG.matcher(possibleTag);
                        if (m.find()) {
                            // It's a document tag, an inline tag or a glue tag
                            boolean isOpenTag = m.group(1) == null;
                            String tagName = m.group(2);
                            String rest = StringUtil.trimWhitespace(m.group(3));
                            boolean selfClosing = rest.endsWith("/");
                            if (!isOpenTag && selfClosing)
                                throw new MalformedInputFile("Close tag must not also end with /: " + tagName);
                            if (selfClosing)
                                rest = rest.substring(0, rest.length() - 1);
                            Map<String, List<String>> attributes = getAttr(rest);

                            if (lookForDocumentTags && tagName.equals(config.getDocumentPath())) {
                                // Document tag.
                                if (inDocument && isOpenTag)
                                    throw new MalformedInputFile("Found document open tag inside document");
                                if (!inDocument && !isOpenTag)
                                    throw new MalformedInputFile(
                                            "Found document close tag outside of document");
                                if (isOpenTag) {
                                    // Start a new document and add attributes as metadata fields
                                    inDocument = true;
                                    startDocument();
                                    for (Map.Entry<String, List<String>> e : attributes.entrySet()) {
                                        for (String value: e.getValue()) {
                                            value = processMetadataValue(e.getKey(), value);
                                            addMetadataField(e.getKey(), value);
                                        }
                                    }
                                } else {
                                    endDocument();
                                    inDocument = false;
                                }
                            } else if (hasGlueTags && tagName.equals(GLUE_TAG_NAME)) {
                                // Glue tag. Don't add default punctuation when adding next word.
                                if (!attributes.isEmpty())
                                    warn("Glue tag has attributes: " + attributes);
                                setPreventNextDefaultPunctuation();
                            } else if (inDocument) {
                                inlineTag(tagName, isOpenTag, attributes);
                                if (selfClosing)
                                    inlineTag(tagName, false, null);
                            }
                            continue;
                        }
                    }

                    // It's a regular token
                    beginWord();
                    if (isStoreDocuments()) {
                        p.printRecord(record);
                    }

                    // For each annotation
                    for (ConfigAnnotation annotation : annotatedField.getAnnotationsFlattened().values()) {
                        // Either column number or name
                        String value;
                        if (annotation.isValuePathInteger()) {
                            int i = annotation.getValuePathInt() - 1;
                            if (i < record.size())
                                value = record.get(i);
                            else
                                value = "";
                        } else {
                            if (record.isMapped(annotation.getValuePath()))
                                value = record.get(annotation.getValuePath());
                            else
                                value = "";
                        }
                        indexValue(annotation, value);
                    }
                    endWord();
                }
            }
        }

        if (!lookForDocumentTags)
            endDocument();
    }

    /** Single- or double-quoted attribute in a tag */
    private static final Pattern REGEX_ATTR = Pattern.compile("\\b(\\w+)\\s*=\\s*(\"[^\"]*\"|'[^']*')");

    /**
     * Get attributes from part of an XML tag.
     * @param group the attributes part of the tag
     * @return attributes map
     */
    private static Map<String, List<String>> getAttr(String group) {
        if (group == null)
            return Collections.emptyMap();
        String strAttrDef = StringUtil.trimWhitespace(group);
        Matcher m = REGEX_ATTR.matcher(strAttrDef);
        Map<String, List<String>> attributes = new LinkedHashMap<>();
        while (m.find()) {
            String key = m.group(1);
            String value = m.group(2);
            value = value.substring(1, value.length() - 1); // chop quotes
            attributes.put(key, List.of(value));
        }
        return attributes;
    }

    @Override
    public void indexSpecificDocument(String documentExpr) {
        // documentExpr is ignored because tabular format files always contain 1 document
        try {
            index();
        } catch (Exception e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    @Override
    protected void storeDocument() {
        storeWholeDocument(csvData.toString());
    }

    @Override
    protected int getCharacterPosition() {
        return csvData.length();
    }

}
