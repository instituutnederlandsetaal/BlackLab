package nl.inl.blacklab.index;

import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexableFieldType;

import nl.inl.blacklab.search.BlackLabIndexIntegrated;

/** Represents Lucene field types. */
public class BLFieldTypeLucene implements BLFieldType {

    /** How to index metadata fields (tokenized) */
    public static BLFieldType METADATA_TOKENIZED;

    /** How to index metadata fields (untokenized) */
    public static BLFieldType METADATA_UNTOKENIZED;

    private static final Map<String, BLFieldType> fieldTypeCache = new HashMap<>();

    static {
        FieldType tokenized = new FieldType();
        tokenized.setStored(true);
        //tokenized.setIndexed(true);
        tokenized.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
        tokenized.setTokenized(true);
        tokenized.setOmitNorms(true); // <-- depending on setting?
        tokenized.setStoreTermVectors(true);
        tokenized.setStoreTermVectorPositions(true);
        tokenized.setStoreTermVectorOffsets(true);
        tokenized.freeze();
        METADATA_TOKENIZED = new BLFieldTypeLucene(tokenized);

        FieldType untokenized = new FieldType(tokenized);
        untokenized.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
        //untokenized.setTokenized(false);  // <-- this should be done with KeywordAnalyzer, otherwise untokenized fields aren't lowercased
        untokenized.setStoreTermVectors(false);
        untokenized.setStoreTermVectorPositions(false);
        untokenized.setStoreTermVectorOffsets(false);
        untokenized.freeze();
        METADATA_UNTOKENIZED = new BLFieldTypeLucene(untokenized);
    }

    public static BLFieldType metadata(boolean tokenized) {
        return tokenized ? BLFieldTypeLucene.METADATA_TOKENIZED : BLFieldTypeLucene.METADATA_UNTOKENIZED;
    }

    public static synchronized BLFieldType contentStore() {
        return getFieldType(false, false, true);
    }

    public static synchronized BLFieldType annotationSensitivity(boolean offsets, boolean forwardIndex) {
        return getFieldType(offsets, forwardIndex, false);
    }

    /**
     * Get the appropriate FieldType given the options for an annotation sensitivity.
     */
    private static synchronized BLFieldType getFieldType(boolean offsets, boolean forwardIndex, boolean contentStore) {
        if (contentStore && (offsets || forwardIndex))
            throw new IllegalArgumentException("Field can either be content store or can have offsets/forward index, "
                    + "not both!");

        String key = (offsets ? "O" : "-") + (forwardIndex ? "F" : "-") + (contentStore ? "C" : "-");
        return fieldTypeCache.computeIfAbsent(key, (__) -> {
            FieldType type = new FieldType();
            type.setStored(contentStore);
            type.setOmitNorms(true);
            boolean indexed = !contentStore;
            IndexOptions indexOptions = indexed ? (offsets ?
                    IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS :
                    IndexOptions.DOCS_AND_FREQS_AND_POSITIONS) : IndexOptions.NONE;
            type.setIndexOptions(indexOptions);
            type.setTokenized(indexed);
            type.setStoreTermVectors(indexed);
            type.setStoreTermVectorPositions(indexed);
            type.setStoreTermVectorOffsets(indexed && offsets);
            if (contentStore) {
                // indicate that this field should store value as a content store (for random access)
                // (we set the field attribute regardless of our index format, but that's okay, it doesn't hurt anything
                //  if not used)
                BlackLabIndexIntegrated.setContentStoreField(type);
            }
            if (forwardIndex) {
                // indicate that this field should get a forward index when written to the index
                BlackLabIndexIntegrated.setFieldHasForwardIndex(type);
            }
            type.freeze();
            return new BLFieldTypeLucene(type);
        });
    }

    private final IndexableFieldType type;

    public BLFieldTypeLucene(IndexableFieldType type) {
        this.type = type;
    }

    @Override
    public IndexableFieldType luceneType() {
        return type;
    }

}
