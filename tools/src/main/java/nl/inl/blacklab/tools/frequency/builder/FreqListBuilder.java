package nl.inl.blacklab.tools.frequency.builder;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.tools.frequency.config.BuilderConfig;
import nl.inl.blacklab.tools.frequency.config.FreqListConfig;
import nl.inl.blacklab.tools.frequency.data.AnnotationInfo;

public abstract class FreqListBuilder {
    final BlackLabIndex index;
    final BuilderConfig bCfg;
    final FreqListConfig fCfg;
    final AnnotationInfo aInfo;

    FreqListBuilder(final BlackLabIndex index, final BuilderConfig bCfg, final FreqListConfig fCfg) {
        this.index = index;
        this.bCfg = bCfg;
        this.fCfg = fCfg;
        this.aInfo = new AnnotationInfo(index, bCfg, fCfg);
    }

    public void makeFrequencyList() {
        System.out.println("Generate frequency list: " + fCfg.getReportName());
    }
}
