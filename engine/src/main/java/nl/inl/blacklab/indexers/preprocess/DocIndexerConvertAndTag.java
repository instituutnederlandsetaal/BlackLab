package nl.inl.blacklab.indexers.preprocess;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import org.apache.commons.io.FilenameUtils;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
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
    InputStream input;
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
    public void close() throws BlackLabRuntimeException {
        outputIndexer.close();
    }

    public void setDocument(InputStream is, Charset cs) {
        input = is;
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

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (config.getConvertPluginId() != null) {
            ConvertPlugin converter = PluginManager.getConverter(config.getConvertPluginId())
                    .orElseThrow(
                            () -> new RuntimeException("Unknown conversion plugin: " + config.getConvertPluginId()));

            BufferedInputStream converterInput = new BufferedInputStream(input);
            converterInput.mark(converter.getBufferSize());
            if (converter.canConvert(converterInput, charset, documentName)) {
                converterInput.reset();
                converter.perform(converterInput, charset, FilenameUtils.getExtension(this.documentName).toLowerCase(), output);
                this.documentName = converter.getOutputFileName(this.documentName);
                this.charset = converter.getOutputCharset();
                this.input = new ByteArrayInputStream(output.toByteArray());
                output.reset();
            }
        }

        if (config.getTagPluginId() != null) {
            TagPlugin tagger = PluginManager.getTagger(config.getTagPluginId())
                    .orElseThrow(() -> new RuntimeException("Unknown tagging plugin: " + config.getTagPluginId()));

            BufferedInputStream taggerInput = new BufferedInputStream(input);
            taggerInput.mark(tagger.getBufferSize());
            if (tagger.canConvert(taggerInput, charset, documentName)) {
                taggerInput.reset();
                tagger.perform(taggerInput, charset, documentName, output);
                this.documentName = tagger.getOutputFileName(documentName);
                this.charset = tagger.getOutputCharset();
                this.input = new ByteArrayInputStream(output.toByteArray());
                output.reset();
            }
        }

        this.outputIndexer.setDocumentName(this.documentName);
        this.outputIndexer.setConfigInputFormat(config);

        this.outputIndexer.setDocument(FileReference.fromInputStream(documentName, input, null));
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

    @Override
    public int numberOfDocsDone() {
        return outputIndexer.numberOfDocsDone();
    }

    @Override
    public long numberOfTokensDone() {
        return outputIndexer.numberOfTokensDone();
    }

    // do not override setDocumentName
}
