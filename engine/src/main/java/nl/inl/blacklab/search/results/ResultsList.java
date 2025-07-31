package nl.inl.blacklab.search.results;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import nl.inl.blacklab.Constants;

/** Base class for non-Hits Results (docs, groups, termfrequency). */
public abstract class ResultsList<T> extends ResultsAbstract {

    /** The results. */
    protected List<T> results;

    protected ResultsList(QueryInfo queryInfo) {
        super(queryInfo);
        results = new ArrayList<>();
    }

    // Perform simple generic sampling operation
    protected static <U> List<U> doSample(ResultsList<U> source, SampleParameters sampleParameters) {
        // We can later provide an optimized version that uses a HitsSampleCopy or somesuch
        // (this class could save memory by only storing the hits we're interested in)

        if (source.size() > Constants.JAVA_MAX_ARRAY_SIZE) {
            // TODO: we might want to enable this, because the whole point of sampling is to make sense
            //       of huge result sets without having to look at every hit.
            //       Ideally, old seeds would keep working as well (although that may not be practical,
            //       and not likely to be a huge issue)
            throw new UnsupportedOperationException("Cannot sample from more than " + Constants.JAVA_MAX_ARRAY_SIZE + " hits");
        }

        List<U> results = new ArrayList<>();

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
            U hit = source.get(hitIndex);
            results.add(hit);
        }
        return results;
    }

    protected static <U> List<U> doWindow(ResultsList<U> results, long first, long number) {
        if (first < 0 || first != 0 && !results.resultsStats().processedAtLeast(first + 1)) {
            return Collections.emptyList();
        }

        // Auto-clamp number
        long actualSize = number;
        if (!results.resultsStats().processedAtLeast(first + actualSize))
            actualSize = results.size() - first;

        // Make sublist (copy results from List.subList() to avoid lingering references large lists)
        return new ArrayList<>(results.resultsSubList(first, first + actualSize));
    }

    /**
     * Return an iterator over these hits.
     *
     * @return the iterator
     */
    public Iterator<T> iterator() {
        // Construct a custom iterator that iterates over the hits in the hits
        // list, but can also take into account the Spans object that may not have
        // been fully read. This ensures we don't instantiate Hit objects for all hits
        // if we just want to display the first few.
        return new Iterator<>() {

            int index = -1;

            @Override
            public boolean hasNext() {
                // Do we still have hits in the hits list?
                return ensureResultsRead((long)index + 2);
            }

            @Override
            public T next() {
                // Check if there is a next, taking unread hits from Spans into account
                if (hasNext()) {
                    index++;
                    return results.get(index);
                }
                throw new NoSuchElementException();
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }
    
    public synchronized T get(long i) {
        if (ensureResultsRead(i + 1)) {
            return results.get((int)i);
        }
        return null;
    }

    /**
     * Get part of the list of results.
     * 
     * Clients shouldn't use this. Used internally for certain performance-sensitive
     * operations like sorting.
     * 
     * The returned list is a view backed by the results list.
     * 
     * If toIndex is out of range, no exception is thrown, but a smaller list is returned.
     *
     * @param fromIndex the index of the first hit to return (inclusive)
     * @paream toIndex the index of the last hit to return (exclusive)
     * @return the list of hits
     */
    protected List<T> resultsSubList(long fromIndex, long toIndex) {
        if (!ensureResultsRead(toIndex)) {
            // Not enough hits; adjust toIndex
            toIndex = results.size();
        }
        return results.subList((int)fromIndex, (int)toIndex);
    }
}
