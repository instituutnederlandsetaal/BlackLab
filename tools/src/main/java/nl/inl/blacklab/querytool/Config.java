package nl.inl.blacklab.querytool;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.help.HelpFormatter;

import nl.inl.util.FileUtil;

class Config {

    static Options options = getCommandLineOptions();

    private static Options getCommandLineOptions() {
        Options options = new Options();
        options.addOption(Option.builder().longOpt("mode").hasArg().argName("mode")
                .desc("Mode to run the tool in. Valid modes are 'correctness', 'performance' or 'all', " +
                        "or their 1-character abbreviations. Without -f, 'all' is the default. With -f, 'correctness' " +
                        "is the default. Correctness shows results; performance shows timings; all shows both.").get());
        options.addOption(Option.builder().longOpt("encoding").hasArg().argName("charset")
                .desc("Encoding to use. If omitted, uses system default.").get());
        options.addOption(Option.builder("v").longOpt("verbose")
                .desc("Start in verbose mode (show query & rewrite).").get());
        options.addOption(Option.builder("f").longOpt("file").hasArg().argName("command-file")
                .desc("Execute batch commands from file and exit. " +
                        "Batch command files should contain one command per line, or multiple " +
                        "commands on a single line separated by && (use this e.g. to time " +
                        "querying and sorting together). Lines starting with # are comments. " +
                        "Comments are printed on stdout as well. Lines starting with - will " +
                        "not be reported. Start a line with -# for an unreported comment.").get());
        return options;
    }

    static void showUsage() {
        try {
            HelpFormatter formatter = HelpFormatter.builder().setShowSince(false).get();
            formatter.printHelp("QueryTool <corpus-dir>",
                    "Interactive and batch querying tool for BlackLab corpora.",
                    options, "", true);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Get Config object from command line arguments AND configure output object.
     *
     * @param args   command line arguments
     * @param output output object to configure (and write messages to)
     * @return the Config object
     */
    public static Config fromCommandLineCommonsCli(String[] args, Output output) {
        // Parse command line
        CommandLine cmd = null;
        try {
            cmd = new DefaultParser().parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            showUsage();
            System.exit(1);
        }

        Config config = new Config();
        String mode = cmd.getOptionValue("mode", "all").toLowerCase();
        if (mode.matches("c(orrectness)?")) {
            // Correctness testing: we want results, no timing and larger pagesize
            output.setShowOutput(true);
            config.showStats = false;
            QueryToolImpl.defaultPageSize = 1000;
            QueryToolImpl.alwaysSortBy = "after:word:s,hitposition"; // for reproducibility
            output.setShowDocIds(false); // doc ids are randomly assigned
            output.setShowMatchInfo(false); // (temporary)
        } else if (mode.matches("p(erformance)?")) {
            // Performance testing: we want timing and no results
            output.setShowOutput(false);
            config.showStats = true;
        } else if (mode.matches("a(ll)?")) {
            // Regular: we want results and timing
            output.setShowOutput(true);
            config.showStats = true;
            output.setShowMatchInfo(true);
        } else {
            return error("Unknown mode: " + mode);
        }

        config.encoding = Charset.forName(cmd.getOptionValue("encoding", Charset.defaultCharset().name()));
        String filePath = cmd.getOptionValue("file");
        if (filePath != null) {
            config.inputFile = new File(filePath);
            output.error("Batch mode; reading commands from " + config.inputFile);
        }
        if (cmd.hasOption("verbose")) {
            output.setVerbose(true);
            output.setShowMatchInfo(true);
        }
        config.indexDir = new File(cmd.getArgs().length > 0 ? cmd.getArgs()[0] : ".");
        return finalizeConfig(output, config);
    }

    private static Config finalizeConfig(Output output, Config config) {
        if (config.indexDir == null)
            return error("No index directory specified");
        if (!config.indexDir.exists() || !config.indexDir.isDirectory())
            return error("Index dir " + config.indexDir.getPath() + " doesn't exist.");

        // By default we don't show stats in batch mode, but we do in interactive mode
        // (batch mode is useful for correctness testing, where you don't want stats;
        //  use --mode performance to get stats but no results in batch mode)
        boolean showStatsDefaultValue = config.inputFile == null;
        output.setShowStats(config.showStats == null ? showStatsDefaultValue : config.showStats);

        // Use correct output encoding
        // Yes
        output.setOutputWriter(new PrintWriter(new OutputStreamWriter(System.out, config.encoding), true));
        output.setErrorWriter(new PrintWriter(new OutputStreamWriter(System.err, config.encoding), true));
        output.line("Using output encoding " + config.encoding + "\n");

        if (config.inputFile != null)
            output.setBatchMode(true);

        return config;
    }

    /**
     * Get a Config object with an error message.
     *
     * @param error the error message
     * @return the Config object
     */
    public static Config error(String error) {
        Config config = new Config();
        config.error = error;
        return config;
    }

    private String error = null;

    private File indexDir = null;

    private File inputFile = null;

    private Charset encoding = Charset.defaultCharset();

    private Boolean showStats = null; // default not overridden (default depends on batch mode or not)

    public String getError() {
        return error;
    }

    public File getIndexDir() {
        return indexDir;
    }

    public BufferedReader getInput() throws UnsupportedEncodingException, FileNotFoundException {
        return inputFile == null ?
                new BufferedReader(new InputStreamReader(System.in, encoding)) :
                FileUtil.openForReading(inputFile, QueryToolImpl.INPUT_FILE_ENCODING);
    }
}
