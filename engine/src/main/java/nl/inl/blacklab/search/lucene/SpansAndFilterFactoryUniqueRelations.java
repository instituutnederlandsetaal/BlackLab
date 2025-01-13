package nl.inl.blacklab.search.lucene;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import nl.inl.blacklab.search.lucene.SpansAndFiltered.SpansAndFilter;

/**
 * Checks that each clause in the AND operation matches a unique relation.
 *
 * Also ensures that we don't return the same combination of relations twice, i.e.
 * if we've already returned a hit with relations [R1, R2], we won't return the same
 * hit (in terms of start/end) with [R2, R1] again.
 */
public class SpansAndFilterFactoryUniqueRelations implements SpansAndFilterFactory {
    public static final SpansAndFilterFactory INSTANCE = new SpansAndFilterFactoryUniqueRelations();

    private SpansAndFilterFactoryUniqueRelations() {
        // Singleton
    }

    @Override
    public SpansAndFilter create(BLSpans spans, SpansInBuckets[] subSpans, int[] indexInBucket) {
        return new SpansAndFilter() {
            private final Set<List<RelationInfo>> relationsReturnedAtThisPosition = new HashSet<>();

            private List<RelationInfo> getRelationsSorted(HitQueryContext context, BLSpans spans) {
                MatchInfo[] matchInfo = new MatchInfo[context.numberOfMatchInfos()];
                spans.getMatchInfo(matchInfo);
                List<RelationInfo> ri = new ArrayList<>();
                for (MatchInfo mi: matchInfo) {
                    if (mi != null && mi.getType() == MatchInfo.Type.RELATION) {
                        ri.add((RelationInfo) mi);
                    }
                }
                ri.sort(RelationInfo::compareTo);
                return ri;
            }

            @Override
            public boolean accept() {
                // Double-check that no relation was matched multiple times,
                // i.e. all matched relations are unique
                Set<RelationInfo> relations = new HashSet<>();
                for (int i = 0; i < subSpans.length; i++) {
                    RelationInfo r = subSpans[i].getRelationInfo(indexInBucket[i]);
                    if (!relations.add(r)) {
                        // This relation was already found by another clause; this is not a valid match
                        return false;
                    }
                }

                // Also double check that we haven't found this same combination of relations before,
                // i.e. don't return two hits with relations [R1, R2] as well as [R2, R1]; those are the same
                List<RelationInfo> relStr = getRelationsSorted(context, spans);
                if (!relationsReturnedAtThisPosition.add(relStr)) {
                    // We've already seen this combination of relations, so no new match.
                    return false;
                }
                // Yes, it's a valid match!
                return true;
            }
        };
    }
}
