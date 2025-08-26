package nl.inl.blacklab.index;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexableFieldType;

import com.ibm.icu.text.Collator;

import nl.inl.blacklab.config.BLConfigCollator;
import nl.inl.blacklab.forwardindex.Collators;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.indexmetadata.RelationsStrategy;

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
        return getFieldType(false, false, true, null);
    }

    public static synchronized BLFieldType annotationSensitivity(boolean offsets, boolean forwardIndex,
            RelationsStrategy relationsStrategy) {
        return getFieldType(offsets, forwardIndex, false, relationsStrategy);
    }

    /**
     * Get the appropriate FieldType given the options for an annotation sensitivity.
     *
     * @param offsets whether to store offsets
     * @param forwardIndex whether to store a forward index
     * @param contentStore whether to store a content store
     * @param strategy the relation strategy (if this is a relation field; null otherwise)
     */
    private static synchronized BLFieldType getFieldType(boolean offsets, boolean forwardIndex, boolean contentStore,
            RelationsStrategy strategy) {
        if (contentStore && (offsets || forwardIndex))
            throw new IllegalArgumentException("Field can either be content store or can have offsets/forward index, "
                    + "not both!");

        String key = (offsets ? "O" : "-") + (forwardIndex ? "F" : "-") + (contentStore ? "C" : "-") +
                "[" + (strategy != null ? strategy.getName() : "-") + "]";
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
                type.putAttribute(BLFA_CONTENT_STORE, "true");
            }
            if (forwardIndex) {
                // indicate that this field should get a forward index when written to the index
                // also set the collator definition for this field, so that we can use it when building the forward index
                type.putAttribute(BLFA_FORWARD_INDEX, "true");
                // Make configurable per field?
                type.putAttribute(BLFA_COLLATOR, getCollatorDef());
            }
            if (strategy != null) {
                // Record the relation strategy used for this _relations field
                type.putAttribute(BLFA_RELATION_STRATEGY, strategy.getName());
            }
            type.freeze();
            return new BLFieldTypeLucene(type);
        });
    }

    /** Return a :-separated string with collator parameters to put in field attribute (for FI) */
    private static String getCollatorDef() {
        BLConfigCollator collatorConfig = BlackLab.config().getSearch().getCollator();
        String language = collatorConfig.getLanguage();
        String country = collatorConfig.getCountry();
        String variant = collatorConfig.getVariant();
        return StringUtils.join(new String[]{language, country, variant}, ":");
    }

    private final IndexableFieldType type;

    public BLFieldTypeLucene(IndexableFieldType type) {
        this.type = type;
    }

    static Map<String, Collators> collators = new ConcurrentHashMap<>();

    /**
     * Set the collator to use for this field.
     *
     * Collator is used to determine term sort orders for the forward index.
     *
     * @param fieldInfo field type
     */
    public static Collators getFieldCollators(FieldInfo fieldInfo) {
        String collatorParams = fieldInfo.getAttribute(BLFA_COLLATOR);
        if (collatorParams == null || collatorParams.isEmpty())
            return Collators.getDefault();
        String[] parts = collatorParams.split(":");
        String language = parts[0];
        String country = parts.length > 1 ? parts[1] : "";
        String variant = parts.length > 2 ? parts[2] : "";

        String key = language + ":" + country + ":" + variant;
        return collators.computeIfAbsent(key, k ->
                new Collators(Collator.getInstance(new Locale(language, country, variant))));
        //return new Collators(Collator.getInstance(new Locale(language, country, variant)));
    }

    @Override
    public IndexableFieldType luceneType() {
        return type;
    }

    /** Lucene field attribute. Does the field have a forward index?
     If yes, payloads will indicate primary/secondary values. */
    private static final String BLFA_FORWARD_INDEX = "BL_hasForwardIndex";

    /** Collator to use (for determining term sort values for FI) */
    private static final String BLFA_COLLATOR = "BL_collator";

    /** Lucene field attribute. Does the field have a content store? */
    private static final String BLFA_CONTENT_STORE = "BL_hasContentStore";

    /** Lucene field attribute. How is this relation field encoded?
     *  ("naive-separate-terms" / "single-term" / ...)
     */
    private static final String BLFA_RELATION_STRATEGY = "BL_relationsStrategy";

    /**
     * Does the specified Lucene field have a forward index stored with it?
     *
     * If yes, we can deduce from the payload if a value is the primary value (e.g. original word,
     * to use for concordances, sort, group, etc.) or a secondary value (e.g. stemmed, synonym).
     * This is used because we only store the primary value in the forward index.
     *
     * We need to know this whenever we work with payloads too, so we can skip this indicator.
     * See {@link nl.inl.blacklab.analysis.PayloadUtils}.
     *
     * @param fieldInfo Lucene field to check
     * @return true if it has a forward index
     */
    public static boolean doesFieldHaveForwardIndex(FieldInfo fieldInfo) {
        String v = fieldInfo.getAttribute(BLFA_FORWARD_INDEX);
        return v != null && v.equals("true");
    }

    /**
     * Is the specified field a content store field?
     *
     * @param fieldInfo field to check
     * @return true if it's a content store field
     */
    public static boolean isContentStoreField(FieldInfo fieldInfo) {
        String v = fieldInfo.getAttribute(BLFA_CONTENT_STORE);
        return v != null && v.equals("true");
    }

    public static RelationsStrategy getRelationsStrategy(FieldInfo fieldInfo) {
        String strategyName = fieldInfo.getAttribute(BLFA_RELATION_STRATEGY);
        if (StringUtils.isEmpty(strategyName))
            return RelationsStrategy.ifNotRecorded();
        return RelationsStrategy.fromName(strategyName);
    }

}
