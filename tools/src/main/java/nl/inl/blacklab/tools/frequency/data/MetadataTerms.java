package nl.inl.blacklab.tools.frequency.data;

import java.util.List;
import java.util.Map;
import java.util.Set;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.tools.frequency.config.frequency.FrequencyListConfig;
import nl.inl.blacklab.tools.frequency.counter.FrequencyCounter;
import nl.inl.util.LuceneUtil;

import org.apache.lucene.queryparser.classic.ParseException;

final public class MetadataTerms {
    private final Map<String, List<String>> terms;

    public MetadataTerms(final BlackLabIndex index, final FrequencyListConfig cfg) {
        final var uniqueMetadata = new Object2ObjectArrayMap<String, Set<String>>();
        // Initialize with custom null values
        final int numMetadata = cfg.metadata().size();
        for (int i = 0; i < numMetadata; i++) {
            final var metadata = cfg.metadata().get(i);
            final var values = new ObjectLinkedOpenHashSet<String>();
            // if this metadata has a custom null value, add it
            if (metadata.nullValue() != null) {
                values.add(metadata.nullValue());
            }
            uniqueMetadata.put(metadata.name(), values);
        }
        // Fill with document metadata
        final var docIds = FrequencyCounter.getDocIds(index, cfg);
        for (final int id : docIds) {
            for (int i = 0; i < numMetadata; i++) {
                final var metadata = cfg.metadata().get(i);
                final var field = index.luceneDoc(id).getField(metadata.name());
                if (field != null)
                    uniqueMetadata.get(metadata.name()).add(field.stringValue());
            }
        }
        // Add to map with lists for sake of indexing
        terms = new Object2ObjectArrayMap<>();
        for (int i = 0; i < numMetadata; i++) {
            final var metadata = cfg.metadata().get(i);
            System.out.println("  " + uniqueMetadata.get(metadata.name()).size() + " unique values of " + metadata.name());
            terms.put(metadata.name(), new ObjectArrayList<>(uniqueMetadata.get(metadata.name())));
        }
    }

    public int getIdx(final String field, final String value) {
        final int i = terms.get(field).indexOf(value);
        if (i < 0)
            throw new RuntimeException("Value '" + value + "' not found in metadata field '" + field + "'");
        return i;
    }

    public String getValue(final String field, final int idx) {
        return terms.get(field).get(idx);
    }
}
