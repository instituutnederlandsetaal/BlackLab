package nl.inl.blacklab.indexers.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.exceptions.ErrorIndexingFile;
import nl.inl.blacklab.exceptions.InvalidInputFormatConfig;
import nl.inl.blacklab.index.DocWriter;
import nl.inl.blacklab.index.IndexerStats;
import nl.inl.blacklab.index.InputFormat;
import nl.inl.util.fileprocessor.FileReference;

/**
 * An indexer for tabular file formats, such as tab-separated or comma-separated
 * values.
 */
public class InputFormatTypePlainText extends InputFormatTypeConfig {

    @Override
    public InputFormat createInputFormat(ConfigInputFormat config) {
        return new InputFormatPlainText(config);
    }

    public static class InputFormatPlainText extends InputFormatConfig {

        public InputFormatPlainText(ConfigInputFormat config) {
            super(config);
            if (config.getAnnotatedFields().size() > 1)
                throw new InvalidInputFormatConfig("Plain text type can only have 1 annotated field");
        }

        @Override
        protected Doc createDoc(DocWriter docWriter, FileReference file) {
            return new DocPlainText(docWriter, file);
        }

        protected class DocPlainText extends DocConfig {
            private BufferedReader reader;

            private StringBuilder fullText;

            public DocPlainText(DocWriter docWriter, FileReference file) throws ErrorIndexingFile {
                super(docWriter, file);
            }

            @Override
            public void close() throws RuntimeException {
                try {
                    reader.close();
                } catch (IOException e) {
                    throw BlackLabException.wrapRuntime(e);
                }
            }

            public void setDocument(Reader reader) {
                this.reader = reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader);
            }

            @Override
            public void setDocument(FileReference file) {
                super.setDocument(file);
                setDocument(file.getSinglePassReader());
            }

            static final Pattern REGEX_WORD = Pattern.compile("\\b\\p{L}+\\b");

            @Override
            public IndexerStats index() throws ErrorIndexingFile {
                super.index();

                startDocument();

                fullText = new StringBuilder();

                // For the configured annotated field...
                if (config.getAnnotatedFields().size() > 1)
                    throw new InvalidInputFormatConfig("Plain text files can only have 1 annotated field");
                for (ConfigAnnotatedField annotatedField: config.getAnnotatedFields().values()) {
                    setCurrentAnnotatedFieldName(annotatedField.getName());

                    // For each line
                    StringBuilder punct = new StringBuilder();
                    while (true) {
                        String line;
                        try {
                            line = reader.readLine();
                        } catch (IOException e) {
                            throw new ErrorIndexingFile(e);
                        }
                        if (line == null)
                            break;
                        if (isStoreDocuments()) {
                            fullText.append(line);
                        }

                        // For each word
                        Matcher m = REGEX_WORD.matcher(line);
                        int i = 0;
                        while (m.find()) {
                            beginWord();

                            // For each annotation
                            String word = m.group();
                            punct.append(line, i, m.start());
                            i = m.end();
                            for (ConfigAnnotation annotation: annotatedField.getAnnotationsFlattened()) {
                                String processedWord = annotation.getCompiledProcessSteps().performSingle(word, metadataFieldValues);
                                if (annotation.getValuePath().equals(".")) {
                                    annotationValueAppend(annotation.getName(), processedWord, 1);
                                } else {
                                    throw new InvalidInputFormatConfig("Plain text annotation must have valuePath '.'");
                                }
                            }
                            punctuation(punct.toString());
                            punct.setLength(0);
                            endWord();
                        }
                        if (line.length() > i) {
                            // Capture last bit of "punctuation" on this line and add it to first word on next line.
                            punct.append(line.substring(i));
                        }
                    }
                    punctuation(
                            punct.toString()); // Put the last bit of punctuation (on the "extra closing token" at the end)
                    punct.setLength(0);
                }

                endDocument();
                return getStats();
            }

            @Override
            public void storeDocument() {
                storeWholeDocument(fullText.toString());
            }

            @Override
            protected int getCharacterPosition() {
                return fullText.length();
            }
        }
    }

}
