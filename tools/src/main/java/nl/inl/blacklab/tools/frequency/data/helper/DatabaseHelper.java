package nl.inl.blacklab.tools.frequency.data.helper;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.tools.frequency.config.frequency.FrequencyListConfig;
import nl.inl.blacklab.tools.frequency.config.frequency.MetadataConfig;
import nl.inl.blacklab.tools.frequency.counter.FrequencyCounter;
import nl.inl.blacklab.tools.frequency.counter.index.DocumentFrequencyCounter;
import nl.inl.blacklab.tools.frequency.data.MetadataTerms;
import nl.inl.blacklab.tools.frequency.data.IdMap;
import nl.inl.util.LuceneUtil;
import nl.inl.util.Timer;

import org.apache.lucene.queryparser.classic.ParseException;

public record DatabaseHelper(
        IdMap metaToId,
        IdMap wordToId,
        MetadataTerms metadataTerms,
        int[] groupedMetadata,
        int[] ungroupedMetadata
) {
    public static DatabaseHelper create(final BlackLabIndex index, final FrequencyListConfig cfg) {
        final var groupedMetadata = cfg.metadata().stream().filter(MetadataConfig::outputAsId)
                .mapToInt(m -> cfg.metadata().indexOf(m)).toArray();
        final var ungroupedMetadata = cfg.metadata().stream().filter(m -> !m.outputAsId())
                .mapToInt(m -> cfg.metadata().indexOf(m)).toArray();
        final var metadataTerms = new MetadataTerms(index, cfg);
        final var metaToId = getSortedMetaToId(index, cfg, metadataTerms, groupedMetadata);
        return new DatabaseHelper(metaToId, new IdMap(), metadataTerms, groupedMetadata,
                ungroupedMetadata);
    }

    /** Creates a sorted metadata-to-id map that is consistent between runs */
    private static IdMap getSortedMetaToId(final BlackLabIndex index, final FrequencyListConfig cfg,
            final MetadataTerms terms, final int[] groupedMetadata) {
        final var t = new Timer();
        final var metaToId = new IdMap();
        final var docIds = FrequencyCounter.getDocIds(index, cfg);
        for (final int id : docIds) {
            final var doc = index.luceneDoc(id);
            final var metadataTermIds = DocumentFrequencyCounter.getMetadataTermIds(terms, doc, cfg);
            if (metadataTermIds == null)
                continue; // skip this document
            metaToId.putOrGet(metadataTermIds, groupedMetadata);
        }
        System.out.println("  " + metaToId.getMap().size() + " unique metadata value combinations in "
                + t.elapsedDescription(true));
        return metaToId;
    }
}
