package org.ivdnt.blacklab.proxy.helper;

import com.ibm.icu.text.Collator;

/**
 * Utility methods for generating a sortable string from various values.
 *
 * The resulting strings will yield the same sort order as the original value.
 *
 * For now we simply encode numbers to padded decimal number strings to ensure they
 * can still be compared using a collator. If we sort by numbers a lot, we should
 * think about a more efficient encoding.
 */
public class SortValueUtil {
    public static String encode(int value) {
        if (value < 0)
            throw new IllegalArgumentException("Value may not be negative");
        return String.format("%010d", value);
    }

    public static int decodeInt(String value) {
        return Integer.parseInt(value);
    }

    public static String encode(long value) {
        if (value < 0)
            throw new IllegalArgumentException("Value may not be negative");
        return String.format("%019d", value);
    }

    public static long decodeLong(String value) {
        return Long.parseLong(value);
    }

    public static String encodeYear(int year) {
        if (year < 0 || year > 3000)
            throw new IllegalArgumentException("Year must be between 0 and 3000, not " + year);
        return String.format("%04d", year);
    }

    public static int decodeYear(String year) {
        if (year.length() != 4)
            throw new IllegalArgumentException("Year must be 4 characters long, not " + year);
        return Integer.parseInt(year);
    }

    public static int compare(Collator collator, String[] aw, String[] bw) {
        int n = Math.min(aw.length, bw.length);
        for (int i = 0; i < n; i++) {
            int cmp = collator.compare(aw[i], bw[i]);
            if (cmp != 0)
                return cmp;
        }
        if (aw.length > n)
            return 1;
        else if (bw.length > n)
            return -1;
        return 0;
    }
}
