package nl.inl.blacklab.tools.frequency;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;

/**
 * Configuration for making frequency lists
 */
class Config {

    /**
     * Number of docs to process in parallel per run. After each run,
     * we check if we need to write to chunk file.
     *
     * Larger values allow more parallellism but risk overshooting the
     * chunk file size target.
     *
     * Optional, for advanced performance tuning.
     */
    private int docsToProcessInParallel = 500_000;

    /**
     * How large to grow the grouping until we write the intermediate result to disk.
     *
     * Higher values decrease processing overhead but increase memory requirements.
     *
     * Optional, for advanced performance tuning.
     */
    private int groupsPerChunk = 10_000_000;

    /**
     * Compress temporary ("chunk") files?
     *
     * Optional. Takes less disk space but requires more processing time.
     * Default: false.
     */
    private boolean compressTempFiles = false;

    /**
     * Use regular search instead of specifically optimized one?
     *
     * Optional, for debugging.
     */
    private boolean useRegularSearch = false;

    /**
     * How often to count each document.
     *
     * Optional, for debugging.
     */
    private int repetitions = 1;

    /**
     * Read config from file.
     *
     * @param f config file
     * @return config object
     */
    static Config fromFile(File f) {
        try {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            return mapper.readValue(f, Config.class);
        } catch (IOException e) {
            throw new BlackLabRuntimeException("Error reading config file " + f, e);
        }
    }

    /**
     * Annotated field to analyze
     */
    private String annotatedField;

    /**
     * Frequency lists to make
     */
    private List<ConfigFreqList> frequencyLists;

    public String getAnnotatedField() {
        return annotatedField;
    }

    @SuppressWarnings("unused")
    public void setAnnotatedField(String annotatedField) {
        this.annotatedField = annotatedField;
    }

    public List<ConfigFreqList> getFrequencyLists() {
        return frequencyLists;
    }

    @SuppressWarnings("unused")
    public void setFrequencyLists(List<ConfigFreqList> frequencyLists) {
        this.frequencyLists = frequencyLists;
    }

    @SuppressWarnings("unused")
    public void setGroupsPerChunk(int groupsPerChunk) {
        this.groupsPerChunk = groupsPerChunk;
    }

    public int getGroupsPerChunk() {
        return this.groupsPerChunk;
    }

    public int getDocsToProcessInParallel() {
        return this.docsToProcessInParallel;
    }

    @SuppressWarnings("unused")
    public void setDocsToProcessInParallel(int docsToProcessInParallel) {
        this.docsToProcessInParallel = docsToProcessInParallel;
    }

    public boolean isUseRegularSearch() {
        return useRegularSearch;
    }

    @SuppressWarnings("unused")
    public void setUseRegularSearch(boolean useRegularSearch) {
        this.useRegularSearch = useRegularSearch;
    }

    public boolean isCompressTempFiles() {
        return compressTempFiles;
    }

    @SuppressWarnings("unused")
    public void setCompressTempFiles(boolean compressTempFiles) {
        this.compressTempFiles = compressTempFiles;
    }

    @Override
    public String toString() {
        return "Config{" +
                "docsToProcessInParallel=" + docsToProcessInParallel +
                ", groupsPerChunk=" + groupsPerChunk +
                ", compressTempFiles=" + compressTempFiles +
                ", useRegularSearch=" + useRegularSearch +
                ", repetitions=" + repetitions +
                ", annotatedField='" + annotatedField + '\'' +
                ", frequencyLists=" + frequencyLists +
                '}';
    }

    public String show() {
        return "docsToProcessInParallel: " + docsToProcessInParallel + "\n" +
                "groupsPerChunk: " + groupsPerChunk + "\n" +
                "compressTempFiles: " + compressTempFiles + "\n" +
                "useRegularSearch: " + useRegularSearch + "\n" +
                "repetitions: " + repetitions + "\n" +
                "annotatedField: '" + annotatedField + "\n" +
                "frequencyLists:\n" +
                frequencyLists.stream().map(ConfigFreqList::show).collect(Collectors.joining("\n"));
    }

    /**
     * Check if this is a valid config.
     *
     * @param index our index
     */
    public void check(BlackLabIndex index) {
        if (!index.annotatedFields().exists(annotatedField))
            throw new IllegalArgumentException("Annotated field not found: " + annotatedField);
        AnnotatedField af = index.annotatedField(annotatedField);
        Set<String> reportNames = new HashSet<>();
        for (ConfigFreqList l : frequencyLists) {
            String name = l.getReportName();
            if (reportNames.contains(name))
                throw new IllegalArgumentException("Report occurs twice: " + name);
            reportNames.add(name);

            for (String a : l.getAnnotations()) {
                if (!af.annotations().exists(a))
                    throw new IllegalArgumentException("Annotation not found: " + annotatedField + "." + a);
            }
            for (String m : l.getMetadataFields()) {
                if (!index.metadataFields().exists(m))
                    throw new IllegalArgumentException("Metadata field not found: " + m);
            }
        }
    }

    public int getRepetitions() {
        return this.repetitions;
    }

    @SuppressWarnings("unused")
    public void setRepetitions(int repetitions) {
        this.repetitions = repetitions;
    }


}
