package nl.inl.blacklab.index;

import java.io.File;
import java.util.Optional;

import nl.inl.util.FileProcessor;

/** A file, directory or glob to index. */
public class IndexSourceFile extends IndexSource {

    @SuppressWarnings("unused") // Used by reflection to find this class
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

    @Override
    public void index(Indexer indexer) {
        if (glob.contains("*") || glob.contains("?")) {
            // Real wildcard glob
            try (FileProcessor proc = indexer.createFileProcessor()) {
                proc.setFileHandler(new FileHandlerDocIndexer(indexer));
                proc.setFileNameGlob(glob);
                proc.processFileOrDirectory(inputDir);
            }
        } else {
            // Single file.
            indexer.index(new File(inputDir, glob));
        }
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
