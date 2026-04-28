package nl.inl.blacklab.index;

import java.io.File;
import java.util.Optional;

import nl.inl.blacklab.plugins.IndexSourceType;
import nl.inl.blacklab.plugins.param.PluginParams;
import nl.inl.util.fileprocessor.FileIterator;
import nl.inl.util.fileprocessor.FileReference;

/** A file, directory or glob to index. */
public class IndexSourceTypeFile extends IndexSourceType {

    @Override
    public String getId() {
        return "file";
    }

    @Override
    public IndexSource get(String path, PluginParams params) {
        return new IndexSourceFile(path);
    }

    public static class IndexSourceFile extends IndexSource {

        private final File inputDir;

        private final String globFilesInThisDir;

        public IndexSourceFile(String path) {
            this(new File(path));
        }

        public IndexSourceFile(File file) {
            super(file.getPath());
            if (file.isDirectory()) {
                this.inputDir = file;
                this.globFilesInThisDir = "*";
            } else {
                this.inputDir = file.getParentFile() == null ? new File(".") : file.getParentFile();
                this.globFilesInThisDir = file.getName();
            }
        }

        @Override
        public FileIterator filesToIndex() {
            File dirAndGlob = new File(inputDir, globFilesInThisDir);
            if (dirAndGlob.exists() && dirAndGlob.isFile()) {
                // Exact file exists, just index that one file
                FileReference file = FileReference.fromFile(dirAndGlob);
                return FileIterator.from(file, getFileIteratorSettings());
            }
            if (!globFilesInThisDir.contains("*") && !globFilesInThisDir.contains("?"))
                throw new IllegalArgumentException("File does not exist: " + dirAndGlob);
            // No exact file, treat as directory + glob
            return FileIterator.from(inputDir, globFilesInThisDir, getFileIteratorSettings());
        }

        @Override
        public Optional<File> getAssociatedDirectory() {
            return Optional.of(inputDir);
        }

        @Override
        public String toString() {
            return inputDir + File.separator + (globFilesInThisDir.isEmpty() || globFilesInThisDir.equals("*") ? "" :
                    globFilesInThisDir);
        }
    }
}
