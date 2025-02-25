package nl.inl.blacklab.tools.frequency;

import java.io.File;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.results.HitGroups;

interface FreqListOutput {

    enum Type {
        TSV,           // TSV file
        TSV_GZIP,      // gzipped TSV file
        UNMERGED_TSV_GZ   // separate TSV files for each chunk (to avoid crash when merging)
    }

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
     * @param outputDir      directory to write output file
     */
    void write(BlackLabIndex index, AnnotatedField annotatedField, ConfigFreqList freqList,
               HitGroups result, File outputDir, boolean gzip);

    /**
     * Write a frequency list file.
     *
     * @param index          our index
     * @param annotatedField annotated field
     * @param reportName     report name (file name without extensions)
     * @param annotationNames names of annotations grouped on
     * @param occurrences    resulting frequencies
     * @param outputDir      directory to write output file
     */
    File write(BlackLabIndex index, AnnotatedField annotatedField, String reportName,
               List<String> annotationNames, Map<GroupIdHash, OccurrenceCounts> occurrences,
               File outputDir, boolean gzip);

}
