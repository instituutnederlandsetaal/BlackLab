package nl.inl.util;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.input.BOMInputStream;

public class FileReferenceBytes implements FileReference {

    String path;

    byte[] contents;

    File assocFile;

    Charset charset;

    FileReferenceBytes(String path, byte[] contents, File assocFile) {
        this.path = path;
        this.contents = contents;
        this.assocFile = assocFile;
    }

    FileReferenceBytes(String path, byte[] contents, Charset charset) {
        this.path = path;
        this.contents = contents;
        this.charset = charset;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public FileReference withCreateReader() {
        return this;
    }

    @Override
    public InputStream getSinglePassInputStream() {
        return new ByteArrayInputStream(contents);
    }

    @Override
    public BufferedReader createReader(Charset overrideEncoding) {
        if (overrideEncoding == null)
            overrideEncoding = getCharSet();
        return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(contents), overrideEncoding));
    }

    @Override
    public File getAssociatedFile() {
        return assocFile;
    }

    @Override
    public Charset getCharSet() {
        if (charset == null) {
            // Check the contents for a BOM
            BOMInputStream str = new BOMInputStream(new ByteArrayInputStream(contents));
            try {
                String name = str.getBOMCharsetName();
                charset = name == null ? StandardCharsets.UTF_8 : Charset.forName(name);
            } catch (IOException e) {
                charset = StandardCharsets.UTF_8;
            }
        }
        return charset;
    }
}
