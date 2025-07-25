package nl.inl.blacklab.search.indexmetadata;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import org.apache.lucene.store.ByteArrayDataInput;
import org.apache.lucene.util.BytesRef;

import nl.inl.blacklab.exceptions.IndexVersionMismatch;
import nl.inl.blacklab.index.annotated.AnnotationWriter;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * Represents how we index and search spans ("inline tags") and relations.
 *
 * Strategy object to allow experimenting with how to make the new enhanced
 * relations search (as well as the existing spans features) faster.
 *
 * The strategy to use will be stored in a Lucene field attribute so the correct
 * class can be instantiated when opening an index.
 */
public interface RelationsStrategy {

    /** Return the current default relations strategy (used if no field attribute found).
     *
     * This would generally be an older strategy, used before we stored the attribute.
     */
    static RelationsStrategy ifNotRecorded() {
        throw new IndexVersionMismatch("Index is an older index with an incompatible way of indexing tags/relations. Please reindex.");
    }

    /** Return the current default relations strategy (used if no field attribute found).
     *
     * This would generally be the latest strategy.
     */
    static RelationsStrategy forNewIndex() {
        return RelationsStrategySeparateTerms.INSTANCE;
    }

    /** Instantiate strategy with given name */
    static RelationsStrategy fromName(String name) {
        return switch (name) {
            case RelationsStrategySeparateTerms.NAME -> RelationsStrategySeparateTerms.INSTANCE;
            default -> throw new IllegalArgumentException("Unsupported relation strategy: " + name);
        };
    }

    int getRelationId(AnnotationWriter writer, int endPos, Map<String, List<String>> attributes);

    /**
     * Get a query to match the given relation type and attributes.
     *
     * @param queryInfo query info
     * @param relationFieldName field name to search in
     * @param relationTypeRegex relation type to find
     * @param attributes attribute values to match
     * @return query to find the relations
     */
    BLSpanQuery getRelationsQuery(QueryInfo queryInfo, AnnotationSensitivity relationField, String relationTypeRegex, Map<String, String> attributes);

    BytesRef getPayload(RelationInfo relationInfo);

    /**
     * Information about encoding and decoding payloads for relations.
     */
    interface PayloadCodec {
        /**
         * Serialize relation info to payload
         */
        BytesRef serialize(RelationInfo relationInfo);

        /**
         * Get payload for an inline tag
         */
        BytesRef inlineTagPayload(int spanStart, int spanEnd, int relationId,
                boolean maybeExtraInfo);

        /**
         * Get payload for a relation
         */
        BytesRef relationPayload(boolean onlyHasTarget, int sourceStart, int sourceEnd, int targetStart, int targetEnd,
                int relationId, boolean maybeExtraInfo);

        /**
         * Read only the relationId directly from the payload
         */
        int readRelationId(ByteArrayDataInput dataInput);

        /**
         * Read relation info from payloaf
         */
        void deserialize(int startPosition, ByteArrayDataInput dataInput, RelationInfo target);

        default BytesRef relationIdOnlyPayload(int relationId) {
            throw new UnsupportedOperationException();
        }
    }

    PayloadCodec getPayloadCodec();

    /** Return strategy name (for field attribute) */
    String getName();

    // INDEXING

    /**
     * Should we write the extra relation info index?
     */
    default boolean writeRelationInfoToIndex() {
        return true;
    }

    /**
     * Index the given relation using the indexTermFunc provided.
     *
     * Function may be called once or multiple times depending on the indexing strategy.
     * This corresponds to a single term or multiple terms being indexed at the same position.
     *
     * (used in AnnotationWriter)
     */
    void indexRelationTerms(String fullType, Map<String, List<String>> attributes, BytesRef payload, BiConsumer<String, BytesRef> indexTermFunc);


    // SEARCH

    /**
     * Get regex(es) needed to find the given relation type with the given attributes.
     *
     * Depending on the strategy, this might be a single regex or multiple regexes.
     *
     * @param index the index to search in
     * @param relationTypeRegex relation type to find
     * @param attributes attribute values to match
     * @return regex(es) to find the relations
     */
    List<String> searchRegexes(BlackLabIndex index, String relationTypeRegex, Map<String, String> attributes);

    /** Optionally sanitize the tag name regex to avoid undesirable matches
     *  (i.e. matching a delimiter, not just the value).
     *  (used in SpanQueryRelations) */
    default String sanitizeTagNameRegex(String tagNameRegex) {
        // nothing to sanitize by default
        return tagNameRegex;
    }

    String fullTypeFromIndexedTerm(String term);

    // INDEXING / SEARCH

    /**
     * Decode any attribute+value pairs encoded in the given indexed term.
     *
     * (used in PWPluginRelationInfo, RelationsStats).
     *
     * @param indexedTerm indexed term
     * @return a stream of any attributes and values found in this term (might be empty)
     */
    Stream<Map.Entry<String, String>> attributesInTerm(String indexedTerm);

    /**
     * Returns the span's attributes, if they can be extracted from the indexed term.
     *
     * Note that an empty map indicates that there are no attributes, whereas null indicates
     * that we can't extract the attributes from the term (look in relation index instead).
     *
     * @param indexedTerm indexed term
     * @return a (possibly empty) map of the attributes if the term contains all of them; otherwise, null.
     */
    Map<String, String> getAllAttributesFromIndexedTerm(String indexedTerm);

    /**
     * Is the term an optimization term?
     *
     * (used to skip when storing forward index / calculating stats)
     *
     * @param term term to check
     * @return true if it is an optimization term
     */
    default boolean isOptimizationTerm(String term) {
        return false;
    }

    /** Should this term be used to determine the relations stats? (/relations) */
    default boolean countTermForStats(String term) {
        return !isOptimizationTerm(term);
    }
}
