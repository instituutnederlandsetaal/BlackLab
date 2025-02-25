package nl.inl.util;

import org.junit.Assert;
import org.junit.Test;

public class TestStringUtil {

    private static final char CHAR_COMBINING_ACCENT_ACUTE = '\u0301';

    @Test
    public void testRemoveAccents() {
        Assert.assertEquals("He, jij!", StringUtil.stripAccents("Hé, jij!"));
    }

    @Test
    public void testEscapeLuceneRegexCharacters() {
        Assert.assertEquals("^the\\*\\.quick\\?brown\\(fox\\)jumps\\@over\\#the\\&lazy$", StringUtil.escapeLuceneRegexCharacters("^the*.quick?brown(fox)jumps@over#the&lazy$"));
        String charsToEscape = "|\\?*+()<[]{}.\"#@&";
        for (int i = 0; i < charsToEscape.length(); i++) {
            char c = charsToEscape.charAt(i);
            Assert.assertEquals("test\\" + c + "test", StringUtil.escapeLuceneRegexCharacters("test" + c + "test"));
        }
    }

    @Test
    public void testSanitizeAndNormalizeUnicode() {
        // Sanitize
        Assert.assertEquals("", StringUtil.sanitizeAndNormalizeUnicode("" + StringUtil.CHAR_ZERO_WIDTH_SPACE + StringUtil.CHAR_SOFT_HYPHEN));

        // Normalize
        Assert.assertEquals("é", StringUtil.sanitizeAndNormalizeUnicode("é"));
        Assert.assertEquals("é", StringUtil.sanitizeAndNormalizeUnicode("e" + CHAR_COMBINING_ACCENT_ACUTE));
    }

    @Test
    public void testNormalizeWhitespace() {
        Assert.assertEquals(" ", StringUtil.normalizeWhitespace("\n "));
        Assert.assertEquals(" ", StringUtil.normalizeWhitespace(" \n \n "));
        Assert.assertEquals(" ", StringUtil.normalizeWhitespace("" + StringUtil.CHAR_EM_SPACE + StringUtil.CHAR_NON_BREAKING_SPACE + ""));
    }

    @Test
    public void testTrimWhitespace() {
        Assert.assertEquals("trim", StringUtil.trimWhitespace("trim "));
        Assert.assertEquals("trim", StringUtil.trimWhitespace("trim" + StringUtil.CHAR_EM_SPACE + StringUtil.CHAR_NON_BREAKING_SPACE));
        Assert.assertEquals("trim", StringUtil.trimWhitespace("" + StringUtil.CHAR_EM_SPACE + StringUtil.CHAR_NON_BREAKING_SPACE + "trim"));
        Assert.assertEquals("tr  im", StringUtil.trimWhitespace(" tr  im "));
    }

    @Test
    public void testEscapeQuote2() {
        String escaped = "\\\\\\\"";  // user entered \\\"
        String unescaped = "\\\\\"";  // only quote is unescaped
        Assert.assertEquals(unescaped, StringUtil.unescapeQuote(escaped, "\""));
        Assert.assertEquals(escaped, StringUtil.escapeQuote(unescaped, "\""));
    }

    @Test
    public void testEscapeQuote() {
        // Escape the correct quote
        Assert.assertEquals("test'\\\"test", StringUtil.escapeQuote("test'\"test", "\""));
        Assert.assertEquals("test\\'\"test", StringUtil.escapeQuote("test'\"test", "'"));

        // Don't do anything to non-quote and non-backslash, whether they're already escaped or not
        Assert.assertEquals("test\\s\\n\\test", StringUtil.escapeQuote("test\\s\\n\\test", "\""));
        Assert.assertEquals("test\\s\\n\\test", StringUtil.escapeQuote("test\\s\\n\\test", "'"));

        // Double-escape if you have to
        Assert.assertEquals("test\\\\\"test", StringUtil.escapeQuote("test\\\"test", "\""));
        Assert.assertEquals("test\\\\'test", StringUtil.escapeQuote("test\\'test", "'"));

        // Let's combine a bunch of stuff
        String escaped   = "bla\\\\\\\"\\'\\\\\\s\\n\\\"\\'bla\\";
        String unescapedDouble = "bla\\\\\"\\'\\\\\\s\\n\"\\'bla\\";
        Assert.assertEquals(unescapedDouble, StringUtil.unescapeQuote(escaped, "\""));
        Assert.assertEquals(escaped, StringUtil.escapeQuote(unescapedDouble, "\""));
        String unescapedSingle = "bla\\\\\\\"'\\\\\\s\\n\\\"'bla\\";
        Assert.assertEquals(unescapedSingle, StringUtil.unescapeQuote(escaped, "'"));
        Assert.assertEquals(escaped, StringUtil.escapeQuote(unescapedSingle, "'"));

        // Test roundtrip as well
        Assert.assertEquals(unescapedDouble, StringUtil.unescapeQuote(StringUtil.escapeQuote(unescapedDouble, "\""), "\""));
        Assert.assertEquals(escaped, StringUtil.escapeQuote(StringUtil.unescapeQuote(escaped, "\""), "\""));
        Assert.assertEquals(unescapedSingle, StringUtil.unescapeQuote(StringUtil.escapeQuote(unescapedSingle, "'"), "'"));
        Assert.assertEquals(escaped, StringUtil.escapeQuote(StringUtil.unescapeQuote(escaped, "'"), "'"));
    }

    @Test
    public void testUnescapeQuote() {
        // Don't trip over quotes that are not escaped
        Assert.assertEquals("test'\"test", StringUtil.unescapeQuote("test'\"test", "\""));
        Assert.assertEquals("test'\"test", StringUtil.unescapeQuote("test'\"test", "'"));

        // Don't unescape non-quote and non-backslash
        Assert.assertEquals("test\\s\\n\\test", StringUtil.unescapeQuote("test\\s\\n\\test", "\""));
        Assert.assertEquals("test\\s\\n\\test", StringUtil.unescapeQuote("test\\s\\n\\test", "'"));

        // Do unescape
        Assert.assertEquals("test\"test", StringUtil.unescapeQuote("test\\\"test", "\""));
        Assert.assertEquals("test'test", StringUtil.unescapeQuote("test\\'test", "'"));

        // Don't unescape other quote type
        Assert.assertEquals("test\\\"test", StringUtil.unescapeQuote("test\\\"test", "'"));
        Assert.assertEquals("test\\'test", StringUtil.unescapeQuote("test\\'test", "\""));

        // Don't get confused by multiple backslashes
        Assert.assertEquals("test\\\\test", StringUtil.unescapeQuote("test\\\\test", "\""));
        Assert.assertEquals("test\\\\\"test", StringUtil.unescapeQuote("test\\\\\\\"test", "\""));
        Assert.assertEquals("test\\\\\"test", StringUtil.unescapeQuote("test\\\\\"test", "\""));
    }
}
