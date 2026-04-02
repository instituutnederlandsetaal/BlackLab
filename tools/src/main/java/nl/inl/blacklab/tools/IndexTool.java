package nl.inl.blacklab.tools;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.WordUtils;
import org.apache.lucene.queryparser.classic.ParseException;

import com.fasterxml.jackson.databind.ObjectMapper;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.exceptions.DocumentFormatNotFound;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.exceptions.InvalidInputFormatConfig;
import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.index.IndexSource;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.index.InputFormatInfo;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.plugins.DocTaskType;
import nl.inl.blacklab.plugins.IndexSourceType;
import nl.inl.blacklab.plugins.PluginManager;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexWriter;
import nl.inl.util.FileUtil;
import nl.inl.util.LogUtil;
import nl.inl.util.LuceneUtil;
import nl.inl.util.fileprocessor.FileIterator;

/**
 * The indexer class and main program for the ANW corpus.
 */
public class IndexTool {

    private IndexTool() {
    }

    public static void main(String[] args) throws ErrorOpeningIndex, ParseException, IOException {
        BlackLab.setCheckCurrentDirForConfig(true);
        BlackLab.setConfigFromFile(); // read blacklab.yaml if exists and set config from that

        // Parse command line
        int maxDocsToIndex = 0;
        File indexDir = null;
        IndexSource indexSource = null; // full file path/glob, or other location to get input from
        String fileNameGlobGlobal = "*"; // test all files we encounter against this glob (--file-glob)
        String formatIdentifier = null;
        boolean forceCreateNew = false;
        String command = "";
        Set<String> commands = new HashSet<>(Arrays.asList("add", "create", "delete", "doctask", "indexinfo", "import-indexinfo"));
        boolean addingFiles = true;
        String deleteQuery = null;
        String docTaskPluginName = null;
        Map<String, String> docTaskArgs = new HashMap<>();
        int numberOfThreadsToUse = BlackLab.config().getIndexing().getNumberOfThreads();
        List<File> linkedFileDirs = new ArrayList<>();
        boolean createEmptyIndex = false;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i].trim();
            if (arg.startsWith("--")) {
                String name = arg.substring(2);
                switch (name) {
                    case "index-type" -> {
                        if (i + 1 == args.length || !List.of("integrated", "external")
                                .contains(args[i + 1].toLowerCase())) {
                            System.err.println(
                                    "--index-type only supports 'integrated' (the default); don't use this option.");
                            usage();
                            return;
                        }
                        if (args[i + 1].equalsIgnoreCase("external")) {
                            System.err.println("The 'external' index type is no longer supported.");
                            usage();
                            return;
                        }
                        i++;
                    }
                    case "create-empty" -> createEmptyIndex = true;
                    case "threads" -> {
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
                    }
                    case "nothreads" -> numberOfThreadsToUse = 1;
                    case "format-dir" -> {
                        if (i + 1 == args.length) {
                            System.err.println("--format-dir option needs argument");
                            usage();
                            return;
                        }
                        DocumentFormats.addConfigFormatsInDirectories(List.of(new File(args[i + 1])));
                        i++;
                    }
                    case "linked-file-dir" -> {
                        if (i + 1 == args.length) {
                            System.err.println("--linked-file-dir option needs argument");
                            usage();
                            return;
                        }
                        linkedFileDirs.add(new File(args[i + 1]));
                        i++;
                    }
                    case "file-glob" -> {
                        if (i + 1 == args.length) {
                            System.err.println("--file-glob option needs argument");
                            usage();
                            return;
                        }
                        fileNameGlobGlobal = args[i + 1];
                        i++;
                    }
                    case "maxdocs" -> {
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
                    }
                    case "help" -> {
                        usage();
                        return;
                    }
                    default -> {
                        System.err.println("Unknown option --" + name);
                        usage();
                        return;
                    }
                }
            } else {
                if (command.isEmpty() && commands.contains(arg)) {
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
                    indexSource = IndexSourceType.fromUri(arg);
                } else if (addingFiles && formatIdentifier == null) {
                    formatIdentifier = arg;
                } else if (command.equals("delete") && deleteQuery == null) {
                    deleteQuery = arg;
                } else if (command.equals("doctask")) {
                    if (docTaskPluginName == null)
                        docTaskPluginName = arg;
                    else {
                        if (!arg.contains("=")) {
                            System.err.println("Argument to doctask must have the form KEY=VALUE");
                            return;
                        }
                        String[] parts = arg.split("=", 2);
                        docTaskArgs.put(parts[0], parts[1]);
                    }
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
        if (formatIdentifier == null  && addingFiles) {
            System.err.println("No format identifier given.");
            usage();
            return;
        }
        if (command.isEmpty()) {
            System.err.println("No command specified; specify 'create' or 'add'. (--help for details)");
            usage();
            return;
        }
        switch (command) {
        case "add":
            break;
        case "create":
            forceCreateNew = true;
            break;
        case "delete":
            deleteDocuments(indexDir, deleteQuery);
            return;
        case "doctask":
            runDocTask(indexDir, docTaskPluginName, docTaskArgs);
            return;
        case "indexinfo":
            exportIndexInfo(indexDir);
            return;
        case "import-indexinfo":
            importIndexInfo(indexDir);
            return;
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
        indexSource.setFileIteratorSettings(new FileIterator.FileIteratorSettings(true, true,
                fileNameGlobGlobal));

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

        String op = forceCreateNew ? "Creating new" : "Appending to";
        System.out.println(op + " index in " + indexDir + File.separator + " from " + indexSource +
                " (using format " + formatIdentifier + ")");

        // Make sure BlackLab can find our format configuration files
        // (by default, it will already look in $BLACKLAB_CONFIG_DIR/formats, $HOME/.blacklab/formats
        //  and /etc/blacklab/formats, but we also want it to look in the current dir, the input dir,
        //  and the parent(s) of the input and index dirs)
        File currentWorkingDir = new File(System.getProperty("user.dir"));
        Set<File> formatDirs = new LinkedHashSet<>(Arrays.asList(currentWorkingDir));
        if (inputDirParent != null)
            formatDirs.add(inputDirParent);
        inputDir.ifPresent(formatDirs::add);
        formatDirs.add(indexDirParent);

        DocumentFormats.addConfigFormatsInDirectories(formatDirs);

        // Create the indexer and index the files
        // First check if the format is a file: if so, load it before continuing.
        if (!DocumentFormats.isSupported(formatIdentifier)) {
            File maybeFormatFile = new File(formatIdentifier);

            if (maybeFormatFile.isFile() && maybeFormatFile.canRead()) {
                if (FileUtil.isBrokenLink(maybeFormatFile)) {
                    System.err.println("Format file " + maybeFormatFile + " is a broken symlink.");
                    usage();
                    return;
                }
                try {
                    ConfigInputFormat format = ConfigInputFormat.read(maybeFormatFile);
                    DocumentFormats.add(format);
                    formatIdentifier = format.getName();
                } catch (InvalidInputFormatConfig e) {
                    System.err.println("Error(s) in format " + formatIdentifier + ": " + e.getMessage());
                    usage();
                    return;
                }
            }
        }

        Indexer indexer;
        try {
            BlackLabIndexWriter indexWriter = BlackLab.openForWriting(indexDir, forceCreateNew, formatIdentifier);
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
        if (maxDocsToIndex > 0)
            indexer.setMaxNumberOfDocsToIndex(maxDocsToIndex);
        indexer.setLinkedFileDirs(linkedFileDirs);
        try {
            if (!createEmptyIndex) {
                indexer.index(indexSource);
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

    public static final String METADATA_FILE_NAME = "indexmetadata.json";

    private static void exportIndexInfo(File indexDir) {
        try (BlackLabIndex index = BlackLab.open(indexDir)) {
            String indexmetadata = index.metadata().getIndexMetadataAsString();
            File indexMetadataFile = new File(indexDir, METADATA_FILE_NAME);
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
            File indexMetadataFile = new File(indexDir, METADATA_FILE_NAME);
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

    private static void deleteDocuments(File indexDir, String deleteQuery) throws ErrorOpeningIndex, ParseException {
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

    private static void runDocTask(File indexDir, String docTaskPluginName, Map<String, String> args) {
        if (StringUtils.isEmpty(docTaskPluginName)) {
            System.err.println("No doc task plugin name given.");
            usage();
            return;
        }
        try (BlackLabIndexWriter indexWriter = BlackLab.openForWriting(indexDir, false)) {
            DocTaskType docTaskType = PluginManager.type(DocTaskType.class).get(docTaskPluginName);
            indexWriter.forEachDocument(docTaskType.docTask(indexWriter, args));
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
                  --create-empty                 Create an empty index (ignore inputdir param)
                  --file-glob <g>                Only index files matching glob <g> (default: '*')
                  --format-dir <d>               Look in directory <d> for formats (i.e. .blf.yaml files)
                  --index-type <t>               Set the index type, integrated (new, default) or external (legacy)
                  --linked-file-dir <d>          Look in directory <d> for linked (e.g. metadata) files
                  --maxdocs <n>                  Stop after indexing <n> documents
                  --nothreads                    Disable multithreaded indexing (enabled by default)
                  --threads <n>                  Number of threads to use
                
                Available input format configurations:""");
        for (InputFormatInfo inputFormat: DocumentFormats.getFormats()) {
            String name = inputFormat.getIdentifier();
            String displayName = inputFormat.getDisplayName();
            String desc = inputFormat.getDescription();
            String url = inputFormat.getHelpUrl();
            if (!url.isEmpty())
                url = "\n      (see " + url + ")";
            if (!displayName.isEmpty())
                displayName = " (" + displayName + ")";
            if (!desc.isEmpty()) {
                desc = "\n      " + WordUtils.wrap(desc, 75, "\n      ", false);
            }
            System.out.println("  " + name + displayName + desc + url);
        }
    }

}
