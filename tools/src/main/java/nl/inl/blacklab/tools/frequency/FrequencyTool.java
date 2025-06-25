package nl.inl.blacklab.tools.frequency;

import java.io.File;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.searches.SearchCacheDummy;
import nl.inl.blacklab.tools.frequency.builder.FreqListBuilder;
import nl.inl.blacklab.tools.frequency.builder.OptimizedBuilder;
import nl.inl.blacklab.tools.frequency.builder.UnoptimizedBuilder;
import nl.inl.blacklab.tools.frequency.config.BuilderConfig;
import nl.inl.blacklab.tools.frequency.config.FreqListConfig;
import nl.inl.util.Timer;

/**
 * Determine frequency lists over annotation(s) and
 * metadata field(s) for the entire index.
 */
public class FrequencyTool {

    private static void exit(String msg) {
        System.out.println(msg);
        System.exit(1);
    }

    private static void exitUsage(String msg) {
        if (!StringUtils.isEmpty(msg)) {
            System.out.println(msg + "\n");
        }
        exit("""
                Calculate term frequencies over annotation(s) and metadata field(s).
                
                Usage:
                
                  FrequencyTool [--compress] [--db-fmt] INDEX_DIR CONFIG_FILE [OUTPUT_DIR]
                
                  --compress   write directly to .lz4 file
                  --no-merge   don't merge chunk files, write separate tsvs instead
                  INDEX_DIR    index to generate frequency lists for
                  CONFIG_FILE  YAML file specifying what frequency lists to generate. See README.md.
                  OUTPUT_DIR   where to write TSV output files (defaults to current dir)
                
                """);
    }

    public static void main(String[] args) throws ErrorOpeningIndex {

        BlackLab.setConfigFromFile(); // read blacklab.yaml if exists and set config from that

        // Check for options
        int numOpts = 0;
        boolean compressed = false;
        for (String arg: args) {
            if (arg.startsWith("--")) {
                numOpts++;
                switch (arg) {
                case "--compress":
                    compressed = true;
                    break;
                case "--help":
                    exitUsage("");
                    break;
                }
            } else
                break;
        }

        // Process arguments
        int numArgs = args.length - numOpts;
        if (numArgs < 2 || numArgs > 3) {
            exitUsage("Incorrect number of arguments.");
        }

        // Read config file
        File configFile = new File(args[numOpts + 1]);
        if (!configFile.canRead()) {
            exit("Can't read config file " + configFile);
        }
        BuilderConfig config = BuilderConfig.fromFile(configFile);

        // Set output directory
        File outputDir = new File(System.getProperty("user.dir")); // current dir
        if (numArgs > 2) {
            outputDir = new File(args[numOpts + 2]);
        }
        if (!outputDir.isDirectory() || !outputDir.canWrite()) {
            exit("Not a directory or cannot write to output dir " + outputDir);
        }

        // Set config options
        config.setCompressed(compressed);
        config.setOutputDir(outputDir);
        System.out.println("CONFIGURATION:\n" + config.show());

        // Open index
        File indexDir = new File(args[numOpts]);
        if (!indexDir.isDirectory() || !indexDir.canRead()) {
            exit("Can't read or not a directory " + indexDir);
        }

        try (BlackLabIndex index = BlackLab.open(indexDir)) {
            Timer t = new Timer();

            // Generate the frequency lists
            makeFrequencyLists(index, config);

            System.out.println("TOTAL TIME: " + t.elapsedDescription(true));
        }
    }

    private static void makeFrequencyLists(BlackLabIndex index, BuilderConfig config) {
        config.check(index);
        index.setCache(new SearchCacheDummy()); // don't cache results
        for (FreqListConfig freqList: config.getFrequencyLists()) {
            Timer t = new Timer();
            FreqListBuilder builder;
            if (config.isUseRegularSearch()) {
                builder = new UnoptimizedBuilder(index, config, freqList);
            } else {
                builder = new OptimizedBuilder(index, config, freqList);
            }
            builder.makeFrequencyList();
            System.out.println("  Time: " + t.elapsedDescription());
        }
    }
}
