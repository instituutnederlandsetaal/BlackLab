package nl.inl.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RangeRegex {

    /** A regex that can never match anything; can be used for invalid ranges. */
    public static final String REGEX_WITHOUT_MATCHES = "[^.].*";

    /** Return a regex string that will match an integer number within the specified range (inclusive).
     *
     * Leading zeroes are taken into account. If you need additional check ssuch as start- or end-of-
     * string or word boundaries, you should add them to the regex yourself.
     */
    public static String forRange(int min, int max) {
        if (min < 0)
            throw new IllegalArgumentException("min and max should be non-negative");
        if (min > max)
            throw new IllegalArgumentException("min should be less than or equal to max");
        String regex = regexRange(min, max);

        // Simplify regex by replacing e.g. [0-9][0-9][0-9] with [0-9]{3}
        Matcher simplified = Pattern.compile("(?:\\[0-9\\]){2,}").matcher(regex);
        StringBuffer sb = new StringBuffer();
        while (simplified.find()) {
            int n = simplified.group().length() / 5;
            simplified.appendReplacement(sb, "[0-9]" + repetition(n, n));
        }
        simplified.appendTail(sb);
        return "0*" + sb;
    }

    private static String regexRange(int min, int max) {
        int shortest = String.valueOf(min).length();
        int longest = String.valueOf(max).length();

        if (shortest == longest) {
            // Single length. E.g. 23-45
            return regexRangeSameLength(String.valueOf(min), String.valueOf(max));

        } else {
            // Multiple lengths. Create patterns for each length.
            List<String> patterns = new ArrayList<>();

            // Smallest length; e.g. if min == 3, a range from 3-9.
            // or if min == 33, range from 33-99.
            patterns.add(regexRange(min, largestOfLength(shortest)));

            // Middle ranges: e.g. if min == 3 and max == 5421, ranges from 10-99 and 100-999.
            if (shortest + 1 < longest) {
                patterns.add("[1-9][0-9]" + repetition(shortest, longest - 2));
            }

            // Largest length: e.g. if max == 34, a range from 10-34.
            // or if max == 123, range from 100-123.
            patterns.add(regexRange(smallestOfLength(longest), max));

            return "(" + String.join("|", patterns) + ")";
        }
    }

    private static String repetition(int repMin, int repMax) {
        if (repMax <= 0) // "no maximum"
            return repMin == 0 ? "*" : (repMin == 1 ? "?" : "{" + repMin + ",}");
        assert repMin <= repMax : "Minimum repetition should be less than or equal to maximum repetition";
        if (repMin == repMax) {
            if (repMin == 1)
                return ""; // not really a repetition
            return "{" + repMin + "}";
        }
        if (repMin == 0 && repMax == 1)
            return "?"; // optional
        return "{" + repMin + "," + repMax + "}";
    }

    /** Smallest number of length l; i.e. 0 for l == 1; 10 for l == 2; 100 for l == 3; etc. */
    private static int smallestOfLength(int l) {
        if (l == 1)
            return 0; // zero is a special case
        return (int)Math.pow(10, l - 1);
    }

    /** Largest number of length l; i.e. 9 for l == 1; 99 for l == 2; 999 for l == 3; etc. */
    private static int largestOfLength(int l) {
        return (int)Math.pow(10, l) - 1;
    }

    /**
     * Create a regex for a range of numbers with the same length.
     *
     * @param firstNumber the start of the range
     * @param lastNumber the end of the range
     * @return the regex pattern
     */
    private static String regexRangeSameLength(String firstNumber, String lastNumber) {
        assert firstNumber.length() == lastNumber.length() : "Numbers should have the same length";
        int length = firstNumber.length();
        if (length == 1) {
            // Single digit; output the range
            return firstNumber.equals(lastNumber) ? firstNumber : characterRange(firstNumber, lastNumber);
        }
        int minDigit = firstNumber.charAt(0) - '0';
        int maxDigit = lastNumber.charAt(0) - '0';

        if (minDigit == maxDigit) {
            // Starts with same digit; output that digit and continue with the rest of the string
            return minDigit + regexRangeSameLength(firstNumber.substring(1), lastNumber.substring(1));
        } else {
            List<String> patterns = new ArrayList<>();

            // First range; e.g. 23-29 for min == 23, regex 2[3-9]
            if (!firstNumber.substring(1).equals("0".repeat(length - 1))) {
                patterns.add(minDigit + regexRangeSameLength(firstNumber.substring(1),
                        "9".repeat(length - 1)));
            } else {
                // Merge the first range into the middle range instead
                minDigit -= 1;
            }
            boolean lastRangeDone = false;
            if (lastNumber.substring(1).equals("9".repeat(length - 1))) {
                // Merge the last range into the midle range instead
                maxDigit += 1;
                lastRangeDone = true;
            }

            if (maxDigit - minDigit > 1) {
                // Middle range; e.g. 30-89 for min == 23 and max == 97, regex [3-8][0-9]
                patterns.add(characterRange(Integer.toString(minDigit + 1), Integer.toString(maxDigit - 1)) +
                        regexRangeSameLength("0".repeat(length - 1), "9".repeat(length - 1)));
            }

            // Last range; e.g. 90-97 for max == 97, regex 9[0-7]
            if (!lastRangeDone) {
                patterns.add(maxDigit + regexRangeSameLength("0".repeat(length - 1),
                        lastNumber.substring(1)));
            }

            return patterns.size() > 1 ? "(" + String.join("|", patterns) + ")" : patterns.get(0);
        }
    }

    private static String characterRange(String startStr, String endStr) {
        return "[" + startStr + "-" + endStr + "]";
    }
}
