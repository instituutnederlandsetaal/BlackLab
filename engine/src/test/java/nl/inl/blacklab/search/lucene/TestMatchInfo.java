package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Random;

import org.apache.lucene.store.ByteArrayDataInput;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

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
                    relationId, false);

            // Encode the payload
            byte[] payload = payloadCodec.serialize(matchInfo).bytes;

            // Decode it again
            RelationInfo decoded = RelationInfo.create();
            payloadCodec.deserialize(sourceStart, new ByteArrayDataInput(payload), decoded);

            Assert.assertEquals(matchInfo, decoded);
        }
    }
}
