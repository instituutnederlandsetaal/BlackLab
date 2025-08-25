package nl.inl.blacklab.testutil;

import com.ibm.icu.text.Collator;
import com.ibm.icu.text.RuleBasedCollator;
import com.ibm.icu.util.ULocale;

import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.util.StringUtil;

public class CollatorCompare {

    private CollatorCompare() {
    }

    static Collator sensitiveColl;

    static Collator insensitiveColl;

    private static synchronized Collator getCollator(boolean sensitive) {
        if (sensitive && sensitiveColl != null)
            return sensitiveColl;
        if (!sensitive && insensitiveColl != null)
            return insensitiveColl;
        Collator coll = Collator.getInstance(new ULocale("en", "GB"));
        if (sensitive)
            coll.setStrength(Collator.TERTIARY);
        else {
            // desensitize
            if (!(coll instanceof RuleBasedCollator))
                throw new IllegalArgumentException("Collator is not instance of RuleBasedCollator");
            // Case- and accent-insensitive collator that doesn't
            // ignore dash and space like the regular insensitive collator (V1) does.
            String rules = ((RuleBasedCollator)coll).getRules().replace(",'-'", ""); // don't ignore dash
            rules += "&' ' < '-' < '_'"; // sort dash and space before underscore
            //rules = rules.replace("<'_'", "<' '<'-'<'_'");
            try {
                coll = new RuleBasedCollator(rules);
            } catch (Exception e) {
                throw BlackLabException.wrapRuntime(e);
            }
            coll.setStrength(com.ibm.icu.text.Collator.PRIMARY); // ignore case and accent differences
        }
        coll.freeze();
        if (sensitive)
            sensitiveColl = coll;
        else
            insensitiveColl = coll;
        return coll;
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
            assertNotEquals("insensitive collator should compare char " + describeChar(c), 0,
                    getCollator(false).compare(testValue, "test"));
            assertEquals("desensitize() shouldn't remove char " + describeChar(c),
                    MatchSensitivity.INSENSITIVE.desensitize(testValue), StringUtil.stripAccents(testValue.toLowerCase()));
        }
    }

    private static void testInsensitiveCollatorIgnoresChars(char... chars) {
        for (char c: chars) {
            String testValue = c + "te" + c + "st" + c;
            assertEquals("insensitive collator should ignore char " + describeChar(c), 0,
                    getCollator(false).compare(testValue, "t" + c + "est"));
            String desensitized = MatchSensitivity.INSENSITIVE.desensitize(StringUtil.sanitizeAndNormalizeUnicode(testValue));
            assertEquals("desensitize() should remove char " + describeChar(c), desensitized, "test");
        }
    }

    private static String describeChar(char c) {
        return "'" + c + "' (" + (int) c + ", " + String.format("\\u%04X", (int) c) + ")";
    }

    public static void testInsensitiveCompare() {
        testInsensitiveCollatorIgnoresChars(/*'\t', '\n',*/ '\r', StringUtil.CHAR_SOFT_HYPHEN, StringUtil.CHAR_EM_SPACE, StringUtil.CHAR_NON_BREAKING_SPACE);
        testInsensitiveCollatorComparesChar(' ');
    }

    public static void testSensitiveCollatorComparesChar(char... chars) {
        for (char c: chars) {
            assertNotEquals("sensitive collator should compare char " + c + "(" + (int)c + ")", 0,
                    getCollator(true).compare(c + "te" + c + "st" + c, "test"));
        }
    }

    public static void testSensitiveCollatorIgnoresChars(char... chars) {
        for (char c: chars) {
            String testValue = c + "te" + c + "st" + c;
            assertEquals("sensitive collator should ignore char " + describeChar(c), 0,
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
        // NOTE: run this with VM option -ea to enable assertions!
        testCodepoints();
    }
}
