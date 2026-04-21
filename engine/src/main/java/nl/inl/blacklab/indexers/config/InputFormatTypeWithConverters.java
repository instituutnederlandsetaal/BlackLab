package nl.inl.blacklab.indexers.config;

import java.util.List;
import java.util.Map;

import org.apache.commons.io.FilenameUtils;

import nl.inl.blacklab.exceptions.ErrorIndexingFile;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.index.DocWriter;
import nl.inl.blacklab.index.IndexerStats;
import nl.inl.blacklab.index.InputFormat;
import nl.inl.blacklab.plugins.FileConverter;
import nl.inl.blacklab.plugins.PluginManager;
import nl.inl.blacklab.plugins.PluginsOfType;
import nl.inl.util.fileprocessor.FileReference;

/**
 * Adds convert and tag plugin support to any input format.
 * <p>
 * Wraps a regular InputFormat to call FileConverter plugin(s) first,
 * then hands the result off to the wrapped InputFormat for indexing.
 */
public class InputFormatTypeWithConverters extends InputFormatTypeBase {

    @Override
    public InputFormat createInputFormat(Map<String, Object> configuration) {
        throw new UnsupportedOperationException("Must be instantiated with an InputFormat to be wrapped");
    }

    public InputFormat createInputFormat(InputFormat wrapped, List<String> fileConverters) {
        return new InputFormatConvertAndTag(wrapped, fileConverters);
    }

    public class InputFormatConvertAndTag extends InputFormatBase {

        private final InputFormat outputIndexer;

        private final List<String> converterIds;

        public InputFormatConvertAndTag(InputFormat actualIndexer, List<String> converterIds) {
            this.outputIndexer = actualIndexer;
            assert converterIds != null;
            this.converterIds = converterIds;
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
                    PluginsOfType<FileConverter> fileConverters = PluginManager.type(FileConverter.class);
                    for (String converterId: converterIds) {
                        // convertplugin always outputs in the input charset if provided, utf8 otherwise
                        String inputFormat = FilenameUtils.getExtension(this.documentName).toLowerCase();
                        result = fileConverters.get(converterId).perform(result, inputFormat);
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
