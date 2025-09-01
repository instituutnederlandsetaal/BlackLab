package nl.inl.blacklab.tools.frequency.builder;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.tools.frequency.config.Config;
import nl.inl.blacklab.tools.frequency.config.FrequencyListConfig;
import nl.inl.blacklab.tools.frequency.data.AnnotationInfo;
import nl.inl.blacklab.tools.frequency.writers.TsvWriter;

public abstract class FreqListBuilder {
    final BlackLabIndex index;
    final Config cfg;
    final FrequencyListConfig fCfg;
    final AnnotationInfo aInfo;
    final TsvWriter tsvWriter;

    FreqListBuilder(final BlackLabIndex index, final Config bCfg, final FrequencyListConfig fCfg) {
        this.index = index;
        this.cfg = bCfg;
        this.fCfg = fCfg;
        this.aInfo = new AnnotationInfo(index, bCfg, fCfg);
        this.tsvWriter = new TsvWriter(bCfg, fCfg, aInfo);
    }

    public void makeFrequencyList() {
        System.out.println("Generate frequency list: " + fCfg.name());
    }

    public AnnotationInfo getAnnotationInfo() {
        return aInfo;
    }

    public TsvWriter getTsvWriter() {
        return tsvWriter;
    }
}
