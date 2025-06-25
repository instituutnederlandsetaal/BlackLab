package nl.inl.blacklab.tools.frequency;

import java.io.File;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.results.HitGroups;

interface FreqListOutput {

    FreqListOutput TSV = new FreqListOutputTsv();

    /**
     * Write a frequency list file.
     *
     * This is used with the unoptimized code.
     *
     * @param index          our index
     * @param annotatedField annotated field
     * @param freqList       config
     * @param result         resulting frequencies
     * @param config         global configuration
     */
    void write(BlackLabIndex index, AnnotatedField annotatedField, ConfigFreqList freqList,
               HitGroups result, Config config);

    /**
     * Write a frequency list file.
     *
     * @param index          our index
     * @param annotatedField annotated field
     * @param reportName     report name (file name without extensions)
     * @param annotationNames names of annotations grouped on
     * @param occurrences    resulting frequencies
     * @param config         global configuration
     */
    File write(BlackLabIndex index, AnnotatedField annotatedField, String reportName,
               List<String> annotationNames, Map<GroupIdHash, OccurrenceCounts> occurrences, Config config);

}
