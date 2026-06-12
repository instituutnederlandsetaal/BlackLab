package nl.inl.blacklab.indexers.config;

import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.io.FilenameUtils;

import nl.inl.blacklab.exceptions.ErrorIndexingFile;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.index.DocWriter;
import nl.inl.blacklab.index.IndexerStats;
import nl.inl.blacklab.index.InputFormat;
import nl.inl.blacklab.plugins.FileConverter;
import nl.inl.blacklab.plugins.param.PluginParams;
import nl.inl.util.fileprocessor.FileReference;

/**
 * Adds convert and tag plugin support to any input format.
 * <p>
 * Wraps a regular InputFormat to call FileConverter plugin(s) first,
 * then hands the result off to the wrapped InputFormat for indexing.
 */
public class InputFormatTypeWithConverters extends InputFormatTypeBase {

    public static InputFormat wrap(InputFormat inputFormat, FileConverter.ExtraConverters extraConverters) {
        if (extraConverters.isEmpty())
            return inputFormat;
        List<FileConverter.Parameterized> alreadyConfiguredConverters = List.of();
        if (inputFormat instanceof InputFormatConvertAndTag withConv) {
            // There were already converters. Unwrap and rewrap.
            alreadyConfiguredConverters = withConv.getConverters();
            inputFormat = withConv.getWrappedInputFormat();
        }
        // Concatenate first, existing and last converters.
        Stream<FileConverter.Parameterized> firstAndExisting = Stream.concat(
                extraConverters.applyFirst().stream(), alreadyConfiguredConverters.stream());
        List<FileConverter.Parameterized> allConverters = Stream.concat(
                firstAndExisting, extraConverters.applyLast().stream()).toList();
        return new InputFormatConvertAndTag(inputFormat, allConverters);
    }

    @Override
    public InputFormat createInputFormat(ConfigInputFormat config, PluginParams params) {
        throw new UnsupportedOperationException("Must be instantiated with an InputFormat to be wrapped");
    }

    public InputFormat createInputFormat(InputFormat wrapped, List<FileConverter.Parameterized> converters) {
        return new InputFormatConvertAndTag(wrapped, converters);
    }

    public static class InputFormatConvertAndTag extends InputFormatBase {

        private final InputFormat outputIndexer;

        private final List<FileConverter.Parameterized> converters;

        public List<FileConverter.Parameterized> getConverters() {
            return converters;
        }

        public InputFormatConvertAndTag(InputFormat actualIndexer, List<FileConverter.Parameterized> converters) {
            this.outputIndexer = actualIndexer;
            assert converters != null && !converters.isEmpty();
            this.converters = converters;
        }

        @Override
        protected Doc createDoc(DocWriter docWriter, FileReference file) {
            return new DocConvertAndTag(docWriter, file);
        }

        @Override
        public void indexSpecificDocument(DocWriter docWriter, FileReference file, String documentExpr, Doc linkingDoc,
                String storeWithName) {
            outputIndexer.indexSpecificDocument(docWriter, file, documentExpr, linkingDoc, storeWithName);
        }

        public InputFormat getWrappedInputFormat() {
            return outputIndexer;
        }

        protected class DocConvertAndTag extends DocBase {

            private FileReference file;

            public DocConvertAndTag(DocWriter docWriter, FileReference file) throws ErrorIndexingFile {
                super(docWriter, file);
            }

            @Override
            public void close() throws RuntimeException {
                // nothing to do here
            }

            @Override
            public void setDocument(FileReference file) {
                super.setDocument(file);
                this.file = file;
            }

            @Override
            public IndexerStats index() throws ErrorIndexingFile {
                // If the converter can't handle the file, an exception will be thrown.
                try {
                    FileReference result = file;
                    for (FileConverter.Parameterized converter: converters) {
                        // convertplugin always outputs in the input charset if provided, utf8 otherwise
                        String inputFormat = FilenameUtils.getExtension(this.documentName).toLowerCase();
                        result = converter.perform(result, inputFormat);
                    }
                    return outputIndexer.index(getDocWriter(), result);
                } catch (PluginException e) {
                    throw new ErrorIndexingFile(e);
                }
            }

            @Override
            protected int getCharacterPosition() {
                return 0;
            }

            @Override
            public void addMetadataField(String fieldName, String value) {
                throw new UnsupportedOperationException();
            }

            @Override
            public IndexerStats indexSpecificDocument(String documentExpr, Doc linkingDoc, String storeWithName) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void storeDocument() {
                throw new UnsupportedOperationException();
            }
        }
    }

}
