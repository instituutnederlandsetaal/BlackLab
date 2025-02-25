package nl.inl.util;

import java.util.Random;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Test;

public class TestRangeRegex {

    // Generate random numbers outside the range and test them
    static Random rand = new Random();

    @Test
    public void testRanges() {
        // Test cases for verifying range matching
        testRange(0, 0);
        testRange(3, 5);
        testRange(0, 9);
        testRange(35, 41);
        testRange(109, 111);
        testRange(0, 12);
        testRange(2, 13);
        testRange(0, 99);
        testRange(3, 98);
        testRange(0, 199);
        testRange(0, 699);
        testRange(0, 999);
        testRange(3, 987);
        testRange(42, 1001);
        testRange(3, 123456);

        // Test some random ranges as well for good measure
        for (int i = 0; i < 100; i++) {
            int min = rand.nextInt(1000);
            int max = rand.nextInt(1000);
            if (min < max)
                testRange(min, max);
            else
                testRange(max, min);
        }

        // This one takes a little longer; don't run them for every test
        //testRange(3, 1234567890);
    }

    private static void testRange(int min, int max) {
        String regex = RangeRegex.forRange(min, max);
        Pattern pattern = Pattern.compile("^" + regex + "$");

        // Test all values within the range if reasonable, or a selection if the range is too large
        // Randomly add leading zeroes
        int stepSize = Math.max(1, (max - min) / 100_000);
        for (int number = min; number <= max; number += (stepSize == 1 ? 1 : rand.nextInt(stepSize) + 1)) {
            String leadingZeroes = rand.nextInt(10) == 0 ? "0".repeat(rand.nextInt(10)) : "";
            Assert.assertTrue("Regex for range (" + min + "," + max + ") does not match " + number + "; regex: " + regex,
                    pattern.matcher(leadingZeroes + number).matches());
        }

        // Generate 50 random numbers below the range
        for (int i = 0; i < 50; i++) {
            int number = min - 1 - rand.nextInt(100); // Random number less than 'min'
            Assert.assertFalse("Regex for range (" + min + "," + max + ") incorrectly matches " + number + "; regex: " + regex,
                    pattern.matcher(String.valueOf(number)).matches());
        }

        // Generate 50 random numbers above the range
        for (int i = 0; i < 50; i++) {
            int number = max + 1 + rand.nextInt(100000); // Random number greater than 'max'
            Assert.assertFalse("Regex for range (" + min + "," + max + ") incorrectly matches " + number + "; regex: " + regex,
                    pattern.matcher(String.valueOf(number)).matches());
        }
    }
}
