package nl.inl.blacklab.forwardindex;

import java.util.Locale;

import org.junit.Assert;
import org.junit.Test;

import com.ibm.icu.text.Collator;

import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.util.StringUtil;

public class TestCollators {

    private static Collator getDefaultEnglishCollator() {
        return Collator.getInstance(new Locale("en", "GB"));
    }

    private static String describeChar(char c) {
        return "'" + c + "' (" + (int) c + ", " + String.format("\\u%04X", (int) c) + ")";
    }

    private static Collator getCollator(boolean sensitive) {
        Collator coll = getDefaultEnglishCollator();
        Collators colls = new Collators(coll);
        return colls.get(sensitive ? MatchSensitivity.SENSITIVE : MatchSensitivity.INSENSITIVE);
    }

    private void testInsensitiveCollatorIgnoresChars(char... chars) {
        for (char c: chars) {
            String testValue = c + "te" + c + "st" + c;
            Assert.assertEquals("insensitive collator should ignore char " + describeChar(c), 0,
                    getCollator(false).compare(testValue, "t" + c + "est"));
            String desensitized = MatchSensitivity.INSENSITIVE.desensitize(StringUtil.sanitizeAndNormalizeUnicode(testValue));
            Assert.assertEquals("desensitize() should remove char " + describeChar(c), desensitized, "test");
        }
    }

    private void testInsensitiveCollatorComparesChar(char... chars) {
        for (char c: chars) {
            String testValue = c + "te" + c + "st" + c;
            Assert.assertNotEquals("insensitive collator should compare char " + describeChar(c), 0,
                    getCollator(false).compare(testValue, "test"));
            Assert.assertEquals("desensitize() should not remove char " + describeChar(c),
                    MatchSensitivity.INSENSITIVE.desensitize(testValue), StringUtil.stripAccents(testValue.toLowerCase()));
        }
    }

    @Test
    public void testInsensitiveCompare() {
        testInsensitiveCollatorIgnoresChars(StringUtil.CHAR_SOFT_HYPHEN);
        testInsensitiveCollatorComparesChar(StringUtil.CHAR_EM_SPACE, StringUtil.CHAR_NON_BREAKING_SPACE, '\r', '\n', '\t', ' ');
    }

    public static void testSensitiveCollatorComparesChar(char... chars) {
        for (char c: chars) {
            Assert.assertNotEquals("sensitive collator should compare char " + describeChar(c), 0,
                    getCollator(true).compare(c + "te" + c + "st" + c, "test"));
        }
    }

    public static void testSensitiveCollatorIgnoresChars(char... chars) {
        for (char c: chars) {
            String testValue = c + "te" + c + "st" + c;
            Assert.assertEquals("sensitive collator should ignore char " + describeChar(c), 0,
                    getCollator(true).compare(testValue, "t" + c + "est"));
        }
    }

    @Test
    public void testSensitiveCompare() {
        testSensitiveCollatorIgnoresChars(StringUtil.CHAR_ZERO_WIDTH_SPACE, StringUtil.CHAR_SOFT_HYPHEN);
        testSensitiveCollatorComparesChar(' ', '\t', '\n', '\r', StringUtil.CHAR_EM_SPACE);
    }

}
