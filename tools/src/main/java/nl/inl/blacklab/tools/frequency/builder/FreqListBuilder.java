package nl.inl.blacklab.tools.frequency.builder;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.tools.frequency.config.BuilderConfig;
import nl.inl.blacklab.tools.frequency.config.FreqListConfig;

public abstract class FreqListBuilder {
    protected BlackLabIndex index;
    protected AnnotatedField annotatedField;
    BuilderConfig bCfg;
    FreqListConfig fCfg;

    FreqListBuilder(BlackLabIndex index, BuilderConfig bCfg, FreqListConfig fCfg) {
        this.index = index;
        this.bCfg = bCfg;
        this.fCfg = fCfg;
        this.annotatedField = index.annotatedField(bCfg.getAnnotatedField());
    }

    public void makeFrequencyList() {
        List<String> extraInfo = new ArrayList<>();
        if (bCfg.getRepetitions() > 1)
            extraInfo.add(bCfg.getRepetitions() + " repetitions");
        if (bCfg.isUseRegularSearch())
            extraInfo.add("regular search");
        String strExtraInfo = extraInfo.isEmpty() ? "" : " (" + StringUtils.join(extraInfo, ", ") + ")";
        System.out.println("Generate frequency list" + strExtraInfo + ": " + fCfg.getReportName());
    }
}
