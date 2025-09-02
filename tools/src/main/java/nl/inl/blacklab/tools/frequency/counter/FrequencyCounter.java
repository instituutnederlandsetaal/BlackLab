package nl.inl.blacklab.tools.frequency.counter;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.tools.frequency.config.frequency.FrequencyListConfig;
import nl.inl.blacklab.tools.frequency.counter.index.IndexFrequencyCounter;
import nl.inl.blacklab.tools.frequency.counter.search.SearchFrequencyCounter;
import nl.inl.blacklab.tools.frequency.data.helper.IndexHelper;
import nl.inl.blacklab.tools.frequency.writers.TsvWriter;
import nl.inl.util.LuceneUtil;

import nl.inl.util.Timer;

import org.apache.lucene.queryparser.classic.ParseException;

import java.util.List;

public abstract class FrequencyCounter {
    protected final BlackLabIndex index;
    protected final FrequencyListConfig cfg;
    protected final IndexHelper helper;
    protected final TsvWriter tsvWriter;

    protected FrequencyCounter(final BlackLabIndex index, final FrequencyListConfig cfg, final IndexHelper helper) {
        this.index = index;
        this.cfg = cfg;
        this.helper = helper;
        this.tsvWriter = new TsvWriter(cfg, helper);
    }

    public static FrequencyCounter create(final BlackLabIndex index, final FrequencyListConfig cfg,
            final IndexHelper helper) {
        return cfg.runConfig().regularSearch() ?
                new SearchFrequencyCounter(index, cfg, helper) :
                new IndexFrequencyCounter(index, cfg, helper);
    }

    /**
     * Get all document IDs matching the filter.
     * If no filter is defined, return all document IDs.
     */
    public static List<Integer> getDocIds(BlackLabIndex index, FrequencyListConfig cfg) {
        final var t = new Timer();
        final var docIds = new IntArrayList();
        if (cfg.filter() != null) {
            try {
                final var q = LuceneUtil.parseLuceneQuery(index, cfg.filter(), index.analyzer(), "");
                index.queryDocuments(q).forEach(d -> docIds.add(d.docId()));
            } catch (final ParseException e) {
                throw new RuntimeException(e);
            }
        } else {
            // No filter: include all documents.
            index.forEachDocument((__, id) -> docIds.add(id));
        }
        System.out.println("  Retrieved " + docIds.size() + " documents IDs with filter='" + cfg.filter() + "' in "
                + t.elapsedDescription(true));
        return docIds;
    }

    public void count() {
        System.out.println("Generate frequency list: " + cfg.name());
    }
}
