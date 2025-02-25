package nl.inl.blacklab.indexers.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.InvalidInputFormatConfig;
import nl.inl.blacklab.exceptions.MalformedInputFile;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.index.annotated.AnnotationWriter;
import nl.inl.blacklab.search.indexmetadata.RelationUtil;
import nl.inl.util.FileReference;
import nl.inl.util.StringUtil;

/**
 * Indexer for the CoNLL-U format.
 *
 * See <a href="https://universaldependencies.org/format.html">https://universaldependencies.org/format.html</a>
 *
 * Example data: https://github.com/UniversalDependencies/UD_Dutch-LassySmall/
 *
 * We don't support all features (yet).
 */
public class DocIndexerCoNLLU extends DocIndexerTabularBase {

    public static final int COL_ID = 0;

    public static final int COL_FORM = 1;

    public static final int COL_HEAD = 6;

    public static final int COL_DEP = 7;

    public static final String ANNOTATION_MULTIWORD_TOKEN = "mwt";

    private AnnotationWriter multiWordAnnotation;

    private StringBuilder csvData;

    private BufferedReader inputReader;

    private int lineNumber;

    public DocIndexerCoNLLU() {
        super("\\|");
    }

    @Override
    public void setConfigInputFormat(ConfigInputFormat config) {
        if (config.getAnnotatedFields().size() != 1)
            throw new InvalidInputFormatConfig("Tabular type must have only 1 annotated field");
        super.setConfigInputFormat(config);
    }

    public void setDocument(Reader reader) {
        inputReader = reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader);
    }

    @Override
    public void setDocument(FileReference file) {
        super.setDocument(file);
        setDocument(file.getSinglePassReader());
    }

    @Override
    public void close() throws BlackLabRuntimeException {
        try {
            if (inputReader != null)
                inputReader.close();
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override protected void endDocument() {
        super.endDocument();

        // Clear csvData (we use this to store the document)
        csvData.delete(COL_ID, csvData.length());
    }

    /** Single- or double-quoted attribute in a tag */
    private static final Pattern SENTENCE_ATTRIBUTE_LINE = Pattern.compile(
            "^#\\s*(\\w+)\\s*=\\s*(.+)$");

    private static class Span {
        final int start;
        final int end;

        public Span(int start, int end) {
            this.start = start;
            this.end = end;
        }

        public Span(int start) {
            this.start = start;
            this.end = start + 1;
        }
    }

    @Override
    public void index() throws MalformedInputFile, PluginException, IOException {
        super.index();

        csvData = new StringBuilder();
        startDocument();

        // For the configured annotated field...
        ConfigAnnotatedField annotatedField = config.getAnnotatedFields().values().iterator().next();
        setCurrentAnnotatedFieldName(annotatedField.getName());
        multiWordAnnotation = getAnnotation(ANNOTATION_MULTIWORD_TOKEN);

        boolean inSentence = false;

        // For each token position
        Map<String, String> sentenceAttr = new LinkedHashMap<>();
        int sentenceStartPosition = -1;
        lineNumber = COL_ID;
        while (true) {

            // Read and trim next line
            String origLine = inputReader.readLine();
            if (origLine == null)
                break; // end of file
            lineNumber++;
            String line = StringUtil.trimWhitespace(origLine);

            // Is it empty?
            if (line.length() == COL_ID) {
                if (inSentence) {
                    // Empty line ends sentence
                    inlineTag("s", false, null);
                    inSentence = false;
                }
                continue; // skip empty lines
            }

            // Sentence attribute line? ("comment", but not really used as such)
            if (line.startsWith("#")) {
                if (!inSentence) {
                    // Comments before sentence contain attributes for the sentence tag.
                    // Collect them until we actually start the sentence.
                    // (Mid-sentence comment lines are ignored)
                    Matcher m = SENTENCE_ATTRIBUTE_LINE.matcher(line);
                    if (m.matches()) {
                        String attributeName = m.group(1);
                        String attributeValue = m.group(2);
                        sentenceAttr.put(attributeName, attributeValue);
                    }
                }
                continue;
            }

            // Split the line into columns
            List<String> record = List.of(line.split("\t", -1));

            String id = record.get(COL_ID);
            if (id.contains(".")) {
                // skip decimal ids; these are "empty tokens" (i.e. implied words that weren't actually in the sentence)
                // ex. Sue likes coffee and Bill [likes] tea.
                // the bracketed word 'likes' would get a decimal id.
                continue;
            }

            if (!inSentence) {
                // We're not in a sentence yet and encountered a value line; start the sentence now.
                inlineTag("s", true, sentenceAttr);
                sentenceAttr.clear();
                inSentence = true;
                sentenceStartPosition = getCurrentTokenPosition();
            }

            // Store this line now, so the start/end offsets are correct
            if (isStoreDocuments())
                csvData.append(origLine).append("\n");

            Span span = idSpan(id, sentenceStartPosition);
            boolean isMultiWordToken = id.contains("-");
            if (isMultiWordToken && multiWordAnnotation != null) {
                String form = record.get(COL_FORM);
                for (int position = span.start; position <= span.end; position++) {
                    multiWordAnnotation.addValueAtPosition(form, position, null);
                }
                continue;
            }

            // Index dependency relation
            String strHead = record.size() > COL_HEAD ? record.get(COL_HEAD) : "_";
            if (!strHead.isEmpty() && !strHead.equals("_")) {
                boolean isRoot = strHead.equals("0");
                String relationType = record.size() > COL_DEP ? record.get(COL_DEP) : "_";
                String fullRelationType = RelationUtil.fullType(
                        RelationUtil.CLASS_DEPENDENCY, relationType);
                if (!isRoot) {
                    // Regular relation with source and target.
                    Span headSpan = idSpan(strHead, sentenceStartPosition);
                    tagsAnnotation().indexRelation(fullRelationType, false, headSpan.start, headSpan.end,
                            span.start, span.end, null, getIndexType());
                } else {
                    // Root relation has no source. We just use the target positions for the source, so
                    // the relation is stored in a sane position.
                    tagsAnnotation().indexRelation(fullRelationType, true, span.start, span.end,
                            span.start, span.end, null, getIndexType());
                }
            }

            // Index each annotation
            beginWord();
            try {
                // Index all annotations defined in the config file
                for (ConfigAnnotation annotation: annotatedField.getAnnotationsFlattened().values()) {
                    String value;
                    if (StringUtils.isEmpty(annotation.getValuePath()))
                        continue; // e.g. mwt annotation doesn't have one
                    if (annotation.isValuePathInteger()) {
                        int i = annotation.getValuePathInt() - 1;
                        if (i < record.size()) {
                            value = record.get(i);
                        } else
                            value = "";
                    } else {
                        throw new RuntimeException("valuePath must be a column number");
                    }
                    indexValue(annotation, value);
                }
            } finally {
                endWord();
            }
        }

        endDocument();
    }

    private Span idSpan(String id, int currentSentenceStart) {
        if (id.matches("\\d+-\\d+")) {
            // Span ID; determine boundaries
            String[] parts = id.split("-");
            int first = Integer.parseInt(parts[COL_ID]) - 1;
            int second = Integer.parseInt(parts[1]) - 1;
            return new Span(currentSentenceStart + first, currentSentenceStart + second);
        } else if (id.matches("\\d+"))
            return new Span(currentSentenceStart + Integer.parseInt(id) - 1);
        else
            throw new IllegalArgumentException("Invalid ID (must be a number or two numbers separated by dash): '" + id + "' on line " + lineNumber);
    }

    @Override
    public void indexSpecificDocument(String documentExpr) {
        // documentExpr is ignored because tabular format files always contain 1 document
        try {
            index();
        } catch (Exception e) {
            throw BlackLabRuntimeException.wrap(e);
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
