package nl.inl.blacklab.search.indexmetadata;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.store.ByteArrayDataInput;
import org.apache.lucene.store.DataOutput;
import org.apache.lucene.store.OutputStreamDataOutput;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.automaton.RegExp;

import nl.inl.blacklab.index.annotated.AnnotationWriter;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.lucene.BLSpanMultiTermQueryWrapper;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * A span/relation strategy where the type (span name) and any attributes are all combined
 * into a single term, with special optimization terms to speed up certain queries.
 * In practice, this turned out to be too slow for large corpora.
 */
public class RelationsStrategySingleTerm implements RelationsStrategy {

    static final String NAME = "single-term";

    public static final RelationsStrategy INSTANCE = new RelationsStrategySingleTerm();

    private RelationsStrategySingleTerm() { }

    /**
     * Separator after relation type and attribute value in _relation annotation.
     */
    private static final String ATTR_SEPARATOR = "\u0001";

    /**
     * Separator between attr and value in _relation annotation.
     */
    private static final String KEY_VALUE_SEPARATOR = "\u0002";

    /**
     * Character before attribute name in _relation annotation.
     */
    private static final String CH_NAME_START = "\u0003";

    /**
     * An indexed term that ends with this character should not be counted, it is an extra search helper.
     * (used for relations, which are indexed with and without attributes so we can search faster if we don't
     * care about attributes)
     */
    static final String IS_OPTIMIZATION_INDICATOR = "\u0004";

    /**
     * Character class meaning "any non-special character" (replacement for .)
     */
    public static final String ANY_NON_SPECIAL_CHAR = "[^" + ATTR_SEPARATOR + KEY_VALUE_SEPARATOR + CH_NAME_START +
            IS_OPTIMIZATION_INDICATOR + "]";

    /**
     * Determine the term to index in Lucene for a relation.
     *
     * @param fullRelationType full relation type
     * @param attributes       any attributes for this relation
     * @param isOptimization   is this an extra index term to help speed up search in some cases? Such terms should
     *                         not be counted when determining stats. This will be indicated in the term encoding.
     * @return term to index in Lucene
     */
    public static String indexTerm(String fullRelationType, Map<String, String> attributes,
            boolean isOptimization) {
        String isOptSuffix = isOptimization ? IS_OPTIMIZATION_INDICATOR : "";

        String term;
        if (attributes == null || attributes.isEmpty()) {
            term = fullRelationType + ATTR_SEPARATOR + isOptSuffix;
        } else {
            // Sort and concatenate the attribute names and values
            String attrPart = attributes.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> tagAttributeIndexValue(e.getKey(), e.getValue(),
                            BlackLabIndex.IndexType.INTEGRATED))
                    .collect(Collectors.joining());

            // The term to index consists of the type followed by the (sorted) attributes.
            term = fullRelationType + ATTR_SEPARATOR + attrPart + isOptSuffix;
        }
        return term;
    }

    /**
     * Determine the term to index in Lucene for a relation.
     * <p>
     * This version can handle relations with multiple values for the same attribute,
     * which can happen as a result of processing steps during indexing.
     *
     * @param fullRelationType full relation type
     * @param attributes       any attributes for this relation
     * @param isOptimization   is this an extra index term to help speed up search in some cases? Such terms should
     *                         not be counted when determining stats. This will be indicated in the term encoding.
     * @return term to index in Lucene
     */
    public static String indexTermMulti(String fullRelationType, Map<String, Collection<String>> attributes,
            boolean isOptimization) {
        String isOptSuffix = isOptimization ? IS_OPTIMIZATION_INDICATOR : "";

        String term;
        if (attributes == null) {
            term = fullRelationType + ATTR_SEPARATOR + isOptSuffix;
        } else {
            // Sort and concatenate the attribute names and values
            String attrPart = attributes.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> e.getValue().stream()
                            .map(v -> tagAttributeIndexValue(e.getKey(), v,
                                    BlackLabIndex.IndexType.INTEGRATED))
                            .collect(Collectors.joining(" ")))
                    .collect(Collectors.joining());

            // The term to index consists of the type followed by the (sorted) attributes.
            term = fullRelationType + ATTR_SEPARATOR + attrPart + isOptSuffix;
        }
        return term;
    }

    public boolean isOptimizationTerm(String indexedTerm) {
        return indexedTerm.endsWith(IS_OPTIMIZATION_INDICATOR);
    }

    @Override
    public Stream<Map.Entry<String, String>> attributesInTerm(String indexedTerm) {
        Map<String, String> allAttributesFromIndexedTerm = getAllAttributesFromIndexedTerm(indexedTerm);
        if (allAttributesFromIndexedTerm == null)
            return Stream.empty();
        return allAttributesFromIndexedTerm.entrySet().stream();
    }

    public Map<String, String> getAllAttributesFromIndexedTerm(String indexedTerm) {
        int i = indexedTerm.indexOf(
                ATTR_SEPARATOR); // if <0, there's no attributes (older index where rel name isn't always terminated)
        boolean isFinalChar = i == indexedTerm.length() - 1; // if true, there's no attributes
        // if true, this is an optimization term and there's no attributes
        boolean isFinalCharBeforeOptIndicator =
                i == indexedTerm.length() - 2 && indexedTerm.charAt(i + 1) == IS_OPTIMIZATION_INDICATOR.charAt(0);
        if (isFinalCharBeforeOptIndicator)
            return null; // indicates attributes not available in this term; get from relation info index instead
        if (i < 0 || isFinalChar)
            return Collections.emptyMap();
        Map<String, String> attributes = new HashMap<>();
        for (String attrPart: indexedTerm.substring(i + 1).split(ATTR_SEPARATOR)) {
            String[] keyVal = attrPart.split(KEY_VALUE_SEPARATOR, 2);
            // older index doesn't have CH_NAME_START; strip it if it's there
            String key = keyVal[0].startsWith(CH_NAME_START) ? keyVal[0].substring(1) : keyVal[0];
            if (!attributes.containsKey(key)) // only the first value if there's multiple!
                attributes.put(key, keyVal[1]);
        }
        return attributes;
    }

    /**
     * What value do we index for attributes to tags (spans)?
     * <p>
     * (integrated index) A tag <s id="123"> ... </s> would be indexed in annotation "_relation"
     * with a single tokens: "__tag::s\u0001\u0003id\u0002123\u0001".
     *
     * @param name  attribute name
     * @param value attribute value
     * @return value to index for this attribute
     */
    private static String tagAttributeIndexValue(String name, String value, BlackLabIndex.IndexType indexType) {
        assert indexType != BlackLabIndex.IndexType.EXTERNAL_FILES;
        return CH_NAME_START + name + KEY_VALUE_SEPARATOR + value + ATTR_SEPARATOR;
    }

    /**
     * What regex do we need for attributes to tags (spans)?
     * <p>
     * (integrated index) A tag <s id="123"> ... </s> would be indexed in annotation "_relation"
     * with a single tokens: "__tag::s\u0001\u0003id\u0002123\u0001".
     *
     * @param useOldEncoding use the older encoding without CH_NAME_START?
     * @param name           attribute name
     * @param valueRegex     attribute value
     * @return regex for this attribute
     */
    private static String tagAttributeRegex(boolean useOldEncoding, String name, String valueRegex) {
        return (useOldEncoding ? "" : CH_NAME_START) + name + KEY_VALUE_SEPARATOR + RelationUtil.optParRegex(valueRegex)
                + ATTR_SEPARATOR;
    }

    /**
     * Given the indexed term, return the full relation type.
     * <p>
     * This leaves out any attributes indexed with the relation.
     *
     * @param indexedTerm the term indexed in Lucene
     * @return the full relation type
     */
    public String fullTypeFromIndexedTerm(String indexedTerm) {
        int sep = indexedTerm.indexOf(ATTR_SEPARATOR);
        if (sep < 0)
            return indexedTerm;
        return indexedTerm.substring(0, sep);
    }

    @Override
    public BLSpanQuery getRelationsQuery(QueryInfo queryInfo, String relationFieldName, String relationTypeRegex,
            Map<String, String> attributes) {
        // Construct the clause from the field, relation type and attributes
        List<String> regexes = searchRegexes(queryInfo.index(), relationTypeRegex, attributes);
        assert regexes.size() == 1;
        RegexpQuery regexpQuery = new RegexpQuery(new Term(relationFieldName, regexes.get(0)), RegExp.COMPLEMENT);
        return new BLSpanMultiTermQueryWrapper<>(queryInfo, regexpQuery);
    }

    /**
     * Determine the search regex for a relation.
     * <p>
     * NOTE: both fullRelationTypeRegex and attribute names/values are interpreted as regexes,
     * so any regex special characters you wish to find should be escaped!
     *
     * @param index                 index we're using (to check index flag IFL_INDEX_RELATIONS_TWICE)
     * @param fullRelationTypeRegex full relation type
     * @param attributes            any attribute criteria for this relation
     * @return regex to find this relation
     */
    public List<String> searchRegexes(BlackLabIndex index, String fullRelationTypeRegex,
            Map<String, String> attributes) {

        // Check if this is an older index that uses the attribute encoding without CH_NAME_START
        // (will be removed eventually)
        boolean useOldRelationsEncoding = index != null && index.metadata()
                .indexFlag(IndexMetadataIntegrated.IFL_INDEX_RELATIONS_TWICE).isEmpty();

        String typeRegex = RelationUtil.optParRegex(fullRelationTypeRegex);
        String result;
        if (attributes == null || attributes.isEmpty()) {
            // No attribute filters, so find the faster term that only has the relation type.
            // (for older encoding, just do a prefix query on the slower terms)
            if (useOldRelationsEncoding) {
                result = typeRegex + ATTR_SEPARATOR + ".*";
            } else {
                // Note: we make the optimization indicator optional so older indexes (created with
                // alpha version) don't break; remove this eventually.
                result = typeRegex + ATTR_SEPARATOR + "(" + IS_OPTIMIZATION_INDICATOR + ")?";
            }
        } else {
            // Sort and concatenate the attribute names and values
            String attrPart = attributes.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> tagAttributeRegex(useOldRelationsEncoding,
                            e.getKey(), e.getValue()))
                    .collect(Collectors.joining(".*")); // zero or more chars between attribute matches

            // The regex consists of the type part followed by the (sorted) attributes part.
            result = typeRegex + ATTR_SEPARATOR + ".*" + attrPart + ".*";
        }
        return List.of(result);
    }

    @Override
    public String sanitizeTagNameRegex(String tagNameRegex) {
        // Replace non-escaped dots with "any non-special character", so we don't accidentally
        // match attributes as our tag name as well.
        // (we use a lookbehind that should match any (reasonable) odd number of backslashes before the dot)
        return tagNameRegex.replaceAll("(?<!(\\\\\\\\){0,10}\\\\)\\.", ANY_NON_SPECIAL_CHAR);
    }

    @Override
    public void indexRelationTermsMulti(String fullType, Map<String, Collection<String>> attributes, BytesRef payload, BiConsumer<String, BytesRef> indexTermFunc) {

        // Determine the full value to index, e.g. full type and any attributes
        String valueToIndex = indexTermMulti(fullType, attributes, false);

        // Actually index the value, once without and once with attributes (if any)
        indexTermFunc.accept(valueToIndex, payload);
        if (attributes != null && !attributes.isEmpty()) {
            // Also index a version without attributes. We'll use this for faster search if we don't filter on
            // attributes.
            valueToIndex = indexTermMulti(fullType, null, true);
            indexTermFunc.accept(valueToIndex, payload);
        }
    }

    @Override
    public void indexRelationTerms(String fullType, Map<String, String> attributes, BytesRef payload, BiConsumer<String, BytesRef> indexTermFunc) {

        // Determine the full value to index, e.g. full type and any attributes
        String valueToIndex = indexTerm(fullType, attributes, false);

        // Actually index the value, once without and once with attributes (if any)
        indexTermFunc.accept(valueToIndex, payload);
        if (attributes != null && !attributes.isEmpty()) {
            // Also index a version without attributes. We'll use this for faster search if we don't filter on
            // attributes.
            valueToIndex = indexTermMulti(fullType, null, true);
            indexTermFunc.accept(valueToIndex, payload);
        }
    }

    @Override
    public int getRelationId(AnnotationWriter writer, int endPos, Map<String, String> attributes) {
        // Only assign a relation id if we know the end position; if not,
        // we'll assign the relation id and create the payload later when we do know the end position.
        return endPos >= 0 ?
                writer.getNextRelationId(attributes != null && !attributes.isEmpty()) :
                -1;
    }

    @Override
    public BytesRef getPayload(RelationInfo relationInfo) {
        // If not yet complete, we will create the payload later when we know the end position
        return relationInfo.hasTarget() ? getPayloadCodec().serialize(relationInfo) : null;
    }

    @Override
    public String getName() {
        return NAME;
    }

    private final PayloadCodec CODEC = new Codec();

    public PayloadCodec getPayloadCodec() { return CODEC; }

    class Codec implements PayloadCodec {
        /** Default value for first number in payload (either relOtherStart or relationId;
         *  see below) */
        private static final int DEFAULT_FIRST_PAYLOAD_NUMBER = 1;

        /** Default value for where the other end of this relation starts.
         *  We use 1 because it's pretty common for adjacent words to have a
         *  relation, and in this case we don't store the value. */
        private static final int DEFAULT_REL_OTHER_START = 1;

        /**
         * Default length for the source and target.
         * Inline tags are always stored with a 0-length source and target.
         * Dependency relations will usually store 1 (a single word), unless
         * the relation involves word group(s).
         */
        private static final int DEFAULT_LENGTH = 0;

        /**
         * Default length for the source and target if {@link #FLAG_DEFAULT_LENGTH_ALT} is set.
         * <p>
         * See there for details.
         */
        private static final int DEFAULT_LENGTH_ALT = 1;

        /** Is it a root relationship, that only has a target, no source? */
        public static final byte FLAG_ONLY_HAS_TARGET = 0x02;

        /** If set, use DEFAULT_LENGTH_ALT (1) as the default length
         * (dependency relations) instead of 0 (tags).
         * <p>
         * Doing it this way saves us a byte in the payload for dependency relations, as
         * we don't have to store two 1s, just one flags value.
         */
        public static final byte FLAG_DEFAULT_LENGTH_ALT = 0x04;

        /** Do relations have a unique id? If so, the payload structure is slightly different for efficiency. */
        public static final byte FLAG_RELATION_ID = 0x08;

        /**
         * Default value for the flags byte.
         * This means the relation has both a source and target, and has an id.
         * Older indexes had 0 as their default flags value, meaning they don't have unique relations ids.
         * This is the most common case (e.g. always true for inline tags), so we use it as the default.
         */
        private static final byte DEFAULT_FLAGS = 0;


        private void serializeRelation(boolean onlyHasTarget, int sourceStart, int sourceEnd,
                int targetStart, int targetEnd, DataOutput dataOutput) {

            assert sourceStart >= 0 && sourceEnd >= 0 && targetStart >= 0 && targetEnd >= 0;
            // Determine values to write from our source and target, and the position we're being indexed at
            int thisLength = sourceEnd - sourceStart;
            int relOtherStart = targetStart - sourceStart;
            int otherLength = targetEnd - targetStart;

            // Which default length should we use? (can save 1 byte per relation)
            boolean useAlternateDefaultLength = thisLength == DEFAULT_LENGTH_ALT && otherLength == DEFAULT_LENGTH_ALT;
            int defaultLength = useAlternateDefaultLength ? DEFAULT_LENGTH_ALT : DEFAULT_LENGTH;

            byte flags = (byte) ((onlyHasTarget ? FLAG_ONLY_HAS_TARGET : 0)
                    | (useAlternateDefaultLength ? FLAG_DEFAULT_LENGTH_ALT : 0));

            // Only write as much as we need (omitting default values from the end)
            boolean writeOtherLength = otherLength != defaultLength;
            boolean writeThisLength = writeOtherLength || thisLength != defaultLength;
            boolean writeFlags = writeThisLength || flags != DEFAULT_FLAGS;
            boolean writeRelOtherStart = writeFlags || relOtherStart != DEFAULT_REL_OTHER_START;
            try {
                if (writeRelOtherStart)
                    dataOutput.writeZInt(relOtherStart);
                if (writeFlags)
                    dataOutput.writeByte(flags);
                if (writeThisLength)
                    dataOutput.writeVInt(thisLength);
                if (writeOtherLength)
                    dataOutput.writeVInt(otherLength);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public void serializeInlineTag(int start, int end, int relationId, boolean maybeExtraInfo, DataOutput dataOutput) throws IOException {
            serializeRelationWithRelationId(false, start, start, end, end, relationId, maybeExtraInfo, dataOutput);
        }

        public void serializeRelationWithRelationId(boolean onlyHasTarget, int sourceStart, int sourceEnd,
                int targetStart, int targetEnd, int relationId, boolean maybeExtraInfo, DataOutput dataOutput) {

            if (!writeRelationInfoToIndex()) {
                serializeRelation(onlyHasTarget, sourceStart, sourceEnd, targetStart, targetEnd, dataOutput);
                return;
            }

            assert sourceStart >= 0 && sourceEnd >= 0 && targetStart >= 0 && targetEnd >= 0;
            // Determine values to write from our source and target, and the position we're being indexed at
            int thisLength = sourceEnd - sourceStart;
            int relOtherStart = targetStart - sourceStart;
            int otherLength = targetEnd - targetStart;

            // Which default length should we use? (can save 1 byte per relation)
            boolean useAlternateDefaultLength = thisLength == DEFAULT_LENGTH_ALT && otherLength == DEFAULT_LENGTH_ALT;
            int defaultLength = useAlternateDefaultLength ? DEFAULT_LENGTH_ALT : DEFAULT_LENGTH;

            byte flags = (byte) ((onlyHasTarget ? FLAG_ONLY_HAS_TARGET : 0)
                    | (useAlternateDefaultLength ? FLAG_DEFAULT_LENGTH_ALT : 0)
                    | FLAG_RELATION_ID);

            // Only write as much as we need (omitting default values from the end)
            boolean writeOtherLength = otherLength != defaultLength;
            boolean writeThisLength = writeOtherLength || thisLength != defaultLength;
            boolean writeRelOtherStart = writeThisLength || relOtherStart != DEFAULT_REL_OTHER_START;
            try {
                dataOutput.writeZInt(relationId);
                dataOutput.writeByte(flags);      // default is 0 but flags is never 0 anymore... (relation id)
                if (writeRelOtherStart)
                    dataOutput.writeZInt(relOtherStart);
                if (writeThisLength)
                    dataOutput.writeVInt(thisLength);
                if (writeOtherLength)
                    dataOutput.writeVInt(otherLength);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Read the relation id from the payload (if present).
         *
         * @param dataInput payload
         * @return relation id, or -1 if not present
         */
        public int readRelationId(ByteArrayDataInput dataInput) {
            try {
                int relationId = dataInput.readZInt();
                byte flags = dataInput.readByte();
                if ((flags & FLAG_RELATION_ID) != 0)
                    return relationId;
                return -1;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Deserialize relation info from the payload.
         *
         * @param currentTokenPosition the position we're currently at
         * @param dataInput data to deserialize
         * @throws IOException on corrupted payload
         */
        public void deserialize(int currentTokenPosition, ByteArrayDataInput dataInput,
                RelationInfo target) {
            try {
                // Read values from payload (or use defaults for missing values)
                int relOtherStart = DEFAULT_REL_OTHER_START, thisLength = DEFAULT_LENGTH, otherLength = DEFAULT_LENGTH;
                byte flags = DEFAULT_FLAGS;
                int firstNumber = DEFAULT_FIRST_PAYLOAD_NUMBER;
                // NOTE: older pre-release indexes didn't have relationId. In order to both remain compatible with these
                //       indexes, at least for a little while, but also keep payload storage efficient, we use a trick
                //       to decide which of two payload layouts we're dealing with. If the first number is <= 20000, it's
                //       a newer payload, the first number encodes the relationId, and relOtherStart follows after flags.
                //       This way we can read older and newer payloads without issues but still store them efficiently.
                if (!dataInput.eof())
                    firstNumber = dataInput.readZInt(); // either relationId or relOtherStart, depending on flags
                if (!dataInput.eof())
                    flags = dataInput.readByte();
                boolean alternatePayloadWithRelationId = (flags & FLAG_RELATION_ID) != 0;
                int relationId = 0;
                if (alternatePayloadWithRelationId) {
                    // relationId is the first number and relOtherStart follows after flags.
                    relationId = firstNumber;
                } else {
                    // Older pre-release index, without relationId and the first number being relOtherStart.
                    relOtherStart = firstNumber;
                }
                if ((flags & FLAG_DEFAULT_LENGTH_ALT) != 0) {
                    // Use alternate default length
                    thisLength = DEFAULT_LENGTH_ALT;
                    otherLength = DEFAULT_LENGTH_ALT;
                }
                if (!dataInput.eof() && alternatePayloadWithRelationId) {
                    // relOtherStart comes after flags (because first number is now relationId)
                    relOtherStart = dataInput.readZInt();
                }
                if (!dataInput.eof())
                    thisLength = dataInput.readVInt();
                if (!dataInput.eof())
                    otherLength = dataInput.readVInt();

                // Fill the relationinfo structure with the source and target start/end positions
                boolean onlyHasTarget = (flags & FLAG_ONLY_HAS_TARGET) != 0;
                int sourceStart = currentTokenPosition;
                int sourceEnd = currentTokenPosition + thisLength;
                int targetStart = currentTokenPosition + relOtherStart;
                int targetEnd = targetStart + otherLength;
                assert sourceStart >= 0 && sourceEnd >= 0 && targetStart >= 0 && targetEnd >= 0;
                target.fill(relationId, onlyHasTarget, sourceStart, sourceEnd, targetStart, targetEnd, relationId != RelationInfo.RELATION_ID_NO_INFO);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Serialize to a BytesRef.
         *
         * @return the serialized data
         */
        public BytesRef serialize(RelationInfo relInfo) {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            DataOutput dataOutput = new OutputStreamDataOutput(os);
            serializeRelationWithRelationId(relInfo.isRoot(), relInfo.getSourceStart(),
                    relInfo.getSourceEnd(), relInfo.getTargetStart(), relInfo.getTargetEnd(),
                    relInfo.getRelationId(), relInfo.mayHaveInfoInRelationIndex(), dataOutput);
            return new BytesRef(os.toByteArray());
        }

        /**
         * Get the payload to store with the span start tag.
         *
         * Spans are stored in the "_relation" annotation, at the token position of the start tag.
         * The payload gives the token position of the end tag.
         *
         * Note that in the integrated index, we store the relative position of the last token
         * inside the span, not the first token after the span. This is so it matches how relations
         * are stored.
         *
         * @param startPosition  start position (inclusive), or the first token of the span
         * @param endPosition    end position (exclusive), or the first token after the span
         * @param indexType      type of index we're writing
         * @param relationId     unique id for this relation, to look up attributes later
         * @return payload to store
         */
        public BytesRef inlineTagPayload(int startPosition, int endPosition, BlackLabIndex.IndexType indexType, int relationId, boolean maybeExtraInfo) {
            if (indexType == BlackLabIndex.IndexType.EXTERNAL_FILES)
                return new BytesRef(ByteBuffer.allocate(4).putInt(endPosition).array());

            try {
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                serializeInlineTag(startPosition, endPosition, relationId, maybeExtraInfo, new OutputStreamDataOutput(os));
                return new BytesRef(os.toByteArray());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public BytesRef relationPayload(boolean onlyHasTarget, int sourceStart, int sourceEnd, int targetStart,
                int targetEnd, int relationId, boolean maybeExtraInfo) {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            serializeRelationWithRelationId(onlyHasTarget, sourceStart, sourceEnd, targetStart, targetEnd,
                    relationId, maybeExtraInfo, new OutputStreamDataOutput(os));
            return new BytesRef(os.toByteArray());
        }
    }
}
