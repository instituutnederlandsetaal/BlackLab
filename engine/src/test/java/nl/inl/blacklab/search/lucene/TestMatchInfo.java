package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.lucene.store.ByteArrayDataInput;
import org.apache.lucene.util.BytesRef;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import nl.inl.blacklab.mocks.MockAnnotatedField;
import nl.inl.blacklab.search.indexmetadata.RelationsStrategy;

public class TestMatchInfo {

    /** Bound for our random numbers, chosen safely to avoid over/underflow */
    public static final int RND_BOUND = Integer.MAX_VALUE / 3;

    public static final int NUMBER_OF_TESTS = 10_000;

    private Random random;

    RelationsStrategy relationsStrategy = RelationsStrategy.forNewIndex();

    RelationsStrategy.PayloadCodec payloadCodec = relationsStrategy.getPayloadCodec();

    @Before
    public void setUp() {
        random = new Random(1928374);
    }

    @Test
    public void testMatchInfoSerialization() throws IOException {
        for (int i = 0; i < NUMBER_OF_TESTS; i++) {

            // Create a random MatchInfo structure
            boolean onlyHasTarget = random.nextBoolean();
            int sourceStart = random.nextInt(RND_BOUND);
            int sourceEnd = sourceStart + random.nextInt(RND_BOUND);
            int targetStart = random.nextInt(RND_BOUND);
            int targetEnd = targetStart + random.nextInt(RND_BOUND);
            int relationId = relationsStrategy.writeRelationInfoToIndex() ? random.nextInt(RND_BOUND) : -1;
            if (onlyHasTarget) {
                // We'll index the same values for source and target in this case,
                // even though source shouldn't be used.
                sourceStart = targetStart;
                sourceEnd = targetEnd;
            }
            RelationInfo matchInfo = RelationInfo.create(onlyHasTarget, sourceStart, sourceEnd, targetStart, targetEnd,
                    relationId, new MockAnnotatedField(), false);

            // Encode the payload
            byte[] payload = payloadCodec.serialize(matchInfo).bytes;

            // Decode it again
            RelationInfo decoded = RelationInfo.create(new MockAnnotatedField());
            payloadCodec.deserialize(sourceStart, new ByteArrayDataInput(payload), decoded);

            Assert.assertEquals(matchInfo, decoded);
        }
    }

    @Test
    public void testRelationPayloadVIntBoundaries() throws IOException {
        int[] relationIds = {
                0, 127, 128, 16_383, 16_384, 2_097_151, 2_097_152,
                268_435_455, 268_435_456, Integer.MAX_VALUE
        };
        for (int relationId: relationIds) {
            assertRelationRoundTrip(300_000_000, 300_000_001,
                    300_000_001, 300_000_002, relationId);
            ByteArrayDataInput input = new ByteArrayDataInput(
                    payloadCodec.relationIdOnlyPayload(relationId).bytes);
            Assert.assertEquals(relationId, payloadCodec.readRelationId(input));
        }

        int[] relativeTargetStarts = {
                -134_217_728, -1_048_576, -8_192, -64, -1, 0,
                1, 63, 64, 8_191, 8_192, 1_048_575, 1_048_576, 134_217_727
        };
        for (int relativeTargetStart: relativeTargetStarts) {
            int sourceStart = 300_000_000;
            int targetStart = sourceStart + relativeTargetStart;
            assertRelationRoundTrip(sourceStart, sourceStart + 16_384,
                    targetStart, targetStart + 128, 16_384);
        }
    }

    @Test
    public void testPreparedRelationTermsMatchRegularIndexing() {
        assertPreparedTermsMatch(Map.of());
        assertPreparedTermsMatch(Map.of(
                "id", List.of("one"),
                "class", List.of("a", "b")));
    }

    private void assertPreparedTermsMatch(Map<String, List<String>> attributes) {
        String fullType = "test::relation";
        BytesRef payload = payloadCodec.relationPayload(false, 10, 11, 20, 21, 42, true);
        List<Map.Entry<String, BytesRef>> regular = new ArrayList<>();
        List<Map.Entry<String, BytesRef>> prepared = new ArrayList<>();

        relationsStrategy.indexRelationTerms(fullType, attributes, payload,
                (term, termPayload) -> regular.add(
                        Map.entry(term, BytesRef.deepCopyOf(termPayload))));
        relationsStrategy.prepareRelationTerms(fullType, attributes).index(payload,
                (term, termPayload) -> prepared.add(
                        Map.entry(term, BytesRef.deepCopyOf(termPayload))));

        Assert.assertEquals(regular, prepared);
    }

    private void assertRelationRoundTrip(int sourceStart, int sourceEnd, int targetStart, int targetEnd,
            int relationId) throws IOException {
        RelationInfo relation = RelationInfo.create(false, sourceStart, sourceEnd, targetStart, targetEnd,
                relationId, new MockAnnotatedField(), true);
        var payload = payloadCodec.serialize(relation);
        RelationInfo decoded = RelationInfo.create(new MockAnnotatedField());
        payloadCodec.deserialize(sourceStart,
                new ByteArrayDataInput(payload.bytes, payload.offset, payload.length), decoded);
        Assert.assertEquals(relation, decoded);
    }

}
