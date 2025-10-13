package nl.inl.blacklab.testutil;

import java.text.Collator;
import java.util.Locale;

import nl.inl.blacklab.forwardindex.Collators;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.util.StringUtil;

public class CollatorCompare {

    private CollatorCompare() {
    }

    private static Collator getDefaultEnglishCollator() {
        return Collator.getInstance(new Locale("en", "GB"));
    }

    private static Collator getCollator(boolean sensitive) {
        Collator coll = getDefaultEnglishCollator();
        //Collator coll = Collator.getInstance(new Locale("en", "GB"));
        Collators colls = new Collators(coll, Collators.CollatorVersion.V2);
        return colls.get(sensitive ? MatchSensitivity.SENSITIVE : MatchSensitivity.INSENSITIVE);
    }

    private static void assertEquals(String msg, Object a, Object b) {
        assert a.equals(b) : msg;
    }

    private static void assertNotEquals(String msg, Object a, Object b) {
        assert !a.equals(b) : msg;
    }

    private static void testInsensitiveCollatorComparesChar(char... chars) {
        for (char c: chars) {
            String testValue = c + "te" + c + "st" + c;
            assertNotEquals("insensitive collator compares char " + c + "(" + (int)c + ")", 0,
                    getCollator(false).compare(testValue, "test"));
            assertEquals("desensitize() doesn't remove char " + c,
                    MatchSensitivity.INSENSITIVE.desensitize(testValue), StringUtil.stripAccents(testValue.toLowerCase()));
        }
    }

    private static void testInsensitiveCollatorIgnoresChars(char... chars) {
        for (char c: chars) {
            String testValue = c + "te" + c + "st" + c;
            assertEquals("insensitive collator ignores char " + c + "(" + (int)c + ")", 0,
                    getCollator(false).compare(testValue, "t" + c + "est"));
            String desensitized = MatchSensitivity.INSENSITIVE.desensitize(StringUtil.sanitizeAndNormalizeUnicode(testValue));
            assertEquals("desensitize() removes char " + ((int)c), desensitized, "test");
        }
    }

    public static void testInsensitiveCompare() {
        testInsensitiveCollatorIgnoresChars('\t', '\n', '\r', StringUtil.CHAR_SOFT_HYPHEN, StringUtil.CHAR_EM_SPACE, StringUtil.CHAR_NON_BREAKING_SPACE);
        testInsensitiveCollatorComparesChar(' ');
    }

    public static void testSensitiveCollatorComparesChar(char... chars) {
        for (char c: chars) {
            assertNotEquals("sensitive collator compares char " + c + "(" + (int)c + ")", 0,
                    getCollator(true).compare(c + "te" + c + "st" + c, "test"));
        }
    }

    public static void testSensitiveCollatorIgnoresChars(char... chars) {
        for (char c: chars) {
            String testValue = c + "te" + c + "st" + c;
            assertEquals("sensitive collator ignores char " + c + "(" + (int)c + ")", 0,
                    getCollator(true).compare(testValue, "t" + c + "est"));
        }
    }

    public static void testCodepoints() {
        for (char ch = ' '; ; ch++) {
            boolean isIgnoredBySensitive = ch < 32 || ch >= StringUtil.CHAR_DELETE && ch < StringUtil.CHAR_NON_BREAKING_SPACE ||
                    ch >= 8203 && ch <= 8207;
            boolean isAccent = Character.toString(ch).matches("\\p{InCombiningDiacriticalMarks}");
            boolean isIgnoredByInsensitive = isIgnoredBySensitive || isAccent || ch == StringUtil.CHAR_NON_BREAKING_SPACE ||
                    ch == 173 || ch >= 1155 &&
                    ch <= 1158 || ch >= 8192 && ch <= 8202 || ch >= 8208 && ch <= 8213 || ch >= 8400 && ch <= 8417 ||
                    ch == 8722 || ch == 12288 || ch == 65279;
            if (isIgnoredBySensitive)
                testSensitiveCollatorIgnoresChars(ch);
            else
                testSensitiveCollatorComparesChar(ch);

            try {
                if (isIgnoredByInsensitive)
                    testInsensitiveCollatorIgnoresChars(ch);
                else
                    testInsensitiveCollatorComparesChar(ch);
                //System.out.println("Insensitive compares char " + ch + " (" + (int)ch + ")");
            } catch(AssertionError e) {
                System.out.println("Insensitive doesn't compare char " + ch + " (" + (int)ch + ")");
                throw e;
            }
            if (ch == Character.MAX_VALUE)
                break;
        }
    }

    public static void main(String[] args) {
        testCodepoints();
    }
}
