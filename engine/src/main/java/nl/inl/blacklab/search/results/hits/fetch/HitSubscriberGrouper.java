package nl.inl.blacklab.search.results.hits.fetch;

import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.index.LeafReaderContext;

import com.ibm.icu.text.CollationKey;

import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropContext;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.results.hitresults.HitGroups;
import nl.inl.blacklab.search.results.hits.EphemeralHit;
import nl.inl.blacklab.search.results.hits.Group;
import nl.inl.blacklab.search.results.hits.Hits;
import nl.inl.blacklab.search.results.hits.HitsMutable;
import nl.inl.blacklab.search.textpattern.CompleteQuery;

/**
 * Performs a grouping operation on a segment.
 * If per-segment publishers aren't available, this can also be used
 * to perform grouping on a global Hits object (just call hits.publisher()).
 */
public class HitSubscriberGrouper implements HitSubscriber {

    final Map<PropertyValue, Group> segmentGroups;
    private final Map<String, CollationKey> collationCache;
    private final HitProperty groupBy;
    private final long maxValuesToStorePerGroup;
    private final Map<PropertyValue, Group> groups;
    private final CompleteQuery originalQuery;

    public HitSubscriberGrouper(Map<String, CollationKey> collationCache, HitProperty groupBy,
            long maxValuesToStorePerGroup,
            Map<PropertyValue, Group> groups, CompleteQuery originalQuery) {
        this.collationCache = collationCache;
        this.groupBy = groupBy;
        this.maxValuesToStorePerGroup = maxValuesToStorePerGroup;
        this.groups = groups;
        segmentGroups = new HashMap<>();
        this.originalQuery = originalQuery;
    }

    /**
     * Add a batch of hits to a grouping
     */
    private static void groupHits(Hits hits, long startIndex, long endIndex, HitProperty groupBy,
            long maxResultsToStorePerGroup, Map<PropertyValue, Group> groups, LeafReaderContext lrc,
            Map<String, CollationKey> collationCache, CompleteQuery originalQuery) {
        // temporary copy used in grouping (don't keep reference to hits)
        // NOTE: we pass toGlobal = true because segment hits must be grouped by global identity (so we can merge them)
        groupBy = groupBy.copyWith(PropContext.segmentToGlobal(hits, lrc, collationCache));

        EphemeralHit hit = new EphemeralHit();
        int prevDoc = -1;
        for (long hitIndex = startIndex; hitIndex < endIndex; hitIndex++) {

            // Determine group indentity
            PropertyValue identity = groupBy.get(hitIndex);

            // Get hit and convert to global if necessary
            hits.getEphemeral(hitIndex, hit);
            if (lrc != null) {
                // This is a segment hit. Convert doc id to global.
                hit.convertDocIdToGlobal(lrc.docBase);
            }

            // Add to correct group
            Group group = groups.get(identity);
            if (group == null) {
                if (groups.size() >= HitGroups.MAX_NUMBER_OF_GROUPS)
                    throw new UnsupportedOperationException(
                            "Cannot handle more than " + HitGroups.MAX_NUMBER_OF_GROUPS + " groups");
                HitsMutable hitsInGroup = HitsMutable.create(
                        hits.context(), -1, hits.size(), false);
                // With the query, we can free memory and find hits again later
                CompleteQuery completeQuery = originalQuery == null ? null :
                        groupBy.refine(hits.index(), originalQuery, identity).orElse(null);
                group = new Group(hitsInGroup, 0, 0, completeQuery);
                groups.put(identity, group);
            }
            if (maxResultsToStorePerGroup < 0 || group.getStoredHits().size() < maxResultsToStorePerGroup) {
                group.getStoredHits().add(hit);
            }
            group.updateCounts(1, hit.doc() != prevDoc);
            prevDoc = hit.doc();
        }
    }

    @Override
    public void start(LeafReaderContext lrc, Hits results) {
        // nothing to do here
    }

    @Override
    public boolean needsMoreHits() {
        return true; // we need all hits
    }

    @Override
    public void hits(LeafReaderContext lrc, Hits batchHits, long batchStart, long batchEnd,
            int batchNumDocs,
            long batchOffsetInTotal) {
        groupHits(batchHits, batchStart, batchEnd, groupBy,
                maxValuesToStorePerGroup, segmentGroups, lrc, collationCache, originalQuery);
    }

    @Override
    public void counted(long hitsCounted, int docsCounted) {
        // ignore
    }

    @Override
    public void flush(LeafReaderContext lrc, long numPublished) {
        // nothing to do
    }

    @Override
    public void done(LeafReaderContext lrc) {
        // Merge to global groups
        for (Map.Entry<PropertyValue, Group> entry: segmentGroups.entrySet()) {
            PropertyValue groupId = entry.getKey();
            Group segmentGroup = entry.getValue();
            groups.compute(groupId, (PropertyValue k, Group v) ->
                    v == null ? segmentGroup : v.merge(segmentGroup, maxValuesToStorePerGroup));
        }
    }

    @Override
    public void error(LeafReaderContext lrc, Throwable exception) {
        // no need to handle here because this will be wrapped in LatchingHitSubscriber, which handles errors
    }
}
