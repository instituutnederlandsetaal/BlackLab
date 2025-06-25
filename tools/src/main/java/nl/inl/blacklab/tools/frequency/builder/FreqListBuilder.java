package nl.inl.blacklab.tools.frequency.builder;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.tools.frequency.config.BuilderConfig;
import nl.inl.blacklab.tools.frequency.config.FreqListConfig;

/**
 * More optimized version of HitGroupsTokenFrequencies.
 * Takes shortcuts to be able to process huge corpora without
 * running out of memory, at the expense of genericity.
 * Major changes:
 * - store metadata values as strings, not PropertyValue
 * - always group on annotations first, then metadata fields
 * - don't create HitGroups, return Map with counts directly
 * - don't check if we exceed maxHitsToCount
 * - always process all documents (no document filter query)
 * - return sorted map, so we can perform sub-groupings and merge them later
 * (uses ConcurrentSkipListMap, or alternatively wraps a TreeMap at the end;
 * note that using ConcurrentSkipListMap has consequences for the compute() method, see there)
 */
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
