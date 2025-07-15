package nl.inl.blacklab.index;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
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

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.exceptions.InvalidInputFormatConfig;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.indexers.config.InputFormatReader;
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

    /** Class finder, so we have convenient direct access to it. */
    private static final FinderInputFormatClass finderClass;

    /** All currently loaded inputFormats. Always check isError() to make sure the format loaded correctly. */
    private static final Map<String, InputFormat> inputFormats = new LinkedHashMap<>();

    static {
        // NOTE: Last added finder has priority (to allow users to override types)
        finderClass = new FinderInputFormatClass();
        addFinder(finderClass);

        // We also add these in a specific order, so that format files in the default directories
        // may override builtin formats.
        addLegacyDocIndexers();
        addStandardConfigFormats(); // load formats built in to BL and from standard directories
        addConfigFormatsInDefaultDirectories();
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

    public static synchronized InputFormat add(InputFormat inputFormat) {
        String formatIdentifier = inputFormat.getIdentifier();
        if (inputFormats.containsKey(formatIdentifier)) {
            logger.info("Loading " + inputFormat + " overrode previously loaded format with this name");
        }
        inputFormats.put(formatIdentifier, inputFormat);
        return inputFormat;
    }

    public static InputFormat add(String formatIdentifier, Class<? extends DocIndexerLegacy> docIndexerClass) {
        return add(new InputFormatClass(formatIdentifier, docIndexerClass));
    }

    public static InputFormat add(ConfigInputFormat format) throws InvalidInputFormatConfig {
        format.validate();
        return add(new InputFormatWithConfig(format));
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
    public static synchronized Collection<InputFormat> getFormats() {
        // TODO maybe we should have a well-defined order? alphabetical, or configs before classes?
        List<InputFormat> reversed = inputFormats.values().stream()
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
    public static Optional<InputFormat> getFormat(String formatIdentifier) {
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
    public static synchronized Optional<InputFormat> getFormatOrError(String formatIdentifier) {
        assert formatIdentifier != null;
        InputFormat format = inputFormats.get(formatIdentifier);
        if (format == null)
            format = find(formatIdentifier);
        return Optional.ofNullable(format);
    }

    private static synchronized InputFormat find(String formatIdentifier) {
        for (FinderInputFormat finder: finders) {
            InputFormat format = finder.find(formatIdentifier);
            if (format != null) {
                inputFormats.put(formatIdentifier, format);
                return format;
            }
        }
        return null;
    }

    private static void addLegacyDocIndexers() {
        try {
            // If the legacy docindexers JAR is included on the classpath, register them
            Class<?> cls = Class.forName("nl.inl.blacklab.index.LegaDocIndexerRegisterer");
            Method m = cls.getMethod("register");
            m.invoke(null);
        } catch (ClassNotFoundException e) {
            // OK, JAR not on classpath
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException |
                 InvocationTargetException e) {
            throw new IllegalStateException("Unable to register legacy doc indexers", e);
        }
    }

    private static void addStandardConfigFormats() {
        // Note that these names should not collide with the abbreviations used by DocIndexerFactoryClass,
        // or this will override those classes.
        String[] formats = {
                "chat", "cmdi", "conll-u", "csv",
                "eaf", "folia", "naf", "sketch-wpl",
                "tcf", "tei-p5", "tei-p4-legacy",
                "tei-p5-legacy", "testformat", "tsv-frog",
                "tsv", "txt" };
        for (String formatIdentifier : formats) {
            String fileNameRelative = "formats/" + formatIdentifier + ".blf.yaml";
            try (InputStream is = DocumentFormats.class.getClassLoader()
                    .getResourceAsStream(fileNameRelative)) {
                if (is == null)
                    continue; // not found

                try (Reader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    ConfigInputFormat format = new ConfigInputFormat(formatIdentifier);

                    format.setReadFromFile(new File("$BLACKLAB_JAR/" + fileNameRelative));
                    InputFormatReader.read(reader, false, format);
                    add(format);
                }
            } catch (InvalidInputFormatConfig | IOException e) {
                //formatErrors.put(formatIdentifier, e.getMessage());
                InputFormat inputFormat = new InputFormatError(formatIdentifier, e.getMessage());
                add(inputFormat);
                throw BlackLabException.wrapRuntime(e);
            }
        }
    }

    private static void addConfigFormatsInDefaultDirectories() {
        List<File> dirs = BlackLab.defaultConfigDirs().stream()
                .filter(f -> {
                    boolean canAccess;
                    try {
                        canAccess = f.canRead();
                    } catch (SecurityException e) {
                        canAccess = false;
                    }
                    if (!canAccess)
                        logger.warn("Config directory is not readable, skipping: {}", f);
                    return canAccess;
                })
                .map(dir -> new File(dir, "formats"))
                .toList();
        addConfigFormatsInDirectories(dirs);
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
                if (f.getName().matches("^[\\-\\w]+\\.blf\\.(ya?ml|json)$")) {
                    String formatIdentifier = ConfigInputFormat.stripExtensions(f.getName());
                    if (!Files.isReadable(f.toPath()) || !Files.isRegularFile(f.toPath())) {
                        logger.trace("Skipping unreadable config file " + f);
                        return;
                    }

                    // Add with file reference; format will be lazy-loaded when needed
                    // (this ensures that baseFormats will be known about when a format needs it)
                    add(new InputFormatWithConfig(formatIdentifier, f));
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
