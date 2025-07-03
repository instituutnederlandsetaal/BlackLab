package nl.inl.blacklab.index;

import java.io.IOException;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Field;
import org.apache.solr.common.SolrInputDocument;
import org.ivdnt.blacklab.solr.BLSolrPreAnalyzedFieldParser;

/**
 * Class for a BlackLab document being indexed into Solr.
 * NOTE: BLFieldType parameters are ignored in this implementation!
 * In Lucene, the fieldtype is provided per field per document.
 * However in solr, fieldtype is provided through the index schema (managed schema).
 * This means that this mechanism of providing field types in the document itself doesn't exist.
 * See {@link org.ivdnt.blacklab.solr.BLSolrXMLLoader} to see how fieldtypes are set in solr instead.
 */
public class BLInputDocumentSolr implements BLInputDocument {

    private final SolrInputDocument document;
    
    public BLInputDocumentSolr() {
        document = new SolrInputDocument();
    }

    @Override
    public void addField(String name, String value, BLFieldType fieldType) {
        document.addField(name, value);
    }

    @Override
    public void addAnnotationField(String name, TokenStream tokenStream, BLFieldType fieldType) {
        try {
            // NOTE: BlackLab is providing a TokenStream here, but Solr expects all fields to have a string value (maybe numeric is allowed too, unsure).
            // Anyway, adding the tokenstream directly isn't supported (the actual error is that transactionlog cannot serialize it, but that is only one aspect of the problem.)
            // Instead, we serialize the tokenstream.
            // Also, the field is registered to have a parser {@link BLSolrPreAnalyzedFieldParser}
            // this parser will parse the string we set here back into the TokenStream once the document is further along in the
            // indexing process (within solr).
            // It seems we can't make solr automatically serialize the tokenstream into a string, so do that here instead.
            String v = new BLSolrPreAnalyzedFieldParser().toFormattedString(new Field(name, tokenStream, fieldType.luceneType()));
            document.addField(name, v);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void addStoredNumericField(String name, int value, boolean addDocValue) {
        document.addField(name, value);
    }

    @Override
    public void addStoredField(String name, String value) {
        document.addField(name, value);
    }

    public SolrInputDocument getDocument() {
        return document;
    }

    @Override
    public String get(String name) {
        return document.getField(name).getValue().toString();
    }

    @Override
    public void addTextualMetadataField(String name, String value, BLFieldType type) {
        // If a value is too long (more than 32K), just truncate it a bit.
        // This should be very rare and would generally only affect sorting/grouping, if anything.
        value = BLInputDocument.truncateValue(value);
        // docvalues for efficient sorting/grouping
        document.addField(name, value);
    }

    @Override
    public BLIndexObjectFactory indexObjectFactory() {
        return BLIndexObjectFactoryLucene.INSTANCE;
    }
}
