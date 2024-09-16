package nl.inl.blacklab.indexers.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.exceptions.MalformedInputFile;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.index.DocWriter;
import nl.inl.blacklab.index.annotated.AnnotatedFieldWriter;
import nl.inl.blacklab.index.annotated.AnnotationSensitivities;
import nl.inl.blacklab.index.annotated.AnnotationWriter;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.FieldType;
import nl.inl.util.FileReference;
import nl.inl.util.StringUtil;

/**
 * An educational DocIndexer to learn how to implement your own.
 *
 * If your input data cannot easily be described using the standard
 * .blf.yaml configuration files, you might need to implement your own
 * DocIndexer class. This shows how to do that.
 *
 * This example is designed to explain the most basic building blocks of
 * indexing data in BlackLab, not to be practical or robust. It covers only
 * the most important features. If something is unclear, please ask.
 *
 * The input files that this DocIndexer can process are a sort of "assembly
 * language" for BlackLab indexing. They contain one instruction per line,
 * each instruction being an uppercase word followed by 0 or more parameter(s),
 * whitespace-separated. Anything after a hash symbol is ignored.
 *
 * Below is a small example. For a larger example, see example/example.txt in
 * the resources dir.
 *
 * <code>
 * # Add document
 * DOC_START                     # begin a new document
 *   # Add document-level metadata
 *   METADATA author Pete Puck
 *   METADATA title This is a test.
 *
 *   # Annotated field: contents
 *   FIELD_START contents
 *     VAL word The
 *     VAL lemma the
 *     ADVANCE 1                 # Go to next token position
 *     VAL word quick
 *     VAL lemma quick
 *     ADVANCE 1                 # Go to next token position
 *     VAL word brown
 *     VAL lemma brown
 *     ADVANCE 1                 # Go to next token position
 *     VAL word fox
 *     VAL lemma fox
 *     ADVANCE 1                 # Go to next token position
 *     VAL word jumps
 *     VAL lemma jump
 *
 *     SPAN named-entity 0 4     # Add span according to token positions
 *                               # (note that start is inclusive, end exclusive)
 *   FIELD_END
 *
 * DOC_END                       # end document and add to index
 * </code>
 *
 */
public class DocIndexerExample extends DocIndexerBase {

    private BufferedReader reader;

    /** Are we inside a document? */
    private boolean inDoc = false;

    /** Name of annotated field we're processing or null if not in annotated field part */
    private String currentAnnotatedField = null;

    /** Are we in an annotated field block and have we called beginWord()? Then make sure to call endWord(). */
    private boolean inWord = false;

    /** What's the token position of the current token we're parsing?
     * (only valid if currentAnnotatedField != null) */
    private int currentTokenPosition;

    /** Character position within the input file. */
    private int characterPosition = 0;

    /** Have we been initialized? */
    private boolean inited = false;

    private StringBuilder wholeDocument = new StringBuilder();

    public DocIndexerExample() {
    }

    @Override
    public void setDocWriter(DocWriter docWriter) {
        super.setDocWriter(docWriter);
        init();
    }

    public void init() {
        if (!inited) {
            inited = true;

            // Create our index structure: annotated and metadata fields.
            createSimpleAnnotatedField("contents", List.of("word", "lemma"));
            createMetadataField("pid", FieldType.UNTOKENIZED);
            createMetadataField("author", FieldType.UNTOKENIZED);
            createMetadataField("title", FieldType.TOKENIZED);
        }
    }



    /**
     * Create a simple annotated field.
     *
     * All of the given annotations get a forward index and will be indexed
     * accent/case insensitively only. A special annotation for spans ("tags")
     * will also be created.
     *
     * @param name annotated field name
     * @param annotations annotations on this field
     */
    private void createSimpleAnnotatedField(String name, List<String> annotations) {
        // Configure an annotated field "contents".
        // Add two annotations, "word" and "lemma". First one added will be the main annotation.
        ConfigAnnotatedField field = new ConfigAnnotatedField(name);
        for (String annotationName: annotations) {
            addAnnotationToFieldConfig(field, annotationName,
                    AnnotationSensitivities.ONLY_INSENSITIVE,
                    true);
        }
        // Add a special annotation where we can index arbitrary spans.
        addAnnotationToFieldConfig(field, AnnotatedFieldNameUtil.relationAnnotationName(getIndexType()),
                AnnotationSensitivities.ONLY_SENSITIVE, false);
        // Add a special annotation where whitespace and punctuation between words is stored.
        addAnnotationToFieldConfig(field, AnnotatedFieldNameUtil.PUNCTUATION_ANNOT_NAME,
                AnnotationSensitivities.ONLY_SENSITIVE, true);

        // Create and add AnnotatedFieldWriter so we can index this field
        createAnnotatedFieldWriter(field);
    }

    private static ConfigAnnotation addAnnotationToFieldConfig(ConfigAnnotatedField config, String name,
            AnnotationSensitivities sensitivities, boolean forwardIndex) {
        ConfigAnnotation annot = new ConfigAnnotation();
        annot.setName(name);
        annot.setSensitivity(sensitivities);
        annot.setForwardIndex(forwardIndex);
        config.addAnnotation(annot);
        return annot;
    }

    private void createAnnotatedFieldWriter(ConfigAnnotatedField fieldContents) {
        // Add the configured field to our index metadata
        getDocWriter().metadata().annotatedFields().addFromConfig(fieldContents);

        // Create a AnnotatedFieldWriter for this field so we can index it
        Collection<ConfigAnnotation> annots = fieldContents.getAnnotations().values();
        Iterator<ConfigAnnotation> annotIt = annots.iterator();
        ConfigAnnotation mainAnnotation = annotIt.next();
        AnnotatedFieldWriter contents = new AnnotatedFieldWriter(getDocWriter(), fieldContents.getName(),
                mainAnnotation.getName(), mainAnnotation.getSensitivitySetting(),
                false,
                getDocWriter().needsPrimaryValuePayloads());
        while (annotIt.hasNext()) {
            ConfigAnnotation annot = annotIt.next();
            boolean includePayloads = annot.getName().equals(AnnotatedFieldNameUtil.relationAnnotationName(getIndexType()));
            contents.addAnnotation(annot.getName(), annot.getSensitivitySetting(), includePayloads, annot.createForwardIndex());
        }
        addAnnotatedField(contents);
    }

    private ConfigMetadataField createMetadataField(String name, FieldType type) {
        ConfigMetadataField metaPidConfig = new ConfigMetadataField();
        metaPidConfig.setName(name);
        metaPidConfig.setType(type);
        getDocWriter().metadata().metadataFields().addFromConfig(metaPidConfig);
        return metaPidConfig;
    }

    @Override
    public void close() {

    }

    @Override
    public void index() throws IOException, MalformedInputFile, PluginException {
        // Execute the commands one line at a time.
        while (true) {
            String line = reader.readLine();
            if (line == null)
                break;

            // If you want to store all the input documents in the BlackLab index
            // for easy retrieval and highlighting, you must capture the content.
            wholeDocument.append(line + "\n");

            // Try to keep track of character position, for highlighting.
            // Assumes lines end with only a newline. May not be accurate.
            characterPosition += line.length() + 1;

            // Execute the command on this line, if any.
            processLine(line);
        }
    }

    private void processLine(String line) {
        // Remove comments and leading/trailing whitespace
        line = StringUtil.trimWhitespace(line.replaceAll("#.*$", ""));
        if (line.isEmpty())
            return;

        // Split on whitespace and separate command from parameters
        String[] parts = line.split(StringUtil.REGEX_WHITESPACE, -1);
        String command = parts[0];
        String[] parameters = Arrays.copyOfRange(parts, 1, parts.length);

        // Execute command
        executeCommand(command, parameters);
    }

    private void executeCommand(String command, String[] parameters) {
        if (currentAnnotatedField != null) {
            // We're inside an annotated field value block (nested inside a document block).
            executeValueCommand(command, parameters);
        } else if (inDoc) {
            // We're inside a document definition.
            executeDocumentCommand(command, parameters);
        } else {
            // Top-level. Look for documents.
            switch (command) {
            case "DOC_START":
                // Start a document.
                startDocument();
                inDoc = true;
                break;

            default:
                throw new RuntimeException("Command " + command + " cannot appear at top level");
            }
        }
    }

    /**
     * Handle command inside an annotated field block.
     *
     * @param command command to handle
     * @param parameters parameters
     */
    private void executeValueCommand(String command, String[] parameters) {
        switch (command) {
        case "VAL":
            // Add annotation value at current position.
            if (!inWord) {
                // Signal that we're starting a new token position.
                beginWord();
                inWord = true; // remember to call endWord() later
            }
            String annotationName = parameters[0];
            String value = parameters[1];
            annotationValue(annotationName, value, currentTokenPosition);
            break;

        case "ADVANCE":
            // Increase current token position.
            // (value is the position increment for the next token we'll add)
            if (inWord) {
                // We're done with the current token position.
                endWord();
                inWord = false;
            }
            int posIncr = Integer.parseInt(parameters[0]);
            currentTokenPosition += posIncr;
            break;

        case "SPAN":
            // Add a named span, e.g. <p/>, <s/>, <entity/>, etc.
            // at the specified token positions.
            // CAUTION: right now, we can only add spans at positions that we have already
            //   indexed values for! So add the spans either while or after indexing values,
            //   not before.
            // NOTE: an alternative way of indexing spans is to do it while you encounter start and end
            //   tags inline. See DocIndexerBase.inlineTag().
            String spanType = parameters[0];
            int spanStart = Integer.parseInt(parameters[1]);
            int spanEnd = Integer.parseInt(parameters[2]);   // end position (exclusive)
            Map<String, String> spanAttributes = new HashMap<>();
            for (int i = 3; i < parameters.length; i += 2) {
                String attName = parameters[i];
                String attValue = parameters[i + 1];
                spanAttributes.put(attValue, attValue);
            }

            tagsAnnotation().indexInlineTag(spanType, spanStart, spanEnd,
                    spanAttributes, getIndexType(), tagsAnnotation().getNextRelationId());
            break;

        case "FIELD_END":
            // End the annotated field block.
            if (inWord) {
                // We're done with the current token position.
                endWord();
                inWord = false;
            }
            currentAnnotatedField = null;
            break;

        default:
            throw new RuntimeException("Command " + command + " cannot appear inside annotated field block");
        }
    }

    @Override
    protected void endWord() {
        super.endWord();

        // Make sure that all annotations are at the same token position.
        // (we don't want annotations to run out of synch)
        for (AnnotationWriter aw: getAnnotatedField(currentAnnotatedField).annotationWriters()) {
            while (aw.lastValuePosition() < currentTokenPosition) {
                aw.addValue("");
            }
        }
    }

    /**
     * Handle command inside a document block.
     *
     * @param command command to handle
     * @param parameters parameters
     */
    private void executeDocumentCommand(String command, String[] parameters) {
        switch (command) {
        case "METADATA":
            // Gives a document metadata value.
            String name = parameters[0];
            String[] valueParts = Arrays.copyOfRange(parameters, 1, parameters.length);
            addMetadataField(name, StringUtils.join(valueParts, " "));
            break;

        case "FIELD_START":
            // Starts an annotated field block.
            currentAnnotatedField = parameters[0];
            //posIncr = 1; // initialize at default value
            currentTokenPosition = 0;
            setCurrentAnnotatedFieldName(currentAnnotatedField);
            break;

        case "DOC_END":
            // Ends document.
            inDoc = false;
            endDocument();
            break;

        default:
            throw new RuntimeException("Command " + command + " cannot appear inside document");
        }
    }

    @Override
    protected int getCharacterPosition() {
        // This should ideally keep track of character position within the input document, to be used
        // for highlighting in BlackLab later. If you don't use BlackLab's highlighting, you don't need this.
        return characterPosition;
    }

    @Override
    public void indexSpecificDocument(String documentExpr) {
        // Used with linked documents to index part of a linked document.
        // For example, if you're indexing a document A that links to a document B that contains metadata
        // for many documents, you only want to index the metadata for document A in document B. The
        // document expression indicates how to find that metadata inside document B. For XML, this would probably
        // be an XPath expression.
        // If this doesn't fit your needs, it is of course always fine to ignore this and write your own code
        // to handle your linked resources.
    }

    @Override
    protected void storeDocument() {
        // If you want to store your input documents in the BlackLab index for easy retrieval and highlighting
        // later, you should uncomment the next line.
        storeWholeDocument(wholeDocument.toString());
        wholeDocument = new StringBuilder(); // reset for next document
    }

    /**
     * Sets document to be indexed.
     *
     * NOTE: there are other setDocument methods that you can override. In some
     * cases, depending on how you process your data, you can optimize indexing by
     * implementing another method. For example, DocIndexerXpath uses the byte array
     * because that's what VTD-XML expects.
     *
     * @param reader document to index
     */
    public void setDocument(Reader reader) {
        this.reader = IOUtils.toBufferedReader(reader);
    }

    @Override
    public void setDocument(FileReference file) {
        super.setDocument(file);
        setDocument(file.getSinglePassReader());
    }
}
