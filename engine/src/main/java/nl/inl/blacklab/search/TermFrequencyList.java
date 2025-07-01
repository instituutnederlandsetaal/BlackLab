package nl.inl.blacklab.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.Constants;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.ResultProperty;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.ResultGroups;
import nl.inl.blacklab.search.results.ResultsList;
import nl.inl.blacklab.search.results.SampleParameters;

/**
 * A collection of tokens and their (absolute) frequencies.
 *
 * This class calculates the total frequency of the entries added, but you can
 * also set the total frequency explicitly (after all entries have been added)
 * if you want to calculate relative frequencies based on a different total.
 */
public class TermFrequencyList extends ResultsList<TermFrequency, ResultProperty<TermFrequency>> {

    long totalFrequency;

    public TermFrequencyList(QueryInfo queryInfo, Map<String, Integer> wordFreq, boolean sort) {
        super(queryInfo);
        if (wordFreq.size() >= Constants.JAVA_MAX_ARRAY_SIZE) {
            // (NOTE: List.size() will return Integer.MAX_VALUE if there's more than that number of items)
            throw new BlackLabRuntimeException("Cannot handle more than " + Constants.JAVA_MAX_ARRAY_SIZE + " termfrequencies");
        }
        results = new ArrayList<>(wordFreq.size());
        for (Map.Entry<String, Integer> e : wordFreq.entrySet()) {
            results.add(new TermFrequency(e.getKey(), e.getValue()));
        }
        if (sort) {
            results.sort(Comparator.naturalOrder());
        }
        calculateTotalFrequency();
    }

    TermFrequencyList(QueryInfo queryInfo, List<TermFrequency> list) {
        super(queryInfo);
        if (list.size() >= Constants.JAVA_MAX_ARRAY_SIZE) {
            // (NOTE: List.size() will return Integer.MAX_VALUE if there's more than that number of items)
            throw new BlackLabRuntimeException("Cannot handle more than " + Constants.JAVA_MAX_ARRAY_SIZE + " termfrequencies");
        }
        this.results = list;
        calculateTotalFrequency();
    }

    private void calculateTotalFrequency() {
        totalFrequency = 0;
        for (TermFrequency fr: results) {
            totalFrequency += fr.frequency;
        }
    }

    @Override
    public long size() {
        return results.size();
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
            throw new BlackLabRuntimeException("index out of range");
        return new TermFrequencyList(queryInfo(), results.subList((int)fromIndex, (int)toIndex));
    }

    @Override
    protected void ensureResultsRead(long number) {
        // NOP
    }

    @Override
    public ResultGroups<TermFrequency> group(ResultProperty<TermFrequency> criteria,
                                             long maxResultsToStorePerGroup) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TermFrequencyList filter(ResultProperty<TermFrequency> property, PropertyValue value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TermFrequencyList sort(ResultProperty<TermFrequency> sortProp) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TermFrequencyList window(long first, long windowSize) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TermFrequencyList sample(SampleParameters sampleParameters) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean doneProcessingAndCounting() {
        return true;
    }

    @Override
    public long numberOfResultObjects() {
        return results.size();
    }

}
