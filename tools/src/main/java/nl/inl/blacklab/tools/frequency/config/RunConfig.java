package nl.inl.blacklab.tools.frequency.config;

/**
 * Config for how frequency list generation is run.
 *
 * @param docsInParallel Number of docs to process in parallel per run. After each run, we check if we need to write to chunk file.
 *                       Larger values allow more parallelism but risk overshooting the chunk file size target.
 * @param groupsPerChunk How large to grow the grouping until we write the intermediate result to disk.
 *                       Higher values decrease processing overhead but increase memory requirements.
 * @param compressed     Whether to compress any files written to disk, including intermediate chunk files.
 * @param regularSearch  Use regular search instead of specifically optimized one.
 * @param databaseFormat Whether to output in database format. Results in outputting ID's instead of string values for the annotations.
 *                       (Metadata is left as is.) Also outputs a 'lookup table' for each annotation, mapping the ID's to the string values.
 */
public record RunConfig(
        Integer docsInParallel,
        Integer groupsPerChunk,
        Boolean compressed,
        Boolean regularSearch,
        Boolean databaseFormat
) {
    public RunConfig {
        if (docsInParallel == null)
            docsInParallel = 500_000;
        if (groupsPerChunk == null)
            groupsPerChunk = 10_000_000;
        if (compressed == null)
            compressed = false;
        if (regularSearch == null)
            regularSearch = false;
        if (databaseFormat == null)
            databaseFormat = false;
    }
}
