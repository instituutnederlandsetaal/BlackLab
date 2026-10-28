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

    private long limitValues;

    private final TreeMap<String, Long> values;

    private boolean truncated;

    public TruncatableFreqList(long limitValues) {
        this.limitValues = limitValues;
        values = new TreeMap<>();
        truncated = false;
    }

    /**
     * Construct a TruncatableFreqList from a map of values.
     *
     * NOTE: The map is not copied but used directly, and if truncated is set,
     * all the values in the map are set to 1L!
     *
     * @param values values and frequencies
     * @param truncated whether the list is truncated or not
     */
    public TruncatableFreqList(TreeMap<String, Long> values, boolean truncated) {
        this.values = values;
        this.truncated = false;
        this.limitValues = truncated ? values.size() : Integer.MAX_VALUE;
        if (truncated)
            setTruncated();
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

    public boolean add(String value, long count) {
        if (truncated)
            return false;
        if (values.size() >= limitValues && !values.containsKey(value)) {
            // Reached the limit; stop storing now and indicate that there's more.
            setTruncated();
            return false;
        }
        // Count as normal
        values.compute(value, (__, prevCount) ->
                prevCount == null ? count : prevCount + count);
        return true;
    }

    private void setTruncated() {
        if (!truncated) {
            truncated = true;
            // We set all frequencies to 1, so it is abundantly clear that these are not correct for a truncated list.
            values.replaceAll((k, v) -> 1L);
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
