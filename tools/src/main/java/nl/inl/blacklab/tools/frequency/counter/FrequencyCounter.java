package nl.inl.blacklab.tools.frequency.counter;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.tools.frequency.config.frequency.FrequencyListConfig;
import nl.inl.blacklab.tools.frequency.counter.index.IndexCounter;
import nl.inl.blacklab.tools.frequency.counter.search.SearchCounter;
import nl.inl.blacklab.tools.frequency.data.AnnotationInfo;
import nl.inl.blacklab.tools.frequency.writers.TsvWriter;

public abstract class FrequencyCounter {
    protected final BlackLabIndex index;
    protected final FrequencyListConfig cfg;
    protected final AnnotationInfo aInfo;
    protected final TsvWriter tsvWriter;

    protected FrequencyCounter(final BlackLabIndex index, final FrequencyListConfig cfg) {
        this.index = index;
        this.cfg = cfg;
        this.aInfo = new AnnotationInfo(index, cfg);
        this.tsvWriter = new TsvWriter(cfg, aInfo);
    }

    public static FrequencyCounter from(final BlackLabIndex index, final FrequencyListConfig cfg) {
        return cfg.runConfig().regularSearch() ?
                new SearchCounter(index, cfg) :
                new IndexCounter(index, cfg);
    }

    public void count() {
        System.out.println("Generate frequency list: " + cfg.name());
    }

    public AnnotationInfo getAnnotationInfo() {
        return aInfo;
    }

}
