package nl.inl.blacklab.tools.frequency.data.helper;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.tools.frequency.config.frequency.FrequencyListConfig;
import nl.inl.blacklab.tools.frequency.config.frequency.MetadataConfig;
import nl.inl.blacklab.tools.frequency.data.FreqMetadata;
import nl.inl.blacklab.tools.frequency.data.IdMap;

public record DatabaseHelper(
        IdMap metaToId,
        IdMap wordToId,
        FreqMetadata freqMetadata,
        int[] groupedMetadata,
        int[] ungroupedMetadata
) {
    public static DatabaseHelper create(final BlackLabIndex index, final FrequencyListConfig cfg) {
        final var groupedMetadata = cfg.metadata().stream().filter(MetadataConfig::outputAsId)
                .mapToInt(m -> cfg.metadata().indexOf(m)).toArray();
        final var ungroupedMetadata = cfg.metadata().stream().filter(m -> !m.outputAsId())
                .mapToInt(m -> cfg.metadata().indexOf(m)).toArray();
        return new DatabaseHelper(new IdMap(), new IdMap(), new FreqMetadata(index, cfg), groupedMetadata, ungroupedMetadata);
    }
}
