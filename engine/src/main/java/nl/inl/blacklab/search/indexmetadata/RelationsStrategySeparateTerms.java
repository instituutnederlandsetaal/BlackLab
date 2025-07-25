package nl.inl.blacklab.search.indexmetadata;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.store.ByteArrayDataInput;
import org.apache.lucene.store.DataOutput;
import org.apache.lucene.store.OutputStreamDataOutput;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.automaton.RegExp;

import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.index.annotated.AnnotationWriter;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.lucene.BLSpanMultiTermQueryWrapper;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.lucene.SpanQueryAnd;
import nl.inl.blacklab.search.lucene.SpansAndFilterFactorySameRelationId;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * A span/relation strategy where the type (span name) and any attributes are indexed
 * in separate terms, all with the same relationId. When searching, we use a special
 * AND search that checks that all relationIds match.
 */
public class RelationsStrategySeparateTerms implements RelationsStrategy {

    static final String NAME = "separate-terms";

    public static final RelationsStrategy INSTANCE = new RelationsStrategySeparateTerms();

    private RelationsStrategySeparateTerms() { }

    /**
     * Separator between relation type name and attribute name in _relation annotation.
     */
    private static final String ATTR_SEPARATOR = "\u0001";

    /**
     * Separator between attribute name and its value in _relation annotation.
     */
    private static final String KEY_VALUE_SEPARATOR = "\u0002";

    /** Separator between multiple values for an attribute */
    public static final String ATTR_VALUE_SEPARATOR = "\u0003";

    /** Prefix for special term that's only added so we can write the relation index */
    public static final String RELATION_INFO_TERM_PREFIX = "\u0004";

    /**
     * Character class meaning "any non-special character" (replacement for .)
     */
    public static final String ANY_NON_SPECIAL_CHAR = "[^\u0001-\u0004]";

    /**
     * Determine the term to index in Lucene for a relation.
     *
     * @param fullRelationType full relation type
     * @param attributes       any attributes for this relation
     * @return term to index in Lucene
     */
    private static List<String> indexTerms(String fullRelationType, Map<String, List<String>> attributes) {
        // Add a special term not used for searching, only to write the relation index
        // It consists of the relation type and the attributes and their values, all unprocessed
        // (i.e. not lowercased, etc.)
        String attrPart = attributes == null ? "" : attributes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    String attrs = StringUtils.join(e.getValue(), ATTR_VALUE_SEPARATOR);
                    return e.getKey() + KEY_VALUE_SEPARATOR + attrs;
                })
                .collect(Collectors.joining(ATTR_SEPARATOR));
        String riTerm = RELATION_INFO_TERM_PREFIX + fullRelationType + ATTR_SEPARATOR + attrPart;

        if (attrPart.isEmpty())
            return List.of(fullRelationType, riTerm);

        List<String> terms = new ArrayList<>();
        terms.add(fullRelationType);
        terms.add(riTerm);
        attributes.entrySet().stream()
                .flatMap(e -> e.getValue().stream()
                        .map(v -> tagAttributeIndexTerm(fullRelationType, e.getKey(), v)))
                .forEach(terms::add);

        return terms;
    }

    @Override
    public boolean isOptimizationTerm(String term) {
        // only the special relation info term (with undesensitized attribute values) is an optimization term
        return this.countTermForStats(term);
    }

    @Override
    public boolean countTermForStats(String term) {
        // only count the special relation info term (with undesensitized attribute values)
        return term.startsWith(RELATION_INFO_TERM_PREFIX);
    }

    /**
     * Parse the special term for writing the relation info index.
     *
     * @return relation type and its attributes
     */
    public void parseRelationInfoTerm(String riTerm, BiConsumer<String, List<String>> attrHandler) {
        if (!isOptimizationTerm(riTerm))
            return;
        String[] parts = riTerm.split(ATTR_SEPARATOR, -1);
        if (parts.length == 2 && parts[1].isEmpty()) {
            // No attributes.
            return;
        }
        for (int i = 1; i < parts.length; i++) {
            String attr = parts[i];
            int p = attr.indexOf(KEY_VALUE_SEPARATOR);
            if (p < 0)
                throw new InvalidIndex("Malformed attribute in relation info term: " + riTerm);
            String key = attr.substring(0, p);
            String[] values = attr.substring(p + 1).split(ATTR_SEPARATOR, -1);
            attrHandler.accept(key, Arrays.asList(values));
        }
    }

    @Override
    public Stream<Map.Entry<String, String>> attributesInTerm(String indexedTerm) {
        if (indexedTerm.startsWith(RELATION_INFO_TERM_PREFIX)) {
            // Relation info term contains all attributes, undesensitized
            String[] parts = indexedTerm.split(ATTR_SEPARATOR, -1);
            List<Map.Entry<String, String>> entries = new ArrayList<>();
            if (parts.length == 2 && parts[1].isEmpty()) {
                // No attributes.
                return Stream.empty();
            }
            for (int j = 1; j < parts.length; j++) {
                String attr = parts[j];
                int p = attr.indexOf(KEY_VALUE_SEPARATOR);
                if (p < 0)
                    throw new InvalidIndex("Malformed attribute in relation info term: " + indexedTerm);
                String name = attr.substring(0, p);
                String[] values = attr.substring(p + 1).split(ATTR_VALUE_SEPARATOR, -1);
                for (String value: values)
                    entries.add(Map.entry(name, value));
            }
            return entries.stream();
        } else {
            // Regular attribute term contains just 1 attribute
            int i = indexedTerm.indexOf(ATTR_SEPARATOR); // if <0, this is not an attribute term
            if (i < 0)
                return Stream.empty();
            int j = indexedTerm.indexOf(KEY_VALUE_SEPARATOR, i + 1);
            if (j < 0) {
                // This is not an attribute term.
                return Stream.empty();
            }
            String name = indexedTerm.substring(i + 1, j);
            String value = indexedTerm.substring(j + 1);
            return Stream.of(Map.entry(name, value));
        }
    }

    @Override
    public Map<String, String> getAllAttributesFromIndexedTerm(String indexedTerm) {
        // we can't get attributes from terms in this strategy
        // (we'll get them from the relation index instead)
        return null;
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
    private static String tagAttributeIndexTerm(String fullRelationType, String name, String value) {
        return fullRelationType + ATTR_SEPARATOR + name + KEY_VALUE_SEPARATOR + value;
    }

    /**
     * What regex do we need to match an attribute term?
     *
     * @param nameRegex      attribute name regex
     * @param valueRegex     attribute value regex
     * @return regex for this attribute
     */
    private static String tagAttributeRegex(String relTypeRegex, String nameRegex, String valueRegex) {
        if (StringUtils.isEmpty(relTypeRegex))
            relTypeRegex = ".+";
        if (StringUtils.isEmpty(nameRegex))
            nameRegex = ".+";
        return RelationUtil.optParRegex(relTypeRegex) + ATTR_SEPARATOR +
                RelationUtil.optParRegex(nameRegex) + KEY_VALUE_SEPARATOR +
                RelationUtil.optParRegex(valueRegex);
    }

    /**
     * Given the indexed term, return the full relation type.
     * <p>
     * This leaves out any attributes indexed with the relation.
     *
     * @param indexedTerm the term indexed in Lucene
     * @return the full relation type
     */
    @Override
    public String fullTypeFromIndexedTerm(String indexedTerm) {
        int sep = indexedTerm.indexOf(ATTR_SEPARATOR);
        if (sep < 0)
            return indexedTerm;
        return indexedTerm.substring(0, sep);
    }

    @Override
    public BLSpanQuery getRelationsQuery(QueryInfo queryInfo, AnnotationSensitivity relationField, String relationTypeRegex,
            Map<String, String> attributes) {
        // Construct the clause from the field, relation type and attributes
        List<String> regexes = searchRegexes(queryInfo.index(), relationTypeRegex, attributes);
        assert regexes.size() == (attributes == null ? 0 : attributes.size()) + 1;
        List<BLSpanQuery> queries = new ArrayList<>();
        for (String regex: regexes) {
            RegexpQuery regexpQuery = new RegexpQuery(new Term(relationField.luceneField(), regex), RegExp.COMPLEMENT);
            queries.add(new BLSpanMultiTermQueryWrapper<>(queryInfo, regexpQuery));
        }
        SpanQueryAnd q =  new SpanQueryAnd(queries);
        q.setFilter(SpansAndFilterFactorySameRelationId.INSTANCE);
        return q;
    }

    /**
     * Determine the search regexes for a relation.
     * <p>
     * NOTE: both fullRelationTypeRegex and attribute names/values are interpreted as regexes,
     * so any regex special characters you wish to find should be escaped!
     *
     * @param fullRelationTypeRegex full relation type
     * @param attributes            any attribute criteria for this relation (regexes)
     * @return regexes to find this relation (first is the type, rest are attributes)
     */
    @Override
    public List<String> searchRegexes(BlackLabIndex index, String fullRelationTypeRegex,
            Map<String, String> attributes) {
        String typeRegex = RelationUtil.optParRegex(fullRelationTypeRegex);
        if (attributes == null || attributes.isEmpty())
            return List.of(typeRegex);

        List<String> regexes = new ArrayList<>();
        regexes.add(typeRegex);

        // Sort and concatenate the attribute names and values
        attributes.entrySet().stream()
                .map(e -> tagAttributeRegex(typeRegex, e.getKey(), e.getValue()))
                .forEach(regexes::add); // zero or more chars between attribute matches

        // The regex consists of the type part followed by the (sorted) attributes part.
        return regexes;
    }

    @Override
    public String sanitizeTagNameRegex(String tagNameRegex) {
        // Replace non-escaped dots with "any non-special character", so we don't accidentally
        // match attributes as our tag name as well.
        // (we use a lookbehind that should match any (reasonable) odd number of backslashes before the dot;
        //  so "." is replaced; "\." is not; "\\." is; "\\\." is not; etc.)
        return tagNameRegex.replaceAll("(?<!(\\\\\\\\){0,10}\\\\)\\.", ANY_NON_SPECIAL_CHAR);
    }

    @Override
    public void indexRelationTerms(String fullType, Map<String, List<String>> attributes, BytesRef payload, BiConsumer<String, BytesRef> indexTermFunc) {
        List<String> terms = indexTerms(fullType, attributes);
        indexTermFunc.accept(terms.get(0), payload);

        // Extract only the relationId from the payload, and index it with the other terms
        if (terms.size() > 1) {
            ByteArrayDataInput dataInput = new ByteArrayDataInput(payload.bytes);
            int relationId = CODEC.readRelationId(dataInput);
            BytesRef relIdOnly = CODEC.relationIdOnlyPayload(relationId);
            for (int i = 1; i < terms.size(); i++)
                indexTermFunc.accept(terms.get(i), relIdOnly);
        }
    }

    @Override
    public int getRelationId(AnnotationWriter writer, int endPos, Map<String, List<String>> attributes) {
        // Always assign a relation id, because we need it to match tags to attributes,
        // even if there's no extra information stored in the relation index (which there should be
        // if there's attributes, but ok).
        return writer.getNextRelationId(true);
    }

    @Override
    public BytesRef getPayload(RelationInfo relationInfo) {
        // If not yet complete, we will create the payload later when we know the end position
        return relationInfo.hasTarget() ?
                CODEC.serialize(relationInfo) :
                CODEC.relationIdOnlyPayload(relationInfo.getRelationId());
    }

    @Override
    public String getName() {
        return NAME;
    }

    public static final PayloadCodec CODEC = new Codec();

    @Override
    public PayloadCodec getPayloadCodec() { return CODEC; }

    static class Codec implements PayloadCodec {

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

        /** If set, this relation has no extra info in the relation index,
         *  so no need to look. */
        public static final byte FLAG_NO_EXTRA_INFO = 0x08;

        /**
         * Default value for the flags byte.
         * This means the relation has both a source and target, and has an id.
         * Older indexes had 0 as their default flags value, meaning they don't have unique relations ids.
         * This is the most common case (e.g. always true for inline tags), so we use it as the default.
         */
        private static final byte DEFAULT_FLAGS = 0;

        public void serializeInlineTag(int start, int end, int relationId, boolean maybeExtraInfo, DataOutput dataOutput) throws IOException {
            serializeRelationWithRelationId(false, start, start, end, end, relationId, maybeExtraInfo, dataOutput);
        }

        public void serializeRelationWithRelationId(boolean onlyHasTarget, int sourceStart, int sourceEnd,
                int targetStart, int targetEnd, int relationId, boolean maybeExtraInfo, DataOutput dataOutput) {
            assert sourceStart >= 0 && sourceEnd >= 0 && targetStart >= 0 && targetEnd >= 0;
            // Determine values to write from our source and target, and the position we're being indexed at
            int sourceLength = sourceEnd - sourceStart;
            int relTargetStart = targetStart - sourceStart;
            int targetLength = targetEnd - targetStart;

            // Which default length should we use? (can save 1 byte per relation)
            boolean useAlternateDefaultLength = sourceLength == DEFAULT_LENGTH_ALT && targetLength == DEFAULT_LENGTH_ALT;
            int defaultLength = useAlternateDefaultLength ? DEFAULT_LENGTH_ALT : DEFAULT_LENGTH;

            byte flags = (byte) ((onlyHasTarget ? FLAG_ONLY_HAS_TARGET : 0)
                    | (useAlternateDefaultLength ? FLAG_DEFAULT_LENGTH_ALT : 0)
                    | (maybeExtraInfo ? 0 : FLAG_NO_EXTRA_INFO));

            // Only write as much as we need (omitting default values from the end)
            boolean writeOtherLength = targetLength != defaultLength;
            boolean writeSourceLength = writeOtherLength || sourceLength != defaultLength;
            boolean writeRelTargetStart = writeSourceLength || relTargetStart != DEFAULT_REL_OTHER_START;
            try {
                writeRelationId(relationId, dataOutput);
                dataOutput.writeByte(flags);      // default is 0 but flags is never 0 anymore... (relation id)
                if (writeRelTargetStart)
                    dataOutput.writeZInt(relTargetStart);
                if (writeSourceLength)
                    dataOutput.writeVInt(sourceLength);
                if (writeOtherLength)
                    dataOutput.writeVInt(targetLength);
            } catch (IOException e) {
                throw new InvalidIndex(e);
            }
        }

        /**
         * Read the relation id from the payload.
         *
         * Note that relation type terms ("span name" terms) have the full payload;
         * attribute terms only have relationId. In either case, the first VInt in
         * the payload is the relationId.
         *
         * @param dataInput payload
         * @return relation id
         */
        @Override
        public int readRelationId(ByteArrayDataInput dataInput) {
            return dataInput.readVInt();
        }

        private static void writeRelationId(int relationId, DataOutput dataOuput) throws IOException {
            dataOuput.writeVInt(relationId);
        }

        /**
         * Deserialize relation info from the payload.
         *
         * @param currentTokenPosition the position we're currently at
         * @param dataInput data to deserialize
         */
        @Override
        public void deserialize(int currentTokenPosition, ByteArrayDataInput dataInput,
                RelationInfo target) {
            try {
                // Read values from payload (or use defaults for missing values)
                int relTargetStart = DEFAULT_REL_OTHER_START;
                int relationId = readRelationId(dataInput); // should always be present
                byte flags = DEFAULT_FLAGS;
                if (!dataInput.eof())
                    flags = dataInput.readByte();
                int defaultLength = (flags & FLAG_DEFAULT_LENGTH_ALT) != 0 ? DEFAULT_LENGTH_ALT : DEFAULT_LENGTH;
                if (!dataInput.eof()) {
                    // relTargetStart comes after flags (because first number is now relationId)
                    relTargetStart = dataInput.readZInt();
                }
                int sourceLength = defaultLength, targetLength = defaultLength;
                if (!dataInput.eof())
                    sourceLength = dataInput.readVInt();
                if (!dataInput.eof())
                    targetLength = dataInput.readVInt();

                // Fill the relationinfo structure with the source and target start/end positions
                boolean onlyHasTarget = (flags & FLAG_ONLY_HAS_TARGET) != 0;
                int sourceStart = currentTokenPosition;
                int sourceEnd = currentTokenPosition + sourceLength;
                int targetStart = currentTokenPosition + relTargetStart;
                int targetEnd = targetStart + targetLength;
                boolean maybeExtraInfo = (flags & FLAG_NO_EXTRA_INFO) == 0;
                assert sourceStart >= 0 && sourceEnd >= 0 && targetStart >= 0 && targetEnd >= 0;
                target.fill(relationId, onlyHasTarget, sourceStart, sourceEnd, targetStart, targetEnd, maybeExtraInfo);
            } catch (IOException e) {
                throw new InvalidIndex(e);
            }
        }

        /**
         * Serialize to a BytesRef.
         *
         * @return the serialized data
         */
        @Override
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
         * @param relationId     unique id for this relation, to look up attributes later
         * @return payload to store
         */
        @Override
        public BytesRef inlineTagPayload(int startPosition, int endPosition, int relationId, boolean maybeExtraInfo) {
            try {
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                serializeInlineTag(startPosition, endPosition, relationId, maybeExtraInfo, new OutputStreamDataOutput(os));
                return new BytesRef(os.toByteArray());
            } catch (IOException e) {
                throw new InvalidIndex(e);
            }
        }

        @Override
        public BytesRef relationPayload(boolean onlyHasTarget, int sourceStart, int sourceEnd, int targetStart,
                int targetEnd, int relationId, boolean maybeExtraInfo) {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            serializeRelationWithRelationId(onlyHasTarget, sourceStart, sourceEnd, targetStart, targetEnd,
                    relationId, maybeExtraInfo, new OutputStreamDataOutput(os));
            return new BytesRef(os.toByteArray());
        }

        @Override
        public BytesRef relationIdOnlyPayload(int relationId) {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            try {
                OutputStreamDataOutput outputStreamDataOutput = new OutputStreamDataOutput(os);
                writeRelationId(relationId, outputStreamDataOutput);
            } catch (IOException e) {
                throw new InvalidIndex(e);
            }
            return new BytesRef(os.toByteArray());
        }
    }
}
