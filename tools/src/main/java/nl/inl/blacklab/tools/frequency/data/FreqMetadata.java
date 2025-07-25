package nl.inl.blacklab.tools.frequency.data;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.MetadataField;
import nl.inl.blacklab.tools.frequency.config.FreqListConfig;

import java.util.List;
import java.util.Map;

final public class FreqMetadata {
    private final Map<String, List<String>> metaFieldToValues;

    FreqMetadata(final BlackLabIndex index, final FreqListConfig fCfg) {
        metaFieldToValues = new Object2ObjectArrayMap<>();
        for (final var meta : fCfg.metadataFields()) {
            final var valueSet = new ObjectArrayList<String>();
            final var valueFreqs = ((MetadataField)index.field(meta.name())).values(Long.MAX_VALUE).valueList().getValues();
            valueSet.addAll(valueFreqs.keySet());
            // if this metadata has a custom null value, add it
            if (meta.nullValue() != null) {
                valueSet.add(meta.nullValue());
            }
            metaFieldToValues.put(meta.name(), valueSet);
        }
    }

    public int getIdx(final String field, final String value) {
        return metaFieldToValues.get(field).indexOf(value);
    }

    public String getValue(final String field, final int idx) {
        return metaFieldToValues.get(field).get(idx);
    }
}
