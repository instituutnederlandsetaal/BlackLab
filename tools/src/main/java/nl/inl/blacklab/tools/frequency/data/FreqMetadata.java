package nl.inl.blacklab.tools.frequency.data;

import java.util.List;
import java.util.Map;
import java.util.Set;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.tools.frequency.config.frequency.FrequencyListConfig;

final public class FreqMetadata {
    private final Map<String, List<String>> metaFieldToValues;

    public FreqMetadata(final BlackLabIndex index, final FrequencyListConfig cfg) {
        metaFieldToValues = new Object2ObjectArrayMap<>();
        for (final var meta: cfg.metadata()) {
            final var valueSet = new ObjectArrayList<String>();
            final var valueFreqs = UniqueTermsFromField(index, meta.name());
            valueSet.addAll(valueFreqs);
            // if this metadata has a custom null value, add it
            if (meta.nullValue() != null) {
                valueSet.add(meta.nullValue());
            }
            metaFieldToValues.put(meta.name(), valueSet);
            System.out.println("  " + valueSet.size() + " unique values of " + meta.name());
        }
    }

    public static Set<String> UniqueTermsFromField(final BlackLabIndex index, final String field) {
        final var values = new ObjectLinkedOpenHashSet<String>();
        index.forEachDocument((__, id) -> {
            final var f = index.luceneDoc(id).getField(field);
            if (f != null)
                values.add(f.stringValue());
        });
        // return a sorted set for consistent ordering
        return values;
    }

    public int getIdx(final String field, final String value) {
        final int i = metaFieldToValues.get(field).indexOf(value);
        if (i < 0)
            throw new RuntimeException("Value '" + value + "' not found in metadata field '" + field + "'");
        return i;
    }

    public String getValue(final String field, final int idx) {
        return metaFieldToValues.get(field).get(idx);
    }
}
