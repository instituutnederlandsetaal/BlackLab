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
import nl.inl.util.DocValuesUtil;

/**
 * List of values with freqencies of a metadata field.
 *
 * This is the version that is determined from the Lucene index via DocValues.
 */
class MetadataFieldValuesFromIndex implements MetadataFieldValues {
    /**
     * Get the current value from a DocValues instance and cast to string.
     * NOTE: For multi-value fields, only returns the first value!
     *
     * @param dv     DocValues instance positioned at a valid document
     */
    public void getCurrentValues(DocIdSetIterator dv) {
        try {
            if (dv instanceof NumericDocValues)
                synchronized (values) {
                    values.add(Long.toString(((NumericDocValues) dv).longValue()));
                }
            else if (dv instanceof SortedSetDocValues ssdv) {
                synchronized (values) {
                    for (int i = 0; i < ssdv.docValueCount(); i++) {
                        long ord = ssdv.nextOrd();
                        values.add(ssdv.lookupOrd(ord).utf8ToString());
                    }
                }
            } else if (dv instanceof SortedDocValues sdv) {
                // OPT: avoid looking up the value and just use the ord directly if possible
                // (e.g. while determining frequencies in MetadataFieldVAluesFromIndex.determineValueDistribution())
                // See LUCENE-9796 in https://lucene.apache.org/core/9_0_0/MIGRATE.html
                synchronized (values) {
                    values.add(sdv.lookupOrd(sdv.ordValue()).utf8ToString());
                }
            } else {
                throw new IllegalStateException("Unexpected DocValues type");
            }
        } catch (IOException e) {
            throw new InvalidIndex(e);
        }
    }

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
        reader.leaves().parallelStream().forEach(lrc -> {
            try {
                getDocValues(lrc.reader(), isNumeric);
            } catch (IOException e) {
                throw new InvalidIndex(e);
            }
        });
    }

    private void getDocValues(LeafReader reader, boolean isNumeric) throws IOException {
        Bits liveDocs = reader.getLiveDocs();
        DocIdSetIterator dv = DocValuesUtil.docValuesIterator(reader, fieldName, isNumeric);
        if (dv != null) { // If null, the documents in this segment do not contain any values for this field
            while (true) {
                int docId = dv.nextDoc();
                if (docId == DocIdSetIterator.NO_MORE_DOCS)
                    break;
                if (liveDocs == null || liveDocs.get(docId)) { // not deleted?
                    getCurrentValues(dv);
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
