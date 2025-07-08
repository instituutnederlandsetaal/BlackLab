package nl.inl.blacklab.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.io.FileUtils;
import org.apache.commons.text.WordUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.queryparser.classic.ParseException;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;

import com.fasterxml.jackson.databind.ObjectMapper;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.exceptions.DocumentFormatNotFound;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.exceptions.InvalidInputFormatConfig;
import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.index.InputFormat;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndex.IndexType;
import nl.inl.blacklab.search.BlackLabIndexWriter;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataExternal;
import nl.inl.blacklab.search.indexmetadata.MetadataFieldsWriter;
import nl.inl.util.FileUtil;
import nl.inl.util.LogUtil;
import nl.inl.util.LuceneUtil;

/**
 * The indexer class and main program for the ANW corpus.
 */
public class IndexTool {

    static final Map<String, String> indexerParam = new TreeMap<>();

    static abstract class IndexSource {

        private static final Logger logger = LogManager.getLogger(IndexSource.class);

        private static final String PROTOCOL_SEPARATOR = "://";

        private static Map<String, Class<? extends IndexSource>> indexSourceTypes;

        /**
         * Find all legacy DocIndexers and store them in a map.
         * @return a map of format identifiers to DocIndexerLegacy classes
         */
        private static synchronized Map<String, Class<? extends IndexSource>> getIndexSourceTypes() {
            if (indexSourceTypes == null) {
                indexSourceTypes = new HashMap<>();
                Reflections reflections = new Reflections("", new SubTypesScanner(false));
                for (Class<? extends IndexSource> cl: reflections.getSubTypesOf(IndexSource.class)) {
                    try {
                        // Get the URI_SCHEME constant from the class
                        String scheme = (String) cl.getField("URI_SCHEME").get(null);
                        indexSourceTypes.put(scheme, cl);
                    } catch (NoSuchFieldException | IllegalAccessException e) {
                        logger.error("Could not get URI_SCHEME constant from class {}, ", cl.getName());
                        logger.error(e);
                    }
                }
            }
            return indexSourceTypes;
        }

        private static IndexSource fromUri(String uri) {
            int index = uri.indexOf(PROTOCOL_SEPARATOR);
            String scheme = index >= 0 ? uri.substring(0, index) : "";
            String path = index >= 0 ? uri.substring(index + PROTOCOL_SEPARATOR.length()) : uri;
            Class<? extends IndexSource> indexSourceClass = getIndexSourceTypes().get(scheme);
            if (indexSourceClass == null) {
                throw new IllegalArgumentException("Unknown input URI scheme: " + uri);
            }
            // Create an instance of the appropriate IndexSource subclass
            try {
                return indexSourceClass.getConstructor(String.class).newInstance(path);
            } catch (ReflectiveOperationException e) {
                throw new IllegalArgumentException("Error creating IndexSource for URI: " + uri, e);
            }
        }

        private final String uri;

        public IndexSource(String uri) {
            this.uri = uri;
        }

        public String getUri() {
            return uri;
        }

        /** Get directory associated with this IndexSource; we will search it for format files. */
        public Optional<File> getAssociatedDirectory() {
            return Optional.empty();
        }

        @Override
        public String toString() {
            return uri;
        }
    }

    static class IndexSourceFile extends IndexSource {

        public static final String URI_SCHEME = "file";

        private final File inputDir;

        private final String glob;

        public IndexSourceFile(String uri) {
            super(uri);
            File file = new File(uri);
            if (file.isDirectory()) {
                this.inputDir = file;
                this.glob = "*";
            } else {
                this.inputDir = file.getParentFile() == null ? new File(".") : file.getParentFile();
                this.glob = file.getName();
            }
        }

        public File getInputDir() {
            return inputDir;
        }

        public String getGlob() {
            return glob;
        }

        @Override
        public Optional<File> getAssociatedDirectory() {
            return Optional.of(inputDir);
        }

        @Override
        public String toString() {
            return inputDir + File.separator + (glob.isEmpty() || glob.equals("*") ? "" : glob);
        }
    }

    private IndexTool() {
    }

    public static void main(String[] args) throws ErrorOpeningIndex, ParseException, IOException {
        BlackLab.setConfigFromFile(); // read blacklab.yaml if exists and set config from that

        // If the current directory contains indexer.properties, read it
        File propFile = new File(".", "indexer.properties");
        if (propFile.canRead())
            readParametersFromPropertiesFile(propFile);

        // Parse command line
        int maxDocsToIndex = 0;
        File indexDir = null;
        IndexSource indexSource = null; // full file path, or other location to get input from
        String formatIdentifier = null;
        boolean forceCreateNew = false;
        String command = "";
        Set<String> commands = new HashSet<>(Arrays.asList("add", "create", "delete", "indexinfo", "import-indexinfo"));
        boolean addingFiles = true;
        String deleteQuery = null;
        int numberOfThreadsToUse = BlackLab.config().getIndexing().getNumberOfThreads();
        List<File> linkedFileDirs = new ArrayList<>();
        IndexType indexType = null; // null means "use default"
        boolean createEmptyIndex = false;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i].trim();
            if (arg.startsWith("---")) {
                String name = arg.substring(3);
                if (i + 1 == args.length) {
                    System.err.println("Passing parameter to indexer: argument needed!");
                    usage();
                    return;
                }
                i++;
                String value = args[i];
                indexerParam.put(name, value);
            } else if (arg.startsWith("--")) {
                String name = arg.substring(2);
                switch (name) {
                case "index-type":
                    if (i + 1 == args.length || !List.of("integrated", "external").contains(args[i + 1].toLowerCase())) {
                        System.err.println("--index-type needs a parameter: integrated (the default) or external (legacy index type).");
                        usage();
                        return;
                    }
                    indexType = args[i + 1].equalsIgnoreCase("integrated") ? IndexType.INTEGRATED : IndexType.EXTERNAL_FILES;
                    i++;
                    break;
                case "integrate-external-files":
                    // NOTE: deprecated; this is the default (or use  --index-type external  to use the legacy variant)
                    if (i + 1 == args.length || !List.of("true", "false").contains(args[i + 1].toLowerCase())) {
                        System.err.println("--integrate-external-files needs a parameter: true or false.");
                        usage();
                        return;
                    }
                    indexType = Boolean.parseBoolean(args[i + 1]) ? IndexType.INTEGRATED : IndexType.EXTERNAL_FILES;
                    i++;
                    break;
                case "create-empty":
                    createEmptyIndex = true;
                    break;
                case "threads":
                    if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                        try {
                            numberOfThreadsToUse = Integer.parseInt(args[i + 1]);
                            i++;
                        } catch (NumberFormatException e) {
                            System.err.println("Specify a valid integer for --threads option. Using default of 2.");
                            numberOfThreadsToUse = 2;
                        }
                    } else
                        numberOfThreadsToUse = 2;
                    break;
                case "nothreads":
                    numberOfThreadsToUse = 1;
                    break;
                case "format-dir":
                    if (i + 1 == args.length) {
                        System.err.println("--format-dir option needs argument");
                        usage();
                        return;
                    }
                    DocumentFormats.addConfigFormatsInDirectories(List.of(new File(args[i + 1])));
                    i++;
                    break;
                case "linked-file-dir":
                    if (i + 1 == args.length) {
                        System.err.println("--linked-file-dir option needs argument");
                        usage();
                        return;
                    }
                    linkedFileDirs.add(new File(args[i + 1]));
                    i++;
                    break;
                case "maxdocs":
                    if (i + 1 == args.length) {
                        System.err.println("--maxdocs option needs argument");
                        usage();
                        return;
                    }
                    try {
                        maxDocsToIndex = Integer.parseInt(args[i + 1]);
                        i++;
                    } catch (NumberFormatException e) {
                        System.err.println("--maxdocs option needs integer argument");
                        usage();
                        return;
                    }
                    break;
                case "create":
                    System.err.println("Option --create is deprecated; use create command (--help for details)");
                    forceCreateNew = true;
                    break;
                case "indexparam":
                    if (i + 1 == args.length) {
                        System.err.println("--indexparam option needs argument");
                        usage();
                        return;
                    }
                    propFile = new File(args[i + 1]);
                    if (!propFile.canRead()) {
                        System.err.println("Cannot read " + propFile);
                        usage();
                        return;
                    }
                    readParametersFromPropertiesFile(propFile);
                    i++;
                    break;
                case "help":
                    usage();
                    return;
                default: {
                    System.err.println("Unknown option --" + name);
                    usage();
                    return;
                }
                }
            } else {
                if (command.length() == 0 && commands.contains(arg)) {
                    command = arg;
                    addingFiles = command.equals("add") || command.equals("create");
                } else if (indexDir == null) {
                    indexDir = new File(arg);
                } else if (addingFiles && indexSource == null) {
                    if (arg.startsWith("\"") && arg.endsWith("\"")) {
                        // Trim off extra quotes needed to pass file glob to
                        // Windows JVM.
                        arg = arg.substring(1, arg.length() - 1);
                    }
                    indexSource = IndexSource.fromUri(arg);
                } else if (addingFiles && formatIdentifier == null) {
                    formatIdentifier = arg;
                } else if (command.equals("delete") && deleteQuery == null) {
                    deleteQuery = arg;
                } else {
                    System.err.println("Too many arguments!");
                    usage();
                    return;
                }
            }
        }
        if (indexDir == null) {
            System.err.println("No index dir given.");
            usage();
            return;
        }

        // Check the command
        if (command.isEmpty()) {
            System.err.println("No command specified; specify 'create' or 'add'. (--help for details)");
            usage();
            return;
        }
        switch (command) {
        case "indexinfo":
            exportIndexInfo(indexDir);
            return;
        case "import-indexinfo":
            importIndexInfo(indexDir);
            return;
        case "delete":
            commandDelete(indexDir, deleteQuery);
            return;
        case "create":
            forceCreateNew = true;
            break;
        case "add":
            break;
        default:
            System.err.println("Unknown command: " + command + ". (--help for details)");
            usage();
            return;
        }

        // We're adding files. Do we have an input dir/file and file format name?
        if (indexSource == null) {
            System.err.println("No input dir given.");
            usage();
            return;
        }

        // Init log4j
        LogUtil.setupBasicLoggingConfig();

        List<File> dirs = new ArrayList<>(List.of(new File(".")));
        Optional<File> inputDir = indexSource.getAssociatedDirectory();
        File inputDirParent = null;
        if (inputDir.isPresent()) {
            dirs.add(inputDir.get());
            inputDirParent = inputDir.get().getAbsoluteFile().getParentFile();
        }
        if (inputDirParent != null)
            dirs.add(inputDirParent);
        dirs.add(indexDir);
        File indexDirParent = indexDir.getAbsoluteFile().getParentFile();
        if (indexDirParent != null)
            dirs.add(indexDirParent);

        propFile = FileUtil.findFile(dirs, "indexer", List.of("properties"));
        if (propFile != null && propFile.canRead())
            readParametersFromPropertiesFile(propFile);
        File indexTemplateFile = null;
        if (forceCreateNew) {
            indexTemplateFile = FileUtil.findFile(dirs, "indextemplate", Arrays.asList("json", "yaml", "yml"));
        }

        String op = forceCreateNew ? "Creating new" : "Appending to";
        System.out.println(op + " index in " + indexDir + File.separator + " from " + indexSource +
                (formatIdentifier != null ? " (using format " + formatIdentifier + ")" : "(using autodetected format)"));
        if (!indexerParam.isEmpty()) {
            System.out.println("Indexer parameters:");
            for (Map.Entry<String, String> e : indexerParam.entrySet()) {
                System.out.println("  " + e.getKey() + ": " + e.getValue());
            }
        }

        // Make sure BlackLab can find our format configuration files
        // (by default, it will already look in $BLACKLAB_CONFIG_DIR/formats, $HOME/.blacklab/formats
        //  and /etc/blacklab/formats, but we also want it to look in the current dir, the input dir,
        //  and the parent(s) of the input and index dirs)
        File currentWorkingDir = new File(System.getProperty("user.dir"));
        Set<File> formatDirs = new LinkedHashSet<>(Arrays.asList(currentWorkingDir, inputDirParent));
        inputDir.ifPresent(formatDirs::add);
        if (indexDirParent != null)
            formatDirs.add(indexDirParent);
        DocumentFormats.addConfigFormatsInDirectories(formatDirs);

        // Create the indexer and index the files
        if (!forceCreateNew || indexTemplateFile == null || !indexTemplateFile.canRead()) {
            indexTemplateFile = null;
        }
        // First check if the format is a file: if so, load it before continuing.
        if (formatIdentifier != null && !DocumentFormats.isSupported(formatIdentifier)) {
            File maybeFormatFile = new File(formatIdentifier);
            if (maybeFormatFile.isFile() && maybeFormatFile.canRead()) {
                try {
                    ConfigInputFormat format = new ConfigInputFormat(maybeFormatFile);
                    DocumentFormats.add(format);
                    formatIdentifier = format.getName();
                } catch (IOException e) {
                    System.err.println("Not a format, not a valid file: " + formatIdentifier + " . " + e.getMessage());
                    System.err.println("Please specify a correct format on the command line.");
                    usage();
                    return;
                }
            }
        }

        Indexer indexer;
        try {
            BlackLabIndexWriter indexWriter = BlackLab.openForWriting(indexDir, forceCreateNew,
                    formatIdentifier, indexTemplateFile, indexType);
            indexer = Indexer.create(indexWriter, formatIdentifier);
        } catch (InvalidInputFormatConfig e) {
            System.err.println("ERROR in input format '" + formatIdentifier + "':");
            System.err.println(e.getMessage());
            return;
        } catch (DocumentFormatNotFound e) {
            System.err.println(e.getMessage());
            usage();
            return;
        }

        indexer.setNumberOfThreadsToUse(numberOfThreadsToUse);
        if (forceCreateNew)
            indexer.indexWriter().metadata().setDocumentFormat(formatIdentifier);
        indexer.setIndexerParam(indexerParam);
        if (maxDocsToIndex > 0)
            indexer.setMaxNumberOfDocsToIndex(maxDocsToIndex);
        indexer.setLinkedFileDirs(linkedFileDirs);
        try {
            if (!createEmptyIndex) {
                indexer.index
                if (inputGlob.contains("*") || inputGlob.contains("?")) {
                    // Real wildcard glob
                    indexer.index(inputDir, inputGlob);
                } else {
                    // Single file.
                    indexer.index(new File(inputDir, inputGlob));
                    MetadataFieldsWriter mf = indexer.indexWriter().metadata().metadataFields();
                }
            }
        } catch (Exception e) {
            System.err.println(
                    "An error occurred, aborting indexing (changes will be rolled back). Error details follow.");
            e.printStackTrace();
            indexer.rollback();
        } finally {
            System.out.println("Saving index, please wait...");
            // Close the index.
            indexer.close();
            System.out.println("Finished!");
        }
    }

    private static void readParametersFromPropertiesFile(File propFile) {
        Properties p = readPropertiesFromFile(propFile);
        for (Map.Entry<Object, Object> e : p.entrySet()) {
            indexerParam.put(e.getKey().toString(), e.getValue().toString());
        }
    }

    private static void exportIndexInfo(File indexDir) {
        try (BlackLabIndex index = BlackLab.open(indexDir)) {
            String indexmetadata = index.metadata().getIndexMetadataAsString();
            File indexMetadataFile = new File(indexDir, IndexMetadataExternal.METADATA_FILE_NAME + ".json");
            System.out.println("Writing " + indexMetadataFile);
            FileUtils.write(indexMetadataFile, indexmetadata, StandardCharsets.UTF_8);

            String indexInfo =
                    "documentCount: " + index.metadata().documentCount() + "\n" +
                    "tokenCount: " + index.metadata().tokenCount() + "\n"; // TODO: per field
            File indexInfoFile = new File(indexDir, "indexinfo.yaml");
            System.out.println("Writing " + indexInfoFile);
            FileUtils.write(indexInfoFile, indexInfo, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    private static void importIndexInfo(File indexDir) {
        try (BlackLabIndexWriter index = BlackLab.openForWriting(indexDir, false)) {
            File indexMetadataFile = new File(indexDir, IndexMetadataExternal.METADATA_FILE_NAME + ".json");
            System.out.println("Reading " + indexMetadataFile);
            String indexmetadata = FileUtils.readFileToString(indexMetadataFile, StandardCharsets.UTF_8);

            try {
                // Check that indexmetadata is valid JSON using FasterXML Jackson's JSON parser
                // (this will throw an exception if it's not valid JSON)
                new ObjectMapper().readTree(indexmetadata);
            } catch (Exception e) {
                throw new InvalidIndex("Invalid JSON in " + indexMetadataFile + ": " + e.getMessage(), e);
            }

            index.metadata().setIndexMetadataFromString(indexmetadata);
        } catch (Exception e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    private static void commandDelete(File indexDir, String deleteQuery) throws ErrorOpeningIndex, ParseException {
        if (deleteQuery == null) {
            System.err.println("No delete query given.");
            usage();
            return;
        }
        try (BlackLabIndexWriter indexWriter = BlackLab.openForWriting(indexDir, false)) {
            System.out.println("Doing delete: " + deleteQuery);
            indexWriter.delete(LuceneUtil.parseLuceneQuery(null, deleteQuery, indexWriter.analyzer(), "nonExistentDefaultField"));
        }
    }

    private static void usage() {
        System.err.flush();
        System.out.flush();
        System.out.println("""
                Usage:
                  IndexTool {add|create} [options] <indexdir> <inputdir> <format>
                  IndexTool delete <indexdir> <filterQuery>
                  IndexTool indexinfo <indexdir>         # export indexmetadata.json from index
                  IndexTool import-indexinfo <indexdir>  # imports indexmetadata.json into index
                
                Options:
                  --maxdocs <n>                  Stop after indexing <n> documents
                  --linked-file-dir <d>          Look in directory <d> for linked (e.g. metadata) files
                  --format-dir <d>               Look in directory <d> for formats (i.e. .blf.yaml files)
                  --nothreads                    Disable multithreaded indexing (enabled by default)
                  --threads <n>                  Number of threads to use
                  --index-type <t>               Set the index type, integrated (new, default) or external (legacy)
                  --create-empty                 Create an empty index (ignore inputdir param)
                
                Available input format configurations:""");
        for (InputFormat inputFormat: DocumentFormats.getFormats()) {
            String name = inputFormat.getIdentifier();
            String displayName = inputFormat.getDisplayName();
            String desc = inputFormat.getDescription();
            String url = inputFormat.getHelpUrl();
            if (!url.isEmpty())
                url = "\n      (see " + url + ")";
            if (displayName.length() > 0)
                displayName = " (" + displayName + ")";
            if (desc.length() > 0) {
                desc = "\n      " + WordUtils.wrap(desc, 75, "\n      ", false);
            }
            System.out.println("  " + name + displayName + desc + url);
        }
    }

    /**
     * Read Properties from the specified file
     *
     * @param file the file to read
     * @return the Properties read
     */
    public static Properties readPropertiesFromFile(File file) {
        try {
            if (!file.isFile()) {
                throw new IllegalArgumentException("Annotation file " + file.getCanonicalPath()
                        + " does not exist or is not a regular file!");
            }

            try (Reader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.ISO_8859_1))) {
                Properties properties = new Properties();
                properties.load(in);
                return properties;
            }
        } catch (Exception e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }
}
