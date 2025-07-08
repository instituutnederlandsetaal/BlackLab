package nl.inl.blacklab.tools.frequency.config;

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
final public class BuilderConfig {

    /**
     * Number of docs to process in parallel per run. After each run,
     * we check if we need to write to chunk file.
     * Larger values allow more parallellism but risk overshooting the
     * chunk file size target.
     * Optional, for advanced performance tuning.
     */
    private int docsToProcessInParallel = 500_000;

    /**
     * How large to grow the grouping until we write the intermediate result to disk.
     * Higher values decrease processing overhead but increase memory requirements.
     * Optional, for advanced performance tuning.
     */
    private int groupsPerChunk = 10_000_000;

    /**
     * Whether to compress any files written to disk, including intermediate chunk files.
     * Default: false.
     */
    private boolean compressed;

    /**
     * Use regular search instead of specifically optimized one?
     * Optional, for debugging.
     */
    private boolean useRegularSearch;

    /**
     * How often to count each document.
     * Optional, for debugging.
     */
    private int repetitions = 1;

    /**
     * Whether to output in database format.
     * Results in outputting ID's instead of string values for the annotations. (Metadata is left as is.)
     * Also outputs a 'lookup table' for each annotation, mapping the ID's to the string values.
     */
    private boolean databaseFormat;

    /**
     * Output directory for frequency lists.
     */
    private File outputDir = new File(".");
    /**
     * Annotated field to analyze
     */
    private String annotatedField;
    /**
     * Frequency lists to make
     */
    private List<FreqListConfig> frequencyLists;

    /**
     * Read config from file.
     *
     * @param file config file
     * @return config object
     */
    public static BuilderConfig fromFile(final File file) {
        try {
            final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            return mapper.readValue(file, BuilderConfig.class);
        } catch (final IOException e) {
            throw new BlackLabRuntimeException("Error reading config file " + file, e);
        }
    }

    public String getAnnotatedField() {
        return annotatedField;
    }

    @SuppressWarnings("unused")
    public void setAnnotatedField(final String annotatedField) {
        this.annotatedField = annotatedField;
    }

    public List<FreqListConfig> getFrequencyLists() {
        return frequencyLists;
    }

    @SuppressWarnings("unused")
    public void setFrequencyLists(final List<FreqListConfig> frequencyLists) {
        this.frequencyLists = frequencyLists;
    }

    public int getGroupsPerChunk() {
        return groupsPerChunk;
    }

    @SuppressWarnings("unused")
    public void setGroupsPerChunk(final int groupsPerChunk) {
        this.groupsPerChunk = groupsPerChunk;
    }

    public int getDocsToProcessInParallel() {
        return docsToProcessInParallel;
    }

    @SuppressWarnings("unused")
    public void setDocsToProcessInParallel(final int docsToProcessInParallel) {
        this.docsToProcessInParallel = docsToProcessInParallel;
    }

    public boolean useRegularSearch() {
        return useRegularSearch;
    }

    @SuppressWarnings("unused")
    public void setUseRegularSearch(final boolean useRegularSearch) {
        this.useRegularSearch = useRegularSearch;
    }

    public boolean isCompressed() {
        return compressed;
    }

    @SuppressWarnings("unused")
    public void setCompressed(final boolean compressed) {
        this.compressed = compressed;
    }

    @Override
    public String toString() {
        return "Config{" +
                "docsToProcessInParallel=" + docsToProcessInParallel +
                ", groupsPerChunk=" + groupsPerChunk +
                ", compressed=" + compressed +
                ", useRegularSearch=" + useRegularSearch +
                ", repetitions=" + repetitions +
                ", databaseFormat=" + databaseFormat +
                ", outputDir=" + outputDir +
                ", annotatedField=" + annotatedField +
                ", frequencyLists=" + frequencyLists +
                "}";
    }

    public String show() {
        return "docsToProcessInParallel: " + docsToProcessInParallel + "\n" +
                "groupsPerChunk: " + groupsPerChunk + "\n" +
                "compressed: " + compressed + "\n" +
                "useRegularSearch: " + useRegularSearch + "\n" +
                "repetitions: " + repetitions + "\n" +
                "databaseFormat: " + databaseFormat + "\n" +
                "outputDir: " + outputDir + "\n" +
                "annotatedField: " + annotatedField + "\n" +
                "frequencyLists:\n" +
                frequencyLists.stream().map(FreqListConfig::toString).collect(Collectors.joining("\n"));
    }

    /**
     * Check if this is a valid config.
     *
     * @param index our index
     */
    public void check(final BlackLabIndex index) {
        if (!index.annotatedFields().exists(annotatedField))
            throw new IllegalArgumentException("Annotated field not found: " + annotatedField);
        final AnnotatedField af = index.annotatedField(annotatedField);
        final Set<String> reportNames = new HashSet<>();
        for (final FreqListConfig l: frequencyLists) {
            final String name = l.getReportName();
            if (reportNames.contains(name))
                throw new IllegalArgumentException("Report occurs twice: " + name);
            reportNames.add(name);

            for (final String a: l.annotations()) {
                if (!af.annotations().exists(a))
                    throw new IllegalArgumentException("Annotation not found: " + annotatedField + "." + a);
            }
            for (final String m: l.metadataFields().stream().map(MetadataConfig::name).toList()) {
                if (!index.metadataFields().exists(m))
                    throw new IllegalArgumentException("Metadata field not found: " + m);
            }
        }
    }

    public int getRepetitions() {
        return repetitions;
    }

    @SuppressWarnings("unused")
    public void setRepetitions(final int repetitions) {
        this.repetitions = repetitions;
    }

    public boolean isDatabaseFormat() {
        return databaseFormat;
    }

    public void setDatabaseFormat(final boolean databaseFormat) {
        this.databaseFormat = databaseFormat;
    }

    public File getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(final File outputDir) {
        this.outputDir = outputDir;
    }
}
