package nl.inl.blacklab.tools.frequency.builder;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.tools.frequency.config.FrequencyListConfig;
import nl.inl.blacklab.tools.frequency.data.AnnotationInfo;
import nl.inl.blacklab.tools.frequency.writers.TsvWriter;

public abstract class FreqListBuilder {
    final BlackLabIndex index;
    final FrequencyListConfig cfg;
    final AnnotationInfo aInfo;
    final TsvWriter tsvWriter;

    FreqListBuilder(final BlackLabIndex index, final FrequencyListConfig cfg) {
        this.index = index;
        this.cfg = cfg;
        this.aInfo = new AnnotationInfo(index, cfg);
        this.tsvWriter = new TsvWriter(cfg, aInfo);
    }

    public static FreqListBuilder from(final BlackLabIndex index, final FrequencyListConfig cfg) {
        return cfg.runConfig().regularSearch() ?
                new SearchBasedBuilder(index, cfg) :
                new IndexBasedBuilder(index, cfg);
    }

    public void makeFrequencyList() {
        System.out.println("Generate frequency list: " + cfg.name());
    }

    public AnnotationInfo getAnnotationInfo() {
        return aInfo;
    }

}
