package nl.inl.util;

import org.junit.Assert;
import org.junit.Test;

public class TestStringUtil {

    private static final char CHAR_COMBINING_ACCENT_ACUTE = '\u0301';

    @Test
    public void testRemoveAccents() {
        // remove accents on letters
        Assert.assertEquals("He, jij!", StringUtil.stripAccents("Hé, jij!"));
        // special case for Ł and ł
        Assert.assertEquals("Ll", StringUtil.stripAccents("Łł"));
        // remove bare accents
        // asserts that stripAccents takes index (i.e. i--) into account when removing accents.
        // If it didn't, the second accent would remain.
        final String twoBareAccentsInARow = CHAR_COMBINING_ACCENT_ACUTE + "" + CHAR_COMBINING_ACCENT_ACUTE; // force cast to string
        Assert.assertEquals("", StringUtil.stripAccents(twoBareAccentsInARow));
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
        Assert.assertEquals(unescaped, StringUtil.unescapeQuoteForLuceneRegex(escaped, "\""));
        Assert.assertEquals(escaped, StringUtil.escapeQuoteForBcql(unescaped, "\""));
    }

    @Test
    public void testEscapeQuote() {
        // Escape the correct quote
        Assert.assertEquals("test'\\\"test", StringUtil.escapeQuoteForBcql("test'\"test", "\""));
        Assert.assertEquals("test\\'\"test", StringUtil.escapeQuoteForBcql("test'\"test", "'"));

        // Don't do anything to non-quote and non-backslash, whether they're already escaped or not
        Assert.assertEquals("test\\s\\n\\test", StringUtil.escapeQuoteForBcql("test\\s\\n\\test", "\""));
        Assert.assertEquals("test\\s\\n\\test", StringUtil.escapeQuoteForBcql("test\\s\\n\\test", "'"));

        // Double-escape if you have to
        Assert.assertEquals("test\\\\\"test", StringUtil.escapeQuoteForBcql("test\\\"test", "\""));
        Assert.assertEquals("test\\\\'test", StringUtil.escapeQuoteForBcql("test\\'test", "'"));

        // Let's combine a bunch of stuff
        String escaped   = "bla\\\\\\\"\\'\\\\\\s\\n\\\"\\'bla\\";
        String unescapedDouble = "bla\\\\\"\\'\\\\\\s\\n\"\\'bla\\";
        Assert.assertEquals(unescapedDouble, StringUtil.unescapeQuoteForLuceneRegex(escaped, "\""));
        Assert.assertEquals(escaped, StringUtil.escapeQuoteForBcql(unescapedDouble, "\""));
        String unescapedSingle = "bla\\\\\\\"'\\\\\\s\\n\\\"'bla\\";
        Assert.assertEquals(unescapedSingle, StringUtil.unescapeQuoteForLuceneRegex(escaped, "'"));
        Assert.assertEquals(escaped, StringUtil.escapeQuoteForBcql(unescapedSingle, "'"));

        // Test roundtrip as well
        Assert.assertEquals(unescapedDouble, StringUtil.unescapeQuoteForLuceneRegex(StringUtil.escapeQuoteForBcql(unescapedDouble, "\""), "\""));
        Assert.assertEquals(escaped, StringUtil.escapeQuoteForBcql(StringUtil.unescapeQuoteForLuceneRegex(escaped, "\""), "\""));
        Assert.assertEquals(unescapedSingle, StringUtil.unescapeQuoteForLuceneRegex(StringUtil.escapeQuoteForBcql(unescapedSingle, "'"), "'"));
        Assert.assertEquals(escaped, StringUtil.escapeQuoteForBcql(StringUtil.unescapeQuoteForLuceneRegex(escaped, "'"), "'"));
    }

    @Test
    public void testUnescapeQuote() {
        // Don't trip over quotes that are not escaped
        Assert.assertEquals("test'\"test", StringUtil.unescapeQuoteForLuceneRegex("test'\"test", "\""));
        Assert.assertEquals("test'\"test", StringUtil.unescapeQuoteForLuceneRegex("test'\"test", "'"));

        // Don't unescape non-quote and non-backslash
        Assert.assertEquals("test\\s\\n\\test", StringUtil.unescapeQuoteForLuceneRegex("test\\s\\n\\test", "\""));
        Assert.assertEquals("test\\s\\n\\test", StringUtil.unescapeQuoteForLuceneRegex("test\\s\\n\\test", "'"));

        // Do unescape
        Assert.assertEquals("test\"test", StringUtil.unescapeQuoteForLuceneRegex("test\\\"test", "\""));
        Assert.assertEquals("test'test", StringUtil.unescapeQuoteForLuceneRegex("test\\'test", "'"));

        // Don't unescape other quote type
        Assert.assertEquals("test\\\"test", StringUtil.unescapeQuoteForLuceneRegex("test\\\"test", "'"));
        Assert.assertEquals("test\\'test", StringUtil.unescapeQuoteForLuceneRegex("test\\'test", "\""));

        // Don't get confused by multiple backslashes
        Assert.assertEquals("test\\\\test", StringUtil.unescapeQuoteForLuceneRegex("test\\\\test", "\""));
        Assert.assertEquals("test\\\\\"test", StringUtil.unescapeQuoteForLuceneRegex("test\\\\\\\"test", "\""));
        Assert.assertEquals("test\\\\\"test", StringUtil.unescapeQuoteForLuceneRegex("test\\\\\"test", "\""));
    }
}
