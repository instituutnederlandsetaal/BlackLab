package nl.inl.util;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;

import org.apache.commons.io.input.BOMInputStream;

public class FileReferenceInputStream implements FileReference {

    String path;

    BOMInputStream is;

    File assocFile;

    FileReferenceInputStream(String path, InputStream is, File assocFile) {
        this.path = path;
        this.is = UnicodeStream.wrap(is);
        this.assocFile = assocFile;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public FileReference withCreateReader() {
        // NOTE: This only works if you haven't read from the InputStream yet!
        return FileReference.readIntoMemoryFromTextualInputStream(path, is, assocFile);
    }

    @Override
    public InputStream getSinglePassInputStream() {
        return is;
    }

    @Override
    public File getAssociatedFile() {
        return assocFile;
    }

    @Override
    public Charset getCharSet() {
        return UnicodeStream.getCharset(is);
    }
}
