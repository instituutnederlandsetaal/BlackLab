package nl.inl.blacklab.search.lucene;

import org.apache.lucene.search.spans.SpanCollector;
import org.apache.lucene.store.ByteArrayDataInput;

import nl.inl.blacklab.search.indexmetadata.RelationsStrategySeparateTerms;
import nl.inl.blacklab.search.lucene.SpansAndFiltered.SpansAndFilter;

/**
 * Checks that each clause in the AND operation matches the same relation.
 * <p>
 * Note that the clauses are assumed to be simple term clauses, and that we check the
 * relation by getting the relationId from the payload directly.
 * <p>
 * Contrast that with SpansAndUniqueRelations, which uses the relationInfo from the match info;
 * that class works at a "higher level", where a complex subquery was already resolved and the
 * relation info decoded, and we just want to ensure that our capture groups capture unique relations.
 * This is more low-level, to find the relation with certain names and attributes in the first place.
 */
public class SpansAndFilterFactorySameRelationId implements SpansAndFilterFactory {

    public static final SpansAndFilterFactory INSTANCE = new SpansAndFilterFactorySameRelationId();

    private SpansAndFilterFactorySameRelationId() {
        // Singleton
    }

    @Override
    public SpansAndFilter create(BLSpans spans, SpansInBuckets[] subSpans, int[] indexInBucket) {
        return new SpansAndFilter() {
            @Override
            public boolean accept() {
                // Note: getRelationInfo doesn't work here, these are simple regex spans.
                //   we need to read the relationId directly from the payload.
                byte[] payload = ((SpansInBucketsSameStartEnd) subSpans[0]).payload(indexInBucket[0]);
                int relationId = RelationsStrategySeparateTerms.CODEC.readRelationId(new ByteArrayDataInput(payload));
                for (int i = 1; i < subSpans.length; i++) {
                    int thisRelId = RelationsStrategySeparateTerms.CODEC.readRelationId(new ByteArrayDataInput(((SpansInBucketsSameStartEnd) subSpans[i]).payload(indexInBucket[i])));
                    if (thisRelId != relationId)
                        return false;
                }
                return true;
            }

            @Override
            public void collect(SpanCollector collector) {
                if (collector instanceof SpansRelations.SpansInBucketsPayloadAndTermCollector myCollector) {
                    // HACK ALERT! We're trying to find relations/tags and using the separate terms indexing/searching strategy.
                    // This class is being used to combine the tag term (clause 0) with the attribute terms (other clauses)
                    // We only need the payload from the tag term, which contains all the info, whereas the others only contain
                    // the relationId.
                    SpansInBucketsSameStartEnd spans = (SpansInBucketsSameStartEnd)subSpans[0];
                    myCollector.setPayload(spans.payload(indexInBucket[0]));
                    myCollector.setTerm(spans.term(indexInBucket[0]));
                }
            }
        };
    }

    @Override
    public SpansInBuckets bucketize(BLSpans blSpans) {
        // We need to get relationIds from the payload and store them for all hits in the bucket
        return new SpansInBucketsSameStartEnd(blSpans, true);
    }
}
