package nl.inl.blacklab.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.Constants;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.ResultProperty;
import nl.inl.blacklab.search.results.MaxStats;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.ResultsList;
import nl.inl.blacklab.search.results.ResultsStats;
import nl.inl.blacklab.search.results.ResultsStatsSaved;
import nl.inl.blacklab.search.results.SampleParameters;

/**
 * A collection of tokens and their (absolute) frequencies.
 *
 * This class calculates the total frequency of the entries added, but you can
 * also set the total frequency explicitly (after all entries have been added)
 * if you want to calculate relative frequencies based on a different total.
 */
public class TermFrequencyList extends ResultsList<TermFrequency> implements Iterable<TermFrequency> {

    long totalFrequency;

    private ResultsStats stats;

    public TermFrequencyList(QueryInfo queryInfo, Map<String, Integer> wordFreq, boolean sort) {
        super(queryInfo);
        if (wordFreq.size() >= Constants.JAVA_MAX_ARRAY_SIZE) {
            // (NOTE: List.size() will return Integer.MAX_VALUE if there's more than that number of items)
            throw new UnsupportedOperationException("Cannot handle more than " + Constants.JAVA_MAX_ARRAY_SIZE + " termfrequencies");
        }
        results = new ArrayList<>(wordFreq.size());
        for (Map.Entry<String, Integer> e : wordFreq.entrySet()) {
            results.add(new TermFrequency(e.getKey(), e.getValue()));
        }
        if (sort) {
            Comparator<TermFrequency> c = Comparator.naturalOrder();
            results.sort(c);
        }
        calculateTotalFrequency();
    }

    TermFrequencyList(QueryInfo queryInfo, List<TermFrequency> list) {
        super(queryInfo);
        if (list.size() >= Constants.JAVA_MAX_ARRAY_SIZE) {
            // (NOTE: List.size() will return Integer.MAX_VALUE if there's more than that number of items)
            throw new UnsupportedOperationException("Cannot handle more than " + Constants.JAVA_MAX_ARRAY_SIZE + " termfrequencies");
        }
        this.results = list;
        calculateTotalFrequency();
    }

    private void calculateTotalFrequency() {
        totalFrequency = 0;
        for (TermFrequency fr: results) {
            totalFrequency += fr.frequency;
        }
        stats = new ResultsStatsSaved(results.size(), results.size(), MaxStats.NOT_EXCEEDED);
    }

    @Override
    public ResultsStats resultsStats() {
        return stats;
    }

    @Override
    public Iterator<TermFrequency> iterator() {
        return results.iterator();
    }

    @Override
    public synchronized TermFrequency get(long index) {
        return results.get((int)index);
    }

    /**
     * Get the frequency of a specific token
     *
     * @param token the token to get the frequency for
     * @return the frequency
     */
    public long frequency(String token) {
        // OPT: maybe speed this up by keeping a map of tokens and frequencies?
        //       (or if sorted by freq, use binary search)
        for (TermFrequency tf : results) {
            if (tf.term.equals(token))
                return tf.frequency;
        }
        return 0;
    }

    public long totalFrequency() {
        return totalFrequency;
    }

    public TermFrequencyList subList(long fromIndex, long toIndex) {
        if (fromIndex < 0 || toIndex < 0 || fromIndex > results.size() || toIndex > results.size())
            throw new IllegalArgumentException("index out of range");
        return new TermFrequencyList(queryInfo(), results.subList((int)fromIndex, (int)toIndex));
    }

    @Override
    protected void ensureResultsRead(long number) {
        // NOP
    }

    public TermFrequencyList filter(ResultProperty property, PropertyValue value) {
        throw new UnsupportedOperationException();
    }

    public TermFrequencyList sort(ResultProperty sortProp) {
        throw new UnsupportedOperationException();
    }

    public TermFrequencyList window(long first, long windowSize) {
        throw new UnsupportedOperationException();
    }

    public TermFrequencyList sample(SampleParameters sampleParameters) {
        throw new UnsupportedOperationException();
    }

    @Override
    public long numberOfResultObjects() {
        return results.size();
    }

    @Override
    public String toString() {
        return "TermFrequencyList{" +
                "totalFrequency=" + totalFrequency +
                '}';
    }
}
