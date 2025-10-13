package nl.inl.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.input.BOMInputStream;

/**
 * InputStream that detects and skips BOM in UTF-8 (and other Unicode encodings)
 * files.
 *
 * Taken from
 * http://stackoverflow.com/questions/1835430/byte-order-mark-screws-up-file-reading-in-java
 * which was adapted from Google Data API Client library (Apache License).
 *
 *
 * TODO: @@@ replace with BOMInputStream from Apache Commons IO...?
 *
 */
public class UnicodeStream {

    private UnicodeStream() {
    }

    public static BOMInputStream wrap(InputStream is) {
        if (is instanceof BOMInputStream) {
            return (BOMInputStream) is;
        }
        return new BOMInputStream(is);
    }

    public static Charset getCharset(BOMInputStream is) {
        return getCharset(is, StandardCharsets.UTF_8);
    }

    public static Charset getCharset(BOMInputStream is, Charset defaultEncoding) {
        String name = null;
        try {
            name = is.getBOMCharsetName();
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
        return name == null ? defaultEncoding : Charset.forName(name);
    }

}
