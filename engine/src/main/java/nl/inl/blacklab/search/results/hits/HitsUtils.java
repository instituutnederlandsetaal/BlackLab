package nl.inl.blacklab.search.results.hits;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropContext;
import nl.inl.blacklab.resultproperty.PropertyValue;

public class HitsUtils {

    private static final Logger logger = LogManager.getLogger(HitsUtils.class);

    /**
     * Below this number of hits, we'll sort/group in a single thread to save overhead.
     */
    private static int THRESHOLD_SINGLE_THREADED = 100;

    /**
     * Grouping hits is best done in 3 threads on average.
     * Depending on the exact search, 2 or 4 might be a bit better, but that's hard to predict.
     */
    static final int IDEAL_NUM_THREADS_GROUPING = 3;

    public static void setThresholdSingleThreadedGroupAndSort(int n) {
        THRESHOLD_SINGLE_THREADED = n;
    }

    public static Map<PropertyValue, Group> group(Hits hits, HitProperty groupBy, long maxValuesToStorePerGroup) {
        logger.debug("GROUP: fetch all hits");
        // Fetch all the hits first and get an efficient, nonlocking implementation
        hits = hits.getStatic();
        long n = hits.size();

        int numThreads = Math.min(
                Math.max(hits.index().blackLab().maxThreadsPerSearch(), 1),
                IDEAL_NUM_THREADS_GROUPING);

        Map<LeafReaderContext, Hits> hitsPerSegment = hits.hitsPerSegment();
        if (hitsPerSegment == null) {
            // We don't have per-segment hits, so we can't do this in parallel.
            return HitsAbstract.groupHits(hits, groupBy.copyWith(PropContext.globalHits(hits, new HashMap<>())), maxValuesToStorePerGroup, new HashMap<>(), null);
        }

        // If there are only a few hits, just group them in a single thread.
        if (numThreads == 1 || n < THRESHOLD_SINGLE_THREADED) {
            logger.debug("GROUP: single thread");

            Map<PropertyValue, Group> groups = new HashMap<>();
            for (Map.Entry<LeafReaderContext, Hits> entry: hitsPerSegment.entrySet()) {
                HitsAbstract.groupHits(entry.getValue(), groupBy.copyWith(PropContext.globalHits(entry.getValue(), new HashMap<>())), maxValuesToStorePerGroup, groups, entry.getKey());
            }
            logger.debug("GROUP: single thread finished");
            return groups;
        }

        logger.debug("GROUP: launch threads");

        // Group in parallel and merge the results.
        Parallel<Map.Entry<LeafReaderContext, Hits>, Map<PropertyValue, Group>> parallel = new Parallel<>(hits.index(), numThreads);
        HitProperty groupByWithCache = groupBy.copyWith(PropContext.globalHits(null, new ConcurrentHashMap<>()));
        return parallel.mapReduce(hits.hitsPerSegment().entrySet(),
                entry -> entry.getValue().size(),
                threadItems -> {
                    int threadNum = threadItems.hashCode() % 1000;
                    logger.debug("GROUP:    a thread started: " + threadNum);
                    // Group items in these segments into a single map.
                    Map<PropertyValue, Group> groups = new HashMap<>();
                    for (Map.Entry<LeafReaderContext, Hits> entry: threadItems) {
                        // For each segment, group the hits using the specified property.
                        logger.debug("GROUP:      thread " + threadNum + ", hits size " + entry.getValue().size());
                        HitsAbstract.groupHits(entry.getValue(), groupByWithCache, maxValuesToStorePerGroup, groups, entry.getKey());
                    }
                    logger.debug("GROUP:    a thread finished: " + threadNum);
                    return List.of(groups);
                },
                (acc, results) -> {
                    logger.debug("GROUP:    merging results from a thread");
                    for (Map.Entry<PropertyValue, Group> entry: results.entrySet()) {
                        PropertyValue groupId = entry.getKey();
                        Group segmentGroup = entry.getValue();
                        acc.compute(groupId, (PropertyValue k, Group v) ->
                                v == null ? segmentGroup : v.merge(segmentGroup, maxValuesToStorePerGroup));
                    }
                    logger.debug("GROUP:    merging results from a thread finished");
                },
                HashMap::new
        );
    }

    /** For grouping */
    public static class Group {

        HitsMutable storedHits;

        long totalNumberOfHits;

        public Group(HitsMutable storedHits, int totalNumberOfHits) {
            this.storedHits = storedHits;
            this.totalNumberOfHits = totalNumberOfHits;
        }

        public HitsMutable getStoredHits() {
            return storedHits;
        }

        public long getTotalNumberOfHits() {
            return totalNumberOfHits;
        }

        public Group merge(Group segmentGroup, long maxValuesToStorePerGroup) {
            if (maxValuesToStorePerGroup >= 0 && storedHits.size() + segmentGroup.storedHits.size() > maxValuesToStorePerGroup) {
                // Can we hold any more hits?
                if (storedHits.size() < maxValuesToStorePerGroup) {
                    // We can add a limited number of hits, so we need to trim the segment group
                    Hits hitsToAdd = segmentGroup.storedHits
                            .sublist(0, maxValuesToStorePerGroup - storedHits.size());
                    storedHits.addAll(hitsToAdd);
                }
            } else {
                // Just add all the hits
                storedHits.addAll(segmentGroup.getStoredHits());
            }
            totalNumberOfHits += segmentGroup.totalNumberOfHits;
            return this;
        }
    }
}
