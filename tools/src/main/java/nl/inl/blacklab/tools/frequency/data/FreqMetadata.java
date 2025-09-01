package nl.inl.blacklab.tools.frequency.data;

import java.util.List;
import java.util.Map;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.tools.frequency.config.FrequencyListConfig;

final public class FreqMetadata {
    private final Map<String, List<String>> metaFieldToValues;

    FreqMetadata(final BlackLabIndex index, final FrequencyListConfig fCfg) {
        metaFieldToValues = new Object2ObjectArrayMap<>();
        for (final var meta: fCfg.metadata()) {
            final var valueSet = new ObjectArrayList<String>();
            final var valueFreqs = AnnotationInfo.UniqueTermsFromField(index, meta.name());
            valueSet.addAll(valueFreqs);
            // if this metadata has a custom null value, add it
            if (meta.nullValue() != null) {
                valueSet.add(meta.nullValue());
            }
            metaFieldToValues.put(meta.name(), valueSet);
            System.out.println("  " + valueSet.size() + " unique values of " + meta.name());
        }
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
