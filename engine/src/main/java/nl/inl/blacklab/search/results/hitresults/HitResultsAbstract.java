package nl.inl.blacklab.search.results.hitresults;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.Constants;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.ResultsAbstract;
import nl.inl.blacklab.search.results.SampleParameters;
import nl.inl.blacklab.search.results.WindowStats;
import nl.inl.blacklab.search.results.docs.DocResults;
import nl.inl.blacklab.search.results.hits.EphemeralHit;
import nl.inl.blacklab.search.results.hits.Hits;
import nl.inl.blacklab.search.results.hits.HitsMutable;
import nl.inl.blacklab.search.results.stats.ResultsStats;
import nl.inl.blacklab.search.results.stats.ResultsStatsSaved;

public abstract class HitResultsAbstract extends ResultsAbstract implements HitResults {

    protected static final Logger logger = LogManager.getLogger(HitResultsWithHitsInternal.class);

    public HitResultsAbstract(QueryInfo queryInfo) {
        super(queryInfo);
    }

    public static Hits sampleHits(Hits hitsList, SampleParameters sampleParameters) {
        // Fetch all hits and get most efficient implementation (nonlocking)
        hitsList = hitsList.getStatic();
        long totalNumberOfHits = hitsList.size();

        Set<Long> chosenHitIndices = new TreeSet<>(); // we need indexes sorted (see below)
        long numberOfHitsToSelect = sampleParameters.numberOfHits(totalNumberOfHits);
        if (numberOfHitsToSelect > Constants.JAVA_MAX_SET_SIZE) {
            throw new UnsupportedOperationException("Cannot sample more than " + Constants.JAVA_MAX_SET_SIZE +
                    " hits (tried to sample " + numberOfHitsToSelect + " from a total of " + totalNumberOfHits + ")");
        }
        if (numberOfHitsToSelect >= totalNumberOfHits) {
            numberOfHitsToSelect = totalNumberOfHits; // default to all hits in this case
            for (long i = 0; i < numberOfHitsToSelect; ++i) {
                chosenHitIndices.add(i);
            }
        } else {
            // Choose the hits
            Random random = new Random(sampleParameters.seed());
            for (int i = 0; i < numberOfHitsToSelect; i++) {
                // Choose a hit we haven't chosen yet
                long hitIndex;
                do {
                    hitIndex = random.nextInt((int) Math.min(Constants.JAVA_MAX_ARRAY_SIZE, totalNumberOfHits));
                } while (chosenHitIndices.contains(hitIndex));
                chosenHitIndices.add(hitIndex);
            }
        }

        // Add the chosen hits indexes to the sample.
        HitsMutable sample = HitsMutable.create(hitsList.field(), hitsList.matchInfoDefs(), numberOfHitsToSelect,
                numberOfHitsToSelect, false);
        EphemeralHit hit = new EphemeralHit();
        for (Long hitIndex: chosenHitIndices) {
            hitsList.getEphemeral(hitIndex, hit);
            sample.add(hit);
        }
        return sample;
    }

    /**
     * Get a window into this list of hits.
     * Use this if you're displaying part of the result set, like in a paging
     * interface. It makes sure BlackLab only works with the hits you want to
     * display and doesn't do any unnecessary processing on the other hits.
     * HitsWindow includes methods to assist with paging, like figuring out if there
     * hits before or after the window.
     *
     * @param first      first hit in the window (0-based)
     * @param windowSize size of the window
     * @return the window
     */
    @Override
    public HitResults window(long first, long windowSize) {
        Hits hits = getHits();
        Hits window = hits.sublist(first, windowSize);
        boolean hasNext = hits.sizeAtLeast(first + windowSize + 1);
        WindowStats windowStats = new WindowStats(hasNext, first, windowSize, window.size());
        ResultsStats hitsStats = new ResultsStatsSaved(window.size());
        ResultsStats docsStats = new ResultsStatsSaved(window.countDocs());
        return new HitResultsList(queryInfo(), window, windowStats, null,
                hitsStats, docsStats);
    }

    /**
     * Take a sample of hits by wrapping an existing Hits object.
     *
     * @param sampleParameters sample parameters
     * @return the sample
     */
    @Override
    public HitResults sample(SampleParameters sampleParameters) {

        Hits sample = HitResultsAbstract.sampleHits(getHits(), sampleParameters);

        // Count the number of documents in the sample
        MutableInt docsInSample = new MutableInt(0);
        int previousDoc = -1;
        for (EphemeralHit hit: sample) {
            if (hit.doc() != previousDoc) { // this works because indexes are sorted (TreeSet)
                docsInSample.add(1);
                previousDoc = hit.doc();
            }
        }

        ResultsStats hitsStats = new ResultsStatsSaved(sample.size());
        ResultsStats docsStats = new ResultsStatsSaved(docsInSample.getValue());
        return new HitResultsList(queryInfo(), sample, null, sampleParameters, hitsStats, docsStats);
    }

    /**
     * Return a new Hits object with these hits sorted by the given property.
     * This keeps the existing sort (or lack of one) intact and allows you to cache
     * different sorts of the same result set.
     *
     * @param sortProp the hit property to sort on
     * @return a new Hits object with the same hits, sorted in the specified way
     */
    @Override
    public HitResults sorted(HitProperty sortProp) {
        ensureResultsRead(-1);
        return new HitResultsList(queryInfo(), getHits().sorted(sortProp), null, null,
                resultsStats(), docsStats());
    }

    @Override
    public HitGroups group(HitProperty groupBy, long maxResultsToStorePerGroup) {
        ensureResultsRead(-1);

        if (groupBy == null)
            throw new IllegalArgumentException("Must have criteria to group on");
        Hits hits = getHits();

        Map<PropertyValue, Hits.Group> groupedHits = hits.grouped(groupBy, maxResultsToStorePerGroup);

        // (We make a copy of the stats so we don't keep any references to the source hits)
        List<HitGroup> hitGroups = HitGroups.fromBasicGroup(queryInfo(), groupedHits);
        return new HitGroups(queryInfo(), hitGroups, groupBy, null, null,
                resultsStats().save(), docsStats().save());
    }

    /**
     * Select only the hits where the specified property has the specified value.
     *
     * @param property property to select on, e.g. "word left of hit"
     * @param value    value to select on, e.g. 'the'
     * @return filtered hits
     */
    @Override
    public HitResults filter(HitProperty property, PropertyValue value) {
        return new HitResultsFiltered(this, property, value);
    }

    /**
     * Count occurrences of context words around hit.
     *
     * @param annotation  what annotation to get collocations for
     * @param contextSize how many words around the hits to use
     * @param sensitivity what sensitivity to use
     * @param sort        sort the resulting collocations by descending frequency?
     * @return the frequency of each occurring token
     */
    @Override
    public TermFrequencyList collocations(Annotation annotation, ContextSize contextSize, MatchSensitivity sensitivity,
            boolean sort) {
        // TODO: implement these using HitProperty objects that return HitPropertyValueContextWord(s);
        //       more versatile and we can eliminate Contexts?
        return Contexts.collocations(this, annotation, contextSize, sensitivity, sort);
    }

    /**
     * Return a per-document view of these hits.
     *
     * @param maxHits maximum number of hits to store per document
     * @return the per-document view.
     */
    @Override
    public DocResults perDocResults(long maxHits) {
        return DocResults.fromHits(queryInfo(), getHits(), maxHits);
    }

    @Override
    public WindowStats windowStats() {
        return null;
    }
}
