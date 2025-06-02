package nl.inl.blacklab.index;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.util.BytesRef;

/**
 * Class for a BlackLab document being indexed directly into Lucene.
 */
public class BLInputDocumentLucene implements BLInputDocument {

    private final Document document;

    public BLInputDocumentLucene() {
        document = new Document();
    }

    public void addField(String name, String value, BLFieldType fieldType) {
        document.add(new Field(name, value, fieldType.luceneType()));
    }

    public void addAnnotationField(String name, TokenStream tokenStream, BLFieldType fieldType) {
        document.add(new Field(name, tokenStream, fieldType.luceneType()));
    }

    public void addStoredNumericField(String name, int value, boolean addDocValue) {
        document.add(new IntPoint(name, value));
        document.add(new StoredField(name, value));
        if (addDocValue)
            document.add(new NumericDocValuesField(name, value));
    }

    public void addStoredField(String name, String value) {
        document.add(new StoredField(name, value));
    }

    public Document getDocument() {
        return document;
    }

    @Override
    public String get(String name) {
        return document.get(name);
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
    public BLIndexObjectFactory indexObjectFactory() {
        return BLIndexObjectFactoryLucene.INSTANCE;
    }
}
