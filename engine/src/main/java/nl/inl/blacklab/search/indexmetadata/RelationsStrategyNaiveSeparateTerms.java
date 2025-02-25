package nl.inl.blacklab.search.indexmetadata;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.lucene.store.ByteArrayDataInput;
import org.apache.lucene.util.BytesRef;

import nl.inl.blacklab.index.annotated.AnnotationWriter;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * A span/relation strategy where the type (span name) and any attributes are all combined
 * into a single term, with special optimization terms to speed up certain queries.
 * In practice, this turned out to be too slow for large corpora.
 */
public class RelationsStrategyNaiveSeparateTerms implements RelationsStrategy {

    static final String NAME = "naive-separate";

    public static final RelationsStrategy INSTANCE = new RelationsStrategyNaiveSeparateTerms();

    private RelationsStrategyNaiveSeparateTerms() { }

    /**
     * Should we write the extra relation info index?
     */
    public boolean writeRelationInfoToIndex() {
        return false;
    }

    /**
     * What value do we index for attributes to tags (spans)?
     * <p>
     * (classic external index) A tag <s id="123"> ... </s> would be indexed in annotation "starttag"
     * with two tokens at the same position: "s" and "@id__123".
     *
     * @param name           attribute name
     * @param value          attribute value
     * @return value to index for this attribute
     */
    public static String tagAttributeIndexValue(String name, String value) {
        // NOTE: this means that we cannot distinguish between attributes for
        // different start tags occurring at the same token position!
        // (In the integrated index format, we include all attributes in the term)
        return "@" + name.toLowerCase() + "__" + value.toLowerCase();
    }

    @Override
    public BLSpanQuery getRelationsQuery(QueryInfo queryInfo, String relationFieldName, String relationTypeRegex,
            Map<String, String> attributes) {
        // Not supported for external index, but some tests may call this...
        return RelationsStrategySingleTerm.INSTANCE.getRelationsQuery(queryInfo, relationFieldName, relationTypeRegex, attributes);
    }

    @Override
    public List<String> searchRegexes(BlackLabIndex index, String relationTypeRegex, Map<String, String> attributes) {
        throw new UnsupportedOperationException("not supported for external index");
    }

    public static String indexedTermNoAttributes(String fullType) {
        // reinterpret as if it were a single-term indexed term, for stats
        // (@@@ FIXME probably)
        return RelationsStrategySingleTerm.indexTerm(fullType, null, false);
    }

    private static final Pattern PATT_ATTRIBUTE_TERM = Pattern.compile("@(.+)__(.*)");

    @Override
    public Stream<Map.Entry<String, String>> attributesInTerm(String indexedTerm) {
        Matcher m = PATT_ATTRIBUTE_TERM.matcher(indexedTerm);
        if (m.matches()) {
            return Stream.of(Map.entry(m.group(1), m.group(2)));
        }
        return Stream.empty();
    }

    @Override
    public Map<String, String> getAllAttributesFromIndexedTerm(String indexedTerm) {
        return null;
    }

    @Override
    public String fullTypeFromIndexedTerm(String term) {
        return RelationUtil.fullType(RelationUtil.CLASS_INLINE_TAG, term);
    }

    @Override
    public void indexRelationTermsMulti(String fullType, Map<String, Collection<String>> attributes, BytesRef payload, BiConsumer<String, BytesRef> indexTermFunc) {
        throw new UnsupportedOperationException("not supported for external index");
    }

    @Override
    public void indexRelationTerms(String fullType, Map<String, String> attributes, BytesRef payload, BiConsumer<String, BytesRef> indexTermFunc) {
        throw new UnsupportedOperationException("not supported for external index");
    }

    @Override
    public int getRelationId(AnnotationWriter writer, int endPos, Map<String, String> attributes) {
        return -1;
    }

    @Override
    public BytesRef getPayload(RelationInfo relationInfo) {
        throw new UnsupportedOperationException(); // external index handles this differently
    }

    @Override
    public String getName() {
        return NAME;
    }

    public static final PayloadCodec CODEC = new Codec();

    public PayloadCodec getPayloadCodec() { return CODEC; }

    static class Codec implements PayloadCodec {
        @Override
        public BytesRef serialize(RelationInfo relationInfo) {
            throw new UnsupportedOperationException();
        }

        /**
         * Get payload for an inline tag
         */
        public BytesRef inlineTagPayload(int spanStart, int spanEnd, BlackLabIndex.IndexType indexType, int relationId,
                boolean maybeExtraInfo) {
            assert indexType == BlackLabIndex.IndexType.EXTERNAL_FILES;
            return new BytesRef(ByteBuffer.allocate(4).putInt(spanEnd).array());
        }

        /**
         * Get payload for a relation
         */
        public BytesRef relationPayload(boolean onlyHasTarget, int sourceStart, int sourceEnd, int start, int end,
                int nextRelationId, boolean maybeExtraInfo) {
            throw new UnsupportedOperationException();
        }

        /**
         * Read the relationId directly from the payload
         */
        public int readRelationId(ByteArrayDataInput dataInput) {
            // this strategy doesn't use relationIds. Just return an invalid value.
            return -1;
        }

        @Override
        public void deserialize(int startPosition, ByteArrayDataInput dataInput, RelationInfo target) {
            throw new UnsupportedOperationException();
        }
    }
}
