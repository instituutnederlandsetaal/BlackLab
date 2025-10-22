package nl.inl.blacklab.tools.frequency.config;

import java.io.File;

import org.apache.commons.lang3.StringUtils;

/**
 * Parse and verify CLI arguments.
 *
 * @param indexDir   index to generate frequency lists for
 * @param configFile YAML file specifying what frequency lists to generate
 * @param outputDir  where to write TSV output files
 */
public record ArgsParser(
        File indexDir,
        File configFile,
        File outputDir
) {
    public static ArgsParser parse(final String[] args) {
        // Print help
        for (final String arg: args) {
            if ("--help".equals(arg) || "-h".equals(arg)) {
                exitUsage(null);
            }
        }
        // Verify number of args
        if (args.length < 2 || args.length > 3) {
            exitUsage("Incorrect number of arguments.");
        }
        // Verify index dir
        final var indexDir = new File(args[0]);
        if (!indexDir.isDirectory() || !indexDir.canRead()) {
            exit("Can't read or not a directory " + indexDir);
        }
        // Verify config file
        final var configFile = new File(args[1]);
        if (!configFile.canRead()) {
            exit("Can't read config file " + configFile);
        }
        // Verify output dir
        final File outputDir;
        if (args.length > 2) { // optional, defaults to current dir
            outputDir = new File(args[2]);
        } else {
            outputDir = new File(".");
        }
        if (!outputDir.isDirectory() || !outputDir.canWrite()) {
            exit("Can't write or not a directory " + outputDir);
        }

        return new ArgsParser(indexDir, configFile, outputDir);
    }

    private static void exit(final String msg) {
        System.out.println(msg);
        System.exit(1);
    }

    private static void exitUsage(final String msg) {
        if (!StringUtils.isEmpty(msg)) {
            System.out.println(msg + "\n");
        }
        exit("""
                Calculate term frequencies over annotation(s) and metadata field(s).
                
                Usage:
                
                  FrequencyTool INDEX_DIR CONFIG_FILE [OUTPUT_DIR]
                
                  INDEX_DIR    index to generate frequency lists for
                  CONFIG_FILE  YAML file specifying what frequency lists to generate. See README.md.
                  OUTPUT_DIR   where to write TSV output files (defaults to current dir)
                """);
    }
}
