package nl.inl.blacklab.search.indexmetadata;

import java.io.IOException;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.util.Bits;

import com.fasterxml.jackson.databind.JsonNode;

import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.util.DocValuesUtil;

/**
 * List of values with freqencies of a metadata field.
 *
 * This is the version that is determined from the Lucene index via DocValues.
 */
class MetadataFieldValuesFromIndex implements MetadataFieldValues {

    //    private static final Logger logger = LogManager.getLogger(MetadataFieldValuesFromIndex.class);

    static class Factory implements MetadataFieldValues.Factory {

        private final BlackLabIndex index;

        Factory(BlackLabIndex index) {
            this.index = index;
        }

        @Override
        public MetadataFieldValues create(String fieldName, FieldType fieldType, long limitValues) {
            return new MetadataFieldValuesFromIndex(index.reader(), fieldName, fieldType == FieldType.NUMERIC,
                    limitValues);
        }
    }

    private final TruncatableFreqList values;

    private final boolean isNumeric;

    /**
     * Field name for use in warning message
     */
    private final String fieldName;

    public MetadataFieldValuesFromIndex(IndexReader reader, String fieldName, boolean isNumeric, long limitValues) {
        this.fieldName = fieldName;
        this.isNumeric = isNumeric;
        this.values = new TruncatableFreqList(limitValues);
        determineValueDistribution(reader);
    }

    public MetadataFieldValuesFromIndex(String fieldName, boolean isNumeric, TruncatableFreqList values) {
        this.fieldName = fieldName;
        this.isNumeric = isNumeric;
        this.values = values;
    }

    @Override
    public boolean canTruncateTo(long maxValues) {
        return values.canTruncateTo(maxValues);
    }

    @Override
    public boolean shouldAddValuesWhileIndexing() { return false; }

    @Override
    public MetadataFieldValues truncated(long maxValues) {
        TruncatableFreqList newValues = values.truncated(maxValues);
        if (newValues == values)
            return this;
        return new MetadataFieldValuesFromIndex(fieldName, isNumeric, newValues);
    }

    private void determineValueDistribution(IndexReader reader) {
        reader.leaves().stream().forEach(rc -> {
            try {
                getDocValues(rc.reader(), isNumeric, values);
            } catch (IOException e) {
                throw new InvalidIndex(e);
            }
        });
    }

    private void getDocValues(LeafReader r, boolean numeric, TruncatableFreqList values) throws IOException {
        Bits liveDocs = r.getLiveDocs();
        DocIdSetIterator dv = DocValuesUtil.docValuesIterator(r, fieldName, numeric);
        if (dv != null) { // If null, the documents in this segment do not contain any values for this field
            while (true) {
                int docId = dv.nextDoc();
                if (docId == DocIdSetIterator.NO_MORE_DOCS)
                    break;
                if (liveDocs == null || liveDocs.get(docId)) { // not deleted?
                    values.addAll(DocValuesUtil.getCurrentValues(dv));
                }
            }
        }
    }

    @Override
    public TruncatableFreqList valueList() {
        return values;
    }

    @Override
    public void setValues(JsonNode values) {
        throw new UnsupportedOperationException("Metadata field values are determined from index");
    }

    @Override
    public void setComplete(boolean complete) {
        throw new UnsupportedOperationException("Metadata field values are determined from index");
    }

    @Override
    public void addValue(String value) {
        throw new UnsupportedOperationException("Metadata field values are determined from index");
    }

    @Override
    public void removeValue(String value) {
        throw new UnsupportedOperationException("Metadata field values are determined from index");
    }

    @Override
    public void reset() {
        throw new UnsupportedOperationException("Metadata field values are determined from index");
    }
}
