package nl.inl.blacklab.tools.frequency.counter;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.tools.frequency.config.frequency.FrequencyListConfig;
import nl.inl.blacklab.tools.frequency.counter.index.IndexFrequencyCounter;
import nl.inl.blacklab.tools.frequency.counter.search.SearchFrequencyCounter;
import nl.inl.blacklab.tools.frequency.data.helper.IndexHelper;
import nl.inl.blacklab.tools.frequency.writers.TsvWriter;

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

    public void count() {
        System.out.println("Generate frequency list: " + cfg.name());
    }
}
