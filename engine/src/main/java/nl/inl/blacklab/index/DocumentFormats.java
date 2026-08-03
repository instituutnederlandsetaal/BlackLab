package nl.inl.blacklab.index;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.exceptions.InvalidInputFormatConfig;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.indexers.config.FormatFileNameUtil;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.util.FileUtil;

/**
 * Document format finder, for resolving a DocIndexer class given a format
 * identifier (common abbreviation or (qualified) class name).
 */
public class DocumentFormats {
    static final Logger logger = LogManager.getLogger(DocumentFormats.class);

    /** Finders for various types of input formats (class-based, user formats, ...) */
    private static final List<FinderInputFormat> finders = new ArrayList<>();

    /** All currently loaded inputFormats. Always check isError() to make sure the format loaded correctly. */
    private static final Map<String, InputFormatInfo> inputFormats = new LinkedHashMap<>();

    /** Name of subdir of BlackLab config directory containing input format files. */
    public static final String FORMATS_CONFIG_DIR_NAME = "formats";

    static {
        // NOTE: Last added finder has priority (to allow users to override types)
        addFinder(new FinderInputFormatClass());

        // We also add these in a specific order, so that format files in the default directories
        // may override builtin formats.
//        addStandardConfigFormats(); // load formats built in to BL and from standard directories
        addFromFormatsConfigDir();
    }

    /**
     * Add a finder. Finders locate input formats of a certain category.
     * The finder that was added last will be checked first when looking for a format.
     */
    public static synchronized void addFinder(FinderInputFormat finder) {
        if (finders.contains(finder))
            return;
        finders.add(finder);
    }

    public static synchronized InputFormatInfo add(InputFormatInfo inputFormat) {
        String formatIdentifier = inputFormat.getIdentifier();
        if (inputFormats.containsKey(formatIdentifier)) {
            logger.info("Loading " + inputFormat + " overrode previously loaded format with this name");
        }
        inputFormats.put(formatIdentifier, inputFormat);
        return inputFormat;
    }

    public static InputFormatInfo add(String formatIdentifier, InputFormat inputFormat) {
        return add(new InputFormatInfoClass(formatIdentifier, inputFormat));
    }

    public static InputFormatInfo add(ConfigInputFormat format) throws InvalidInputFormatConfig {
        return add(new InputFormatInfoWithConfig(format));
    }

    public static synchronized void remove(String formatIdentifier) {
        inputFormats.remove(formatIdentifier);
    }

    /**
     * Check if a particular string denotes a valid document format.
     *
     * @param formatIdentifier format identifier, e.g. "tei" or
     *            "com.example.MyIndexer"
     * @return true iff this format is supported
     */
    public static boolean isSupported(String formatIdentifier) {
        return getFormat(formatIdentifier).isPresent();
    }
    
    /**
     * Returns a list of all valid input formats, in the reverse order they were added.
     *
     * Formats that had an error will be excluded.
     *
     * @return the list of input formats
     */
    public static synchronized Collection<InputFormatInfo> getFormats() {
        // TODO maybe we should have a well-defined order? alphabetical, or configs before classes?
        List<InputFormatInfo> reversed = inputFormats.values().stream()
                .filter(f -> !f.isError()).collect(Collectors.toList());
        Collections.reverse(reversed);
        return reversed;
    }

    /**
     * Returns a format descriptor for a specific format.
     *
     * This will only return formats that could succesfully be loaded.
     * Alternatively, use {@link #getFormatOrError(String)} to also get
     * the format if an error occurred.
     *
     * @return the descriptor, or null if not found
     */
    public static Optional<InputFormatInfo> getFormat(String formatIdentifier) {
        return getFormatOrError(formatIdentifier).filter(f -> !f.isError());
    }

    /**
     * Returns a format descriptor for a specific format.
     *
     * Note the format returned might have an error (e.g. failed to load the config file).
     * Check isError() and use getErrorMessage().
     * Alternatively, use {@link #getFormat(String)} to get only valid formats.
     *
     * @return the descriptor, or null if not found
     */
    public static synchronized Optional<InputFormatInfo> getFormatOrError(String formatIdentifier) {
        assert formatIdentifier != null;
        InputFormatInfo format = inputFormats.get(formatIdentifier);
        if (format == null)
            format = find(formatIdentifier);
        return Optional.ofNullable(format);
    }

    private static synchronized InputFormatInfo find(String formatIdentifier) {
        try {
            for (FinderInputFormat finder: finders) {
                InputFormatInfo format = finder.find(formatIdentifier);
                if (format != null) {
                    inputFormats.put(formatIdentifier, format);
                    return format;
                }
            }
            return null;
        } catch (PluginException e) {
            throw new InvalidInputFormatConfig(e);
        }
    }

//    private static void addStandardConfigFormats() {
//        // Note that these names should not collide with the abbreviations used by DocIndexerFactoryClass,
//        // or this will override those classes.
//        String[] formats = {
//                "chat", "cmdi", "conll-u", "csv",
//                "eaf", "folia", "naf", "sketch-wpl",
//                "tcf", "tei-p5", "tei-p4-legacy",
//                "tei-p5-legacy", "testformat", "tsv-frog",
//                "tsv", "txt" };
//        for (String formatIdentifier : formats) {
//            String fileNameRelative = "formats/" + FormatFileNameUtil.yamlFormatFileName(formatIdentifier);
//            try (InputStream is = DocumentFormats.class.getClassLoader()
//                    .getResourceAsStream(fileNameRelative)) {
//                if (is == null)
//                    continue; // not found
//
//                try (Reader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
//
//                    ConfigInputFormat format = ConfigInputFormat.read(reader, false, formatIdentifier);
//                    format.setReadFromFile(new File("$BLACKLAB_JAR/" + fileNameRelative));
//                    add(format);
//                }
//            } catch (InvalidInputFormatConfig | IOException e) {
//                //formatErrors.put(formatIdentifier, e.getMessage());
//                InputFormatInfo inputFormat = new InputFormatInfoError(formatIdentifier, e.getMessage());
//                add(inputFormat);
//                throw BlackLabException.wrapRuntime(e);
//            }
//        }
//    }

    private static void addFromFormatsConfigDir() {
        File formatsDir = new File(BlackLab.configDir(), FORMATS_CONFIG_DIR_NAME);
        if (formatsDir.exists() && formatsDir.isDirectory() && formatsDir.canRead())
            addConfigFormatsInDirectories(List.of(formatsDir));
    }

    /**
     * Locate all config files (files ending with .blf.yaml, .blf.yml, .blf.json)
     * within the list of directories and load them. Formats will use their filename
     * (excluding the .blf.* extension) as a unique identifier. Duplicate formats
     * will override a previously found one. Note that a configuration file is only
     * loaded when it's needed.
     * <p>
     * This is a one-time scan, so configs placed in these directories after this
     * scan will not be picked up.
     *
     * @throws InvalidInputFormatConfig when one of the formats could not be loaded
     */
    public static void addConfigFormatsInDirectories(Collection<File> dirs) throws InvalidInputFormatConfig {
        // Finds all new configs and add them to the supported list
        FileUtil.FileTask configLocator = new FileUtil.FileTask() {
            @Override
            public void process(File f) {
                if (FormatFileNameUtil.isValidFileName(f.getName())) {
                    String formatIdentifier = FormatFileNameUtil.stripExtensions(f.getName());
                    if (!Files.isReadable(f.toPath()) || !Files.isRegularFile(f.toPath())) {
                        logger.trace("Skipping unreadable config file " + f);
                        return;
                    }

                    // Add with file reference; format will be lazy-loaded when needed
                    // (this ensures that any other referenced format will be known about when a format needs it)
                    add(new InputFormatInfoWithConfigLazy(formatIdentifier, f));
                }
            }
        };

        // Run the configLocator on the directory - not recursive
        for (File dir : dirs) {
            if (Files.isReadable(dir.toPath()) && Files.isDirectory(dir.toPath())) {
                try {
                    FileUtil.processTree(dir, "*", false, configLocator);
                } catch (IOException e) {
                    throw new InvalidInputFormatConfig(e);
                }
            }
        }
    }
}
