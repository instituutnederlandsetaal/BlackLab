package nl.inl.blacklab.search.indexmetadata;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import nl.inl.util.LimitUtil;

/**
 * Possibly truncated value frequency list.
 * Used for metadata, annotation and relation attribute values.
 */
public class TruncatableFreqList implements LimitUtil.Limitable<TruncatableFreqList> {

    private long limitValues = Integer.MAX_VALUE;

    private final Map<String, Long> values;

    private boolean truncated;

    public TruncatableFreqList(long limitValues) {
        this.limitValues = limitValues;
        values = new TreeMap<>();
        truncated = false;
    }

    public TruncatableFreqList(Map<String, Long> values, boolean truncated) {
        this.values = values;
        this.truncated = truncated;
    }

    public static TruncatableFreqList dummy() {
        return new TruncatableFreqList(0);
    }

    public TruncatableFreqList truncated(long maxValues) {
        if (values.size() == maxValues || !truncated && values.size() < maxValues) {
            // Current object is fine, either truncated to the right value or no need to truncate.
            return this;
        }
        if (truncated && values.size() < maxValues)
            throw new IllegalArgumentException(
                    "Cannot re-truncate value list of size " + values.size() + " to " + maxValues);
        return new TruncatableFreqList(LimitUtil.limit(values, maxValues), true);
    }

    public boolean canTruncateTo(long maxValues) {
        return !truncated || maxValues <= values.size();
    }

    public void add(String value, long count) {
        if (values.size() < limitValues || values.containsKey(value)) {
            // Count as normal
            values.compute(value, (__, prevCount) ->
                    prevCount == null ? count : prevCount + count);
        } else {
            // Reached the limit; stop storing now and indicate that there's more.
            truncated = true;
        }
    }

    public void add(String value) {
        add(value, 1);
    }

    public Map<String, Long> getValues() {
        return Collections.unmodifiableMap(values);
    }

    public boolean isTruncated() {
        return truncated;
    }

    public int size() {
        return values.size();
    }

    @Override
    public TruncatableFreqList withLimit(long max) {
        return truncated(max);
    }

    public long getLimit() {
        return limitValues;
    }

    public void addAll(TruncatableFreqList tfl) {
        tfl.values.forEach(this::add);
    }
}
