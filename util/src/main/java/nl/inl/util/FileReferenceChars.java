package nl.inl.util;

import java.io.BufferedReader;
import java.io.CharArrayReader;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.input.ReaderInputStream;

import nl.inl.blacklab.exceptions.ErrorIndexingFile;

public class FileReferenceChars implements FileReference {

    String path;

    char[] contents;

    File assocFile;

    FileReferenceChars(String path, char[] contents, File assocFile) {
        this.path = path;
        this.contents = contents;
        this.assocFile = assocFile;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public byte[] getBytes() {
        // Replaces less efficient version:
        // return new String(contents).getBytes(StandardCharsets.UTF_8);
        try {
            CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder();
            ByteBuffer byteBuffer = encoder.encode(CharBuffer.wrap(contents));
            return byteBuffer.array();
        } catch (CharacterCodingException e) {
            throw new ErrorIndexingFile(e);
        }
    }

    @Override
    public FileReference withCreateReader() {
        return this;
    }

    @Override
    public File getAssociatedFile() {
        return assocFile;
    }

    @Override
    public Charset getCharSet() {
        return StandardCharsets.UTF_8;
    }

    @Override
    public InputStream getSinglePassInputStream() {
        return new ReaderInputStream(getSinglePassReader(), StandardCharsets.UTF_8);
    }

    @Override
    public BufferedReader getSinglePassReader() {
        return createReader();
    }

    @Override
    public BufferedReader createReader(Charset charset) {
        return new BufferedReader(new CharArrayReader(contents));
    }

    @Override
    public boolean hasGetTextContent() {
        return true;
    }

    @Override
    public TextContent getTextContent(long startOffset, long endOffset) {
        if (endOffset < 0)
            endOffset = contents.length;
        return TextContent.from(contents, (int)startOffset, (int)(endOffset - startOffset));
    }
}
