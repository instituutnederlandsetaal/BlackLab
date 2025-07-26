package nl.inl.blacklab.search.results;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.MatchInfo;
import nl.inl.blacklab.search.lucene.MatchInfoDefs;

/**
 * A list of hits, optionally with captured groups.
 *
 * This interface is read-only.
 */
public interface Hits extends Results, HitsForHitProps, Iterable<Hit> {
    /**
     * Construct a Hits object from a SpanQuery.
     *
     * @param queryInfo      information about the original query
     * @param query          the query to execute to get the hits
     * @param searchSettings settings such as max. hits to process/count
     * @return hits found
     */
    static Hits fromSpanQuery(QueryInfo queryInfo, BLSpanQuery query, SearchSettings searchSettings) {
        return new HitsFromQuery(queryInfo, query, searchSettings);
    }

    /**
     * Make a wrapper Hits object for a list of Hit objects.
     * <p>
     * Will create Hit objects from the arrays. Mainly useful for testing.
     * Prefer using @link { {@link #list(QueryInfo, HitsInternal, MatchInfoDefs)} }
     *
     * @param queryInfo information about the original query
     * @param docs      doc ids
     * @param starts    hit starts
     * @param ends      hit ends
     * @return hits found
     */
    static Hits list(QueryInfo queryInfo, int[] docs, int[] starts, int[] ends) {

        IntList lDocs = new IntArrayList(docs);
        IntList lStarts = new IntArrayList(starts);
        IntList lEnds = new IntArrayList(ends);

        return new HitsList(queryInfo, new HitsInternalLock32(queryInfo.field(), null, lDocs, lStarts, lEnds, null), null);
    }

    static Hits list(QueryInfo queryInfo, HitsInternal hits, MatchInfoDefs matchInfoDefs) {
        return new HitsList(queryInfo, hits, matchInfoDefs);
    }

    /**
     * Return a Hits object with a single hit
     *
     * @param queryInfo   query info
     * @param luceneDocId Lucene document id
     * @param start       start of hit
     * @param end         end of hit
     * @return hits object
     */
    static Hits singleton(QueryInfo queryInfo, int luceneDocId, int start, int end) {
        return list(queryInfo, new int[]{luceneDocId}, new int[]{start}, new int[]{end});
    }

    /**
     * Construct an immutable empty Hits object.
     *
     * @param queryInfo query info
     * @return hits found
     */
    static Hits empty(QueryInfo queryInfo) {
        return new HitsList(queryInfo, HitsInternal.emptySingleton(queryInfo.field(), null), null);
    }

    static Map<String, MatchInfo> getMatchInfoMap(Hits hits, Hit hit, boolean omitEmptyCaptures) {
        MatchInfo[] matchInfo = hit.matchInfos();
        if (matchInfo == null)
            return Collections.emptyMap();
        MatchInfoDefs matchInfoDefs = hits.matchInfoDefs();
        Map<String, MatchInfo> map = new HashMap<>();
        for (int i = 0; i < matchInfo.length; i++) {
            if (omitEmptyCaptures && matchInfo[i].isSpanEmpty())
                continue;
            if (matchInfo[i] != null) {
                map.put(matchInfoDefs.get(i).getName(), matchInfo[i]);
            }
        }
        return map;
    }

    /**
     * If this is a hits window, return the window stats.
     *
     * @return window stats, or null if this is not a hits window
     */
    WindowStats windowStats();

    /**
     * Get a window into this list of hits.
     * <p>
     * Use this if you're displaying part of the result set, like in a paging
     * interface. It makes sure BlackLab only works with the hits you want to
     * display and doesn't do any unnecessary processing on the other hits.
     * <p>
     * HitsWindow includes methods to assist with paging, like figuring out if there
     * hits before or after the window.
     *
     * @param first      first hit in the window (0-based)
     * @param windowSize size of the window
     * @return the window
     */
    Hits window(long first, long windowSize);

    /**
     * Take a sample of hits by wrapping an existing Hits object.
     *
     * @param sampleParameters sample parameters
     * @return the sample
     */
    Hits sample(SampleParameters sampleParameters);

    /**
     * Return a new Hits object with these hits sorted by the given property.
     * <p>
     * This keeps the existing sort (or lack of one) intact and allows you to cache
     * different sorts of the same result set.
     *
     * @param sortProp the hit property to sort on
     * @return a new Hits object with the same hits, sorted in the specified way
     */
    Hits sort(HitProperty sortProp);

    HitGroups group(HitProperty criteria, long maxResultsToStorePerGroup);

    /**
     * Select only the hits where the specified property has the specified value.
     *
     * @param property property to select on, e.g. "word left of hit"
     * @param value    value to select on, e.g. 'the'
     * @return filtered hits
     */
    Hits filter(HitProperty property, PropertyValue value);

    @Override
    long numberOfResultObjects();

    /**
     * Iterate over Hit objects.
     *
     * This will return Hit objects that may be stored.
     * See {@link #ephemeralIterator()} for a faster version that
     * returns temporary Hit objects.
     *
     * @return iterator
     */
    @Override
    Iterator<Hit> iterator();

    /**
     * Iterate over Hit objects.
     *
     * This will return temporary Hit objects that must not be stored.
     * See {@link #iterator()} for a slower version that returns
     * Hit objects that may be stored.
     *
     * @return iterator
     */
    Iterator<EphemeralHit> ephemeralIterator();

    @Override
    Hit get(long i);

    /**
     * Copy hit information into a temporary object.
     *
     * @param i index of the desired hit
     * @param hit object to copy values to
     */
    void getEphemeral(long i, EphemeralHit hit);

    /**
     * Count occurrences of context words around hit.
     *
     * @param annotation  what annotation to get collocations for
     * @param contextSize how many words around the hits to use
     * @param sensitivity what sensitivity to use
     * @param sort        sort the resulting collocations by descending frequency?
     * @return the frequency of each occurring token
     */
    TermFrequencyList collocations(Annotation annotation, ContextSize contextSize, MatchSensitivity sensitivity, boolean sort);

    /**
     * Return a per-document view of these hits.
     *
     * @param maxHits maximum number of hits to store per document
     * @return the per-document view.
     */
    DocResults perDocResults(long maxHits);

    /**
     * Create concordances from the forward index.
     *
     * @param contextSize desired context size
     * @return concordances
     */
    Concordances concordances(ContextSize contextSize);

    Hits getHitsInDoc(int docId);

    ResultsStats docsStats();

    /**
     * Return a HitsWindow with a single hit.
     *
     * Assumes this hit is within our lists.
     *
     * @param hit hit for the window
     * @return hit window
     */
    Hits window(Hit hit);

    /**
     * Type of each of our match infos.
     *
     * @return list of match info definitions
     */
    MatchInfoDefs matchInfoDefs();

    boolean hasMatchInfo();

    Concordances concordances(ContextSize contextSize, ConcordanceType type);

    Kwics kwics(ContextSize contextSize);

    long size();

}
