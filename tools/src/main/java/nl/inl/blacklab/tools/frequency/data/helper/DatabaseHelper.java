package nl.inl.blacklab.tools.frequency.data.helper;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.tools.frequency.config.frequency.FrequencyListConfig;
import nl.inl.blacklab.tools.frequency.config.frequency.MetadataConfig;
import nl.inl.blacklab.tools.frequency.counter.index.DocumentFrequencyCounter;
import nl.inl.blacklab.tools.frequency.data.IdMap;
import nl.inl.blacklab.tools.frequency.data.MetadataTerms;
import nl.inl.util.Timer;

import java.io.IOException;
import java.util.stream.Collectors;

public record DatabaseHelper(
        IdMap metaToId,
        IdMap wordToId,
        MetadataTerms metadataTerms,
        int[] groupedMetadata,
        int[] ungroupedMetadata
) {
    public static DatabaseHelper create(final BlackLabIndex index, final FrequencyListConfig cfg) throws IOException {
        final var groupedMetadata = cfg.metadata().stream().filter(MetadataConfig::outputAsId)
                .mapToInt(m -> cfg.metadata().indexOf(m)).toArray();
        final var ungroupedMetadata = cfg.metadata().stream().filter(m -> !m.outputAsId())
                .mapToInt(m -> cfg.metadata().indexOf(m)).toArray();
        final var metadataTerms = new MetadataTerms(index, cfg);
        final var metaToId = getSortedMetaToId(index, cfg, metadataTerms, groupedMetadata);
        return new DatabaseHelper(metaToId, new IdMap(), metadataTerms, groupedMetadata,
                ungroupedMetadata);
    }

    /**
     * Creates a sorted metadata-to-id map that is consistent between runs
     */
    private static IdMap getSortedMetaToId(final BlackLabIndex index, final FrequencyListConfig cfg,
            final MetadataTerms terms, final int[] groupedMetadata) throws IOException {
        final var t = new Timer();
        final var metaToId = new IdMap();
        // final var docIds = FrequencyCounter.getDocIds(index, cfg);
        final var fieldsToLoad = cfg.metadata().stream().map(MetadataConfig::name).collect(Collectors.toSet());
        // TODO only calculate metadata terms for the filtered list of docIds, instead of all.
        for (final var lrc : index.reader().leaves()) {
            final var reader =  lrc.reader();
            final var storedFields = reader.storedFields();
            final int numDocs = reader.numDocs();
            for (int id = 0; id < numDocs; id++) {
                final var doc = storedFields.document(id, fieldsToLoad);
                final var metadataTermIds = DocumentFrequencyCounter.getMetadataTermIds(terms, doc, cfg);
                if (metadataTermIds == null)
                    continue; // skip this document
                metaToId.putOrGet(metadataTermIds, groupedMetadata);
            }
        }
        System.out.println("  " + metaToId.getMap().size() + " unique metadata value combinations in "
                + t.elapsedDescription(true));
        return metaToId;
    }
}
