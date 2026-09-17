package nl.inl.blacklab.index;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.util.BytesRef;

/**
 * Class for a BlackLab document being indexed directly into Lucene.
 */
public class BLInputDocumentLucene implements BLInputDocument {

    private final Document document;

    /** The document type (document/fragment/indexmetadata)
     *
     * Needed because _doc_type is not a stored field, so we can't retrieve it directly from the document
     */
    private DocType docType;

    public BLInputDocumentLucene(DocType type) {
        document = new Document();
        String docTypeValue = type.getValue();
        addIndexedAndDocValues(DOC_TYPE_FIELD_NAME, docTypeValue);
        this.docType = type;
    }

    public Document getDocument() {
        return document;
    }

    public DocType getDocType() {
        return docType;
    }

    @Override
    public String get(String name) {
        return document.get(name);
    }

    @Override
    public void addField(String name, String value, BLFieldType fieldType) {
        document.add(new Field(name, value, fieldType.luceneType()));
    }

    @Override
    public void addIndexedAndDocValues(String name, String value) {
        document.add(new Field(name, value, BLFieldTypeLucene.STRING_UNTOKENIZED_UNSTORED.luceneType()));
        document.add(new SortedDocValuesField(name, new BytesRef(value.getBytes())));
    }

    @Override
    public void addTextualMetadataField(String name, String value, BLFieldType type) {
        addField(name, value, type);

        // If a value is too long (more than 32K), just truncate it a bit.
        // This should be very rare and would generally only affect sorting/grouping, if anything.
        value = BLInputDocument.truncateValue(value);
        // docvalues for efficient sorting/grouping
        document.add(new SortedSetDocValuesField(name, new BytesRef(value)));
    }

    @Override
    public void addAnnotationField(String name, TokenStream tokenStream, BLFieldType fieldType) {
        document.add(new Field(name, tokenStream, fieldType.luceneType()));
    }

    @Override
    public void addNumericField(String name, int value, boolean index, boolean store, boolean docValue) {
        if (index)
            document.add(new IntPoint(name, value));
        if (store)
            document.add(new StoredField(name, value));
        if (docValue)
            document.add(new NumericDocValuesField(name, value));
    }

    @Override
    public void addStoredField(String name, String value) {
        document.add(new StoredField(name, value));
    }

    @Override
    public BLIndexObjectFactory indexObjectFactory() {
        return BLIndexObjectFactoryLucene.INSTANCE;
    }
}
