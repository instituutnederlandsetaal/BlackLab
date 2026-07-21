package nl.inl.blacklab.index;

import java.io.File;
import java.util.Optional;

import nl.inl.blacklab.plugins.IndexSourceType;
import nl.inl.blacklab.plugins.param.PluginParams;
import nl.inl.util.fileprocessor.FileIterator;
import nl.inl.util.fileprocessor.FileReference;

/** A single file inside an archive. Mostly used for linked metadata. */
public class IndexSourceTypeFileInArchive extends IndexSourceType {

    @Override
    public String getName() {
        return "archive";
    }

    @Override
    public IndexSource get(String path, PluginParams params) {
        return new IndexSourceFileInArchive(path);
    }

    protected static class IndexSourceFileInArchive extends IndexSource {

        private File archiveFile;

        private String fileInsideArchive;

        public IndexSourceFileInArchive(String path) {
            super(path);
            File file = new File(path);
            archiveFile = file.getParentFile();
            fileInsideArchive = file.getName();
            while (archiveFile != null && !archiveFile.exists()) {
                fileInsideArchive = archiveFile.getName() + File.separator + fileInsideArchive;
                archiveFile = archiveFile.getParentFile();
            }
            if (archiveFile.isDirectory())
                throw new IllegalArgumentException("No archive file found in path: " + path);
        }

        @Override
        public FileIterator filesToIndex() {
            return FileIterator.archiveFile(FileReference.fromFile(archiveFile), fileInsideArchive);
        }

        @Override
        public Optional<File> getAssociatedDirectory() {
            return Optional.of(archiveFile.getParentFile());
        }

        @Override
        public String toString() {
            return archiveFile + File.separator + fileInsideArchive;
        }
    }
}
