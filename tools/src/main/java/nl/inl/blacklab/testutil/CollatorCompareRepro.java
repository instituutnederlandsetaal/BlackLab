package nl.inl.blacklab.testutil;

import java.util.Locale;

import com.ibm.icu.text.Collator;

/** Repro for an ICU4j 78.3 bug. */
public class CollatorCompareRepro {
    public static void main(String[] args) {
        Collator collator = Collator.getInstance(new Locale("en"));
        test(collator, "dàß", "Daß"); // MISMATCH (-1 / 1)
    }

    private static void test(Collator collator, String left, String right) {
        int resultCompare = collator.compare(left, right);
        int resultCk = collator.getCollationKey(left).compareTo(collator.getCollationKey(right));
        if (resultCompare != resultCk)
            System.out.println("MISMATCH comparing " + left + " and " + right +
                    "\n  compare: " + resultCompare + "; CollationKey: " + resultCk);
    }
}
