package nl.inl.blacklab.indexers.preprocess;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PushbackInputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.FilenameUtils;

import nl.inl.blacklab.exceptions.MalformedInputFile;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.index.BLInputDocument;
import nl.inl.blacklab.index.DocWriter;
import nl.inl.blacklab.index.PluginManager;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.indexers.config.DocIndexerConfig;
import nl.inl.util.FileReference;

/**
 * Wrapper class for a regular DocIndexer. It's activated when a format has the
 * "convertPlugin" or "tagPlugin" properties. This DocIndexer will first run the
 * to-be-indexed file through the convert and tagging plugins before handing
 * result off to the actual DocIndexer.
 * <p>
 * It shares the ConfigInputFormat object with the actual DocIndexer, and should
 * be considered an internal implementation detail of the DocIndexer system.
 */
public class DocIndexerConvertAndTag extends DocIndexerConfig {

    PushbackInputStream input;
    /**
     * Charset of the data for our converter and tagger input/output, might be null
     * if our converter/tagger do not use charsets (because they process binary data
     * for example).
     */
    Charset charset;

    private final DocIndexerConfig outputIndexer;

    public DocIndexerConvertAndTag(DocIndexerConfig actualIndexer, ConfigInputFormat config) {
        this.outputIndexer = actualIndexer;
        this.config = config;
    }

    @Override
    public void close() throws RuntimeException {
        outputIndexer.close();
    }

    public void setDocument(InputStream is, Charset cs) {
        input = new PushbackInputStream(is, 251);
        charset = cs;
    }

    @Override
    public void setDocument(FileReference file) {
        super.setDocument(file);
        setDocument(file.getSinglePassInputStream(), file.getCharSet());
    }

    @Override
    public void index() throws PluginException, MalformedInputFile, IOException {
        if (this.input == null)
            throw new IllegalStateException("A document must be set before calling index()");

        // If the converter can't handle the file, we assume that the file is already in
        // the output format, and we attempt to index it directly.
        // This isn't entirely correct when the file is in a format neither the
        // converter nor the indexer can handle, but that is technically user error.
        //
        // ByteArrayOutputStream can conveniently be read and reused even after close()
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (config.getConvertPluginId() != null) {
            ConvertPlugin converter = PluginManager.getConverter(config.getConvertPluginId())
                    .orElseThrow(
                            () -> new RuntimeException("Unknown conversion plugin: " + config.getConvertPluginId()));

            // convertplugin always outputs in the input charset if provided, utf8 otherwise
            if (converter.canConvert(input, charset, FilenameUtils.getExtension(this.documentName).toLowerCase())) {
                converter.perform(input, charset, FilenameUtils.getExtension(this.documentName).toLowerCase(), output);
                input = new PushbackInputStream(new ByteArrayInputStream(output.toByteArray()), 1);
                output.reset();
            }
        }

        if (config.getTagPluginId() != null) {
            TagPlugin tagger = PluginManager.getTagger(config.getTagPluginId())
                    .orElseThrow(() -> new RuntimeException("Unknown tagging plugin: " + config.getTagPluginId()));

            // read in the original charset (if provided)
            Reader taggerInput = new InputStreamReader(input, charset);

            // always output in utf8 for ease of mind
            charset = StandardCharsets.UTF_8;
            try (OutputStreamWriter w = new OutputStreamWriter(output, charset)) {
                tagger.perform(taggerInput, w);
            }

            this.documentName = tagger.getOutputFileName(this.documentName);
        }

        this.outputIndexer.setDocumentName(this.documentName);
        this.outputIndexer.setConfigInputFormat(config);

        this.outputIndexer.setDocument(FileReference.fromBytesOverrideCharset(documentName, output.toByteArray(), charset));
        this.outputIndexer.index();
    }

    @Override
    protected int getCharacterPosition() {
        return 0;
    }

    @Override
    protected void storeDocument() {
        // FIXME shouldn't we call outputIndexer.storeDocument() here?
    }

    @Override
    public void setDocWriter(DocWriter indexer) {
        outputIndexer.setDocWriter(indexer);
    }

    @Override
    public void addMetadataField(String fieldName, String value) {
        outputIndexer.addMetadataField(fieldName, value);
    }

    @Override
    public BLInputDocument getCurrentDoc() {
        return outputIndexer.getCurrentDoc();
    }

    @Override
    public DocWriter getDocWriter() {
        return outputIndexer.getDocWriter();
    }

    @Override
    public void indexSpecificDocument(String documentExpr) {
        outputIndexer.indexSpecificDocument(documentExpr);
    }

    @Override
    public void setConfigInputFormat(ConfigInputFormat config) {
        outputIndexer.setConfigInputFormat(config);
    }

    // do not override setDocumentName
}
