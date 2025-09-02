package nl.inl.blacklab.search.indexmetadata;

import java.io.IOException;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.SortedSetDocValues;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.util.Bits;

import com.fasterxml.jackson.databind.JsonNode;

import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.search.BlackLabIndex;

/**
 * List of values with freqencies of a metadata field.
 *
 * This is the version that is determined from the Lucene index via DocValues.
 */
class MetadataFieldValuesFromIndex implements MetadataFieldValues {

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
        reader.leaves().parallelStream().forEach(lrc -> {
            try {
                TruncatableFreqList tfl = getDocValues(lrc.reader(), isNumeric);
                if (tfl != null) {
                    synchronized (values) {
                        values.addAll(tfl);
                    }
                }
            } catch (IOException e) {
                throw new InvalidIndex(e);
            }
        });
    }

    private TruncatableFreqList getDocValues(LeafReader reader, boolean isNumeric) throws IOException {
        TruncatableFreqList tfl = new TruncatableFreqList(this.values.getLimit());
        Bits liveDocs = reader.getLiveDocs();
        if (isNumeric) {
            // Numeric doc values
            NumericDocValues dv = reader.getNumericDocValues(fieldName);
            if (dv == null)
                return null;
            while (true) {
                int docId = dv.nextDoc();
                if (docId == DocIdSetIterator.NO_MORE_DOCS)
                    break;
                if (liveDocs == null || liveDocs.get(docId)) { // not deleted?
                    tfl.add(Long.toString(dv.longValue()));
                }
            }
        } else {
            SortedSetDocValues dv = reader.getSortedSetDocValues(fieldName);
            if (dv == null) {
                // Must be sorted doc values
                SortedDocValues sdv = reader.getSortedDocValues(fieldName);
                if (sdv == null)
                    return null;
                while (true) {
                    int docId = sdv.nextDoc();
                    if (docId == DocIdSetIterator.NO_MORE_DOCS)
                        break;
                    if (liveDocs == null || liveDocs.get(docId)) { // not deleted?
                        tfl.add(sdv.lookupOrd(sdv.ordValue()).utf8ToString());
                    }
                }
            } else {
                // Sorted set doc values
                while (true) {
                    int docId = dv.nextDoc();
                    if (docId == DocIdSetIterator.NO_MORE_DOCS)
                        break;
                    if (liveDocs == null || liveDocs.get(docId)) { // not deleted?
                        for (int i = 0; i < dv.docValueCount(); i++) {
                            tfl.add(dv.lookupOrd(dv.nextOrd()).utf8ToString());
                        }
                    }
                }
            }
        }
        return tfl;
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
