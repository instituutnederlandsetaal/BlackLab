package nl.inl.blacklab.search.results;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import nl.inl.blacklab.Constants;
import nl.inl.blacklab.resultproperty.ResultProperty;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;

/**
 * A list of results of some type.
 *
 * All subclasses should be thread-safe.
 *
 * @param <T> result type, e.g. Hit
 */
public abstract class ResultsAbstract<T, P extends ResultProperty<T>> implements Results<T, P> {

    /** Id the next Hits instance will get */
    private static final AtomicInteger nextHitsObjId = new AtomicInteger();

    // Perform simple generic sampling operation
    protected static <T, P extends ResultProperty<T>> List<T> doSample(ResultsList<T, P> source, SampleParameters sampleParameters) {
        // We can later provide an optimized version that uses a HitsSampleCopy or somesuch
        // (this class could save memory by only storing the hits we're interested in)

        if (source.size() > Constants.JAVA_MAX_ARRAY_SIZE) {
            // TODO: we might want to enable this, because the whole point of sampling is to make sense
            //       of huge result sets without having to look at every hit.
            //       Ideally, old seeds would keep working as well (although that may not be practical,
            //       and not likely to be a huge issue)
            throw new UnsupportedOperationException("Cannot sample from more than " + Constants.JAVA_MAX_ARRAY_SIZE + " hits");
        }

        List<T> results = new ArrayList<>();

        Random random = new Random(sampleParameters.seed());
        long numberOfHitsToSelect = sampleParameters.numberOfHits(source.size());
        if (numberOfHitsToSelect > source.size())
            numberOfHitsToSelect = source.size(); // default to all hits in this case
        // Choose the hits
        Set<Long> chosenHitIndices = new TreeSet<>();
        for (int i = 0; i < numberOfHitsToSelect; i++) {
            // Choose a hit we haven't chosen yet
            long hitIndex;
            do {
                hitIndex = random.nextInt((int)Math.min(Constants.JAVA_MAX_ARRAY_SIZE, source.size()));
            } while (chosenHitIndices.contains(hitIndex));
            chosenHitIndices.add(hitIndex);
        }

        // Add the hits in order of their index
        for (Long hitIndex : chosenHitIndices) {
            T hit = source.get(hitIndex);
            results.add(hit);
        }
        return results;
    }

    protected static <T, P extends ResultProperty<T>> List<T> doWindow(ResultsList<T, P> results, long first, long number) {
        if (first < 0 || first != 0 && !results.resultsProcessedAtLeast(first + 1)) {
            return Collections.emptyList();
        }

        // Auto-clamp number
        long actualSize = number;
        if (!results.resultsProcessedAtLeast(first + actualSize))
            actualSize = results.size() - first;

        // Make sublist (copy results from List.subList() to avoid lingering references large lists)
        return new ArrayList<>(results.resultsSubList(first, first + actualSize));
    }

    /** Unique id of this Hits instance (for debugging) */
    protected final int hitsObjId = nextHitsObjId.getAndIncrement();

    /** Information about the original query: index, field, max settings, max stats. */
    private final QueryInfo queryInfo;

    private final ResultsStats resultsStats = new ResultsStats() {
        @Override
        public boolean processedAtLeast(long lowerBound) {
            return resultsProcessedAtLeast(lowerBound);
        }

        @Override
        public long processedTotal() {
            return resultsProcessedTotal();
        }

        @Override
        public long processedSoFar() {
            return resultsProcessedSoFar();
        }

        @Override
        public long countedSoFar() {
            return resultsCountedSoFar();
        }

        @Override
        public long countedTotal() {
            return resultsCountedTotal();
        }

        @Override
        public boolean done() {
            return doneProcessingAndCounting();
        }

        @Override
        public MaxStats maxStats() {
            return (ResultsAbstract.this instanceof Hits ? ((Hits) ResultsAbstract.this).maxStats() : MaxStats.NOT_EXCEEDED);
        }

        @Override
        public String toString() {
            return "ResultsStats(" + ResultsAbstract.this + ")";
        }
    };

    protected ResultsAbstract(QueryInfo queryInfo) {
        this.queryInfo = queryInfo;
    }

    /**
     * Get information about the original query.
     *
     * This includes the index, field, max. settings, and max. stats
     * (whether the max. settings were reached).
     *
     * @return query info
     */
    @Override
    public QueryInfo queryInfo() {
        return queryInfo;
    }

    /**
     * Get the field these hits are from.
     *
     * @return field
     */
    @Override
    public AnnotatedField field() {
        return queryInfo().field();
    }

    /**
     * Get the index these hits are from.
     *
     * @return index
     */
    @Override
    public BlackLabIndex index() {
        return queryInfo().index();
    }

    @Override
    public int resultsObjId() {
        return hitsObjId;
    }

    /**
     * If this is a hits window, return the window stats.
     *
     * @return window stats, or null if this is not a hits window
     */
    @Override
    public WindowStats windowStats() {
        return null;
    }

    /**
     * Is this sampled from another instance?
     *
     * @return true if it's a sample, false if not
     */
    @Override
    public boolean isSample() {
        return sampleParameters() != null;
    }

    /**
     * If this is a sample, return the sample parameters.
     *
     * Also includes the explicitly set or randomly chosen seed.
     *
     * @return sample parameters, or null if this is not a sample
     */
    @Override
    public SampleParameters sampleParameters() {
        return null;
    }

    /**
     * Return a stream of these hits.
     *
     * @return stream
     */
    @Override
    public Stream<T> stream() {
        return StreamSupport.stream(this.spliterator(), false);
    }

    /**
     * Return a parallel stream of these hits.
     *
     * @return stream
     */
    @Override
    public Stream<T> parallelStream() {
        return StreamSupport.stream(this.spliterator(), true);
    }


    @Override
    public String toString() {
        return getClass().getSimpleName() + "(#" + hitsObjId + ")";
    }

    /**
     * Ensure that we have read at least as many results as specified in the parameter.
     *
     * @param number the minimum number of results that will have been read when this
     *            method returns (unless there are fewer hits than this); if
     *            negative, reads all hits
     */
    protected abstract void ensureResultsRead(long number);

    /**
     * Ensure that we have read all results.
     */
    protected void ensureAllResultsRead() {
        ensureResultsRead(-1);
    }

    @Override
    public ResultsStats resultsStats() {
        return resultsStats;
    }

    /**
     * Block until this many results have been processed.
     *
     * Returns false if there's not enough results to process.
     *
     * @param lowerBound number of results to wait for
     * @return true if this many results are now available, false if not
     */
    protected abstract boolean resultsProcessedAtLeast(long lowerBound);

    /**
     * This is an alias of resultsProcessedTotal().
     *
     * @return number of hits processed total
     */
    @Override
    public long size() {
        return resultsProcessedTotal();
    }

    protected abstract long resultsProcessedTotal();

    protected abstract long resultsProcessedSoFar();

    protected long resultsCountedSoFar() {
        return resultsProcessedSoFar();
    }

    protected long resultsCountedTotal() {
        return resultsProcessedTotal();
    }

}
