package nl.inl.blacklab.testutil;

import java.text.CollationKey;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.RandomStringUtils;

import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntComparator;

/**
 * A little test utility for comparing the performance of different sorting
 * methods that use an IntComparator. Mainly used to assess what is the fastest
 * way to sort terms in our PostingsWriter and TermsIntegrated.
 *
 * Main conclusion: precalculate CollationKeys when comparing strings as Collator.compare()
 * is synchronized.
 * Other conclusion: our custom ParallelIntSorter seemed to provide a slight benefit at first,
 * but we couldn't replicate this here, so we're not using it anymore. Better to rely on
 * FastUtil, which is well tested and might get faster over time.
 */
public class TestSortPerformance {

    private TestSortPerformance() {
    }

    @FunctionalInterface
    interface Sorter {
        void sort(int[] arr, IntComparator comp);
    }

    @FunctionalInterface
    interface ComparatorFactory {
        IntComparator create(int numberOfItems);
    }

    public static void main(String[] args) {

        System.out.println("         n    FQS   FPQ");
        int[] testSizes = { 1000, 10000, 100_000, 150_000, 250_000,
                500_000, 750_000, 1_000_000, 2_500_000, 5_000_000, 7_500_000, 10_000_000 };
        for (int size: testSizes) {
            ComparatorFactory f = indirectStringComparator;
            test(size, f);
        }
    }

    private static final ComparatorFactory indirectIntComparator = (numberOfItems) -> {
        int[] arr = randomArray(numberOfItems, Integer.MAX_VALUE);
        return (a, b) -> Integer.compare(arr[a], arr[b]);
    };

    private static final ComparatorFactory indirectStringComparator = (numberOfItems) -> {
        // Create an array of random strings of length 6
        String[] str = new String[numberOfItems];
        for (int i = 0; i < str.length; i++) {
            str[i] = RandomStringUtils.randomAlphanumeric(6);
        }
        // Comparator compares strings at the specified indexes
        Collator collator = Collator.getInstance();
        IntComparator comp = (a, b) -> collator.compare(str[a], str[b]);
        return comp;
    };

    private static final ComparatorFactory indirectStringListComparator = (numberOfItems) -> {
        // Create an array of random strings of length 6
        List<String> str = new ArrayList<>(numberOfItems);
        for (int i = 0; i < numberOfItems; i++) {
            str.add(RandomStringUtils.randomAlphanumeric(6));
        }
        // Comparator compares strings at the specified indexes
        Collator collator = Collator.getInstance();
        IntComparator comp = (a, b) -> collator.compare(str.get(a), str.get(b));
        return comp;
    };

    private static final ComparatorFactory indirectCollationKeyComparator = (numberOfItems) -> {
        // Create an array of random strings of length 6
        List<CollationKey> str = new ArrayList<>(numberOfItems);
        Collator collator = Collator.getInstance();
        for (int i = 0; i < numberOfItems; i++) {
            str.add(collator.getCollationKey(RandomStringUtils.randomAlphanumeric(6)));
        }
        // Comparator compares strings at the specified indexes
        IntComparator comp = (a, b) -> str.get(a).compareTo(str.get(b));
        return comp;
    };

    private static void test(int n, ComparatorFactory f) {
        // Create an array of indexes to sort
        int[] toSort = randomArray(n, n);

        Sorter[] sorters = {
            IntArrays::quickSort,
            IntArrays::parallelQuickSort
            //,ParallelIntSorter::sort  RIP
        };

        long fastest = Long.MAX_VALUE;
        int fastestIndex = -1;
        System.out.print(String.format("%10d ", n));
        for (int i = 0; i < sorters.length; i++) {
            long time = timeSort(sorters[i], toSort, f);
            if (time < fastest) {
                fastest = time;
                fastestIndex = i;
            }
            System.out.print(String.format("%6d", time));
        }
        // Highlight best result
        System.out.println(String.format("\n           %" + (6 * fastestIndex + 1) + "s    *", ""));
    }

    private static long timeSort(Sorter sorter, int[] toSort, ComparatorFactory f) {
        int[] a = Arrays.copyOf(toSort, toSort.length);
        long start = System.currentTimeMillis();
        IntComparator comp = f.create(toSort.length);
        sorter.sort(a, comp);
        long end = System.currentTimeMillis();
        return end - start;
    }

    private static int[] randomArray(int length, int maxValue) {
        int[] array = new int[length];
        for (int i = 0; i < array.length; i++) {
            array[i] = (int)(Math.random() * maxValue);
        }
        return array;
    }
}
