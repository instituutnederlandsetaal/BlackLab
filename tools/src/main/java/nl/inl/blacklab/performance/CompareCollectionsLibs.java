package nl.inl.blacklab.performance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;
import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import nl.inl.util.Timer;

public class CompareCollectionsLibs {
    
    private static final boolean FALSE = false;

    public static void main(String[] args) {
        testIntObjectMaps();
        if (FALSE) {
            testIntegerLists();
            testHitLists();
        }
    }

    record Hit(int doc, int start, int end) implements Comparable<Hit> {
        @Override
        public int compareTo(Hit o) {
            int c = Integer.compare(doc, o.doc);
            if (c == 0) {
                c = Integer.compare(start, o.start);
                if (c == 0) {
                    c = Integer.compare(end, o.end);
                }
            }
            return c;
        }
    }

    private static void testIntObjectMaps() {
        
        System.out.println("\nTesting Map<Integer, Hit> versus specific type implementations\n");
        
        // Conclusion: ...
        
        final int numberOfItems = 10_000_000;
        
        int[] baseKeys = new int[numberOfItems];
        Hit[] base = new Hit[numberOfItems];
        
        Map<Integer, Hit> javaMap = new HashMap<>();
        
        MutableIntObjectMap<Hit> ecMap = new IntObjectHashMap<>();
        
        Int2ObjectMap<Hit> fuMap = new Int2ObjectOpenHashMap<>();

        Random random = new Random(1234);
        for (int i = 0; i < numberOfItems; i++) {
            baseKeys[i] = random.nextInt();
            base[i] = new Hit(random.nextInt(), random.nextInt(), random.nextInt());
        }
        
        time("Fill Java map", () -> { for (int i = 0; i < numberOfItems; i++) javaMap.put(baseKeys[i], base[i]); });
        time("Fill Eclipse map", () -> { for (int i = 0; i < numberOfItems; i++) ecMap.put(baseKeys[i], base[i]); });
        time("Fill fastutil map", () -> { for (int i = 0; i < numberOfItems; i++) fuMap.put(baseKeys[i], base[i]); });
        
        final int numberOfFetches = 2_000_000;
        int[] fetchIndex = new int[numberOfFetches];
        for (int i = 0; i < numberOfFetches; i++) {
            fetchIndex[i] = random.nextInt(numberOfItems);
        }
        time("Retrieve from Java map", () -> {
            int total = 0;
            for (int i = 0; i < numberOfFetches; i++) {
                total += javaMap.get(baseKeys[fetchIndex[i]]).start;
            }
            System.out.println("Total: " + total);
        });
        time("Retrieve from Eclipse map", () -> {
            int total = 0;
            for (int i = 0; i < numberOfFetches; i++) {
                total += javaMap.get(baseKeys[fetchIndex[i]]).start;
            }
            System.out.println("Total: " + total);
        });
        time("Retrieve from fastutil map", () -> {
            int total = 0;
            for (int i = 0; i < numberOfFetches; i++) {
                total += javaMap.get(baseKeys[fetchIndex[i]]).start;
            }
            System.out.println("Total: " + total);
        });
    }

    private static void testHitLists() {
        
        System.out.println("\nTesting different List<Hit> implementations\n");
        
        // Conclusion: Java ArrayList is about as fast as the other two, sometimes even a little faster.
        
        final int numberOfItems = 10_000_000;
        
        Hit[] base = new Hit[numberOfItems];
        
        List<Hit> javaList = new ArrayList<>();
        
        List<Hit> ecList = new FastList<>();
        
        ObjectArrayList<Hit> fuList = new ObjectArrayList<>();

        Random random = new Random(1234);
        for (int i = 0; i < numberOfItems; i++) {
            base[i] = new Hit(random.nextInt(), random.nextInt(), random.nextInt());
        }
        
        time("Fill Java list", () -> javaList.addAll(Arrays.asList(base)));
        time("Fill Eclipse list", () -> ecList.addAll(Arrays.asList(base)));
        time("Fill fastutil list", () -> fuList.addAll(Arrays.asList(base)));
        
        Comparator<Hit> comp = (a, b) -> {
            int c = Integer.compare(a.doc, b.doc);
            if (c == 0) {
                c = Integer.compare(a.start, b.start);
                if (c == 0) {
                    c = Integer.compare(a.end, b.end);
                }
            }
            return c;
        };
        time("Sort Java list", () -> javaList.sort(comp));
        time("Sort Eclipse list", () -> ecList.sort(comp));
        time("Sort fastutil list", () -> fuList.sort(comp));
    }
    
    private static void testIntegerLists() {
        
        System.out.println("\nTesting List<Integer> vs primitive array list implementations\n");
        
        // Conclusion: Java list is slow because of (un)boxing. Eclipse is fast but doesn't allow for custom Comparators.
        // Fastutil is about as fast as Eclipse and allows for custom primitive Comparators.
        
        final int numberOfItems = 10_000_000;
        
        int[] base = new int[numberOfItems];
        
        List<Integer> javaList = new ArrayList<>();
        
        MutableIntList ecList = new IntArrayList();
        
        it.unimi.dsi.fastutil.ints.IntArrayList fuList = new it.unimi.dsi.fastutil.ints.IntArrayList();

        Random random = new Random(1234);
        for (int i = 0; i < numberOfItems; i++) {
            base[i] = random.nextInt();
        }
        
        time("Fill Java list", () -> { for (int item: base) javaList.add(item); });
        time("Fill Eclipse list", () -> { for (int item: base) ecList.add(item); });
        time("Fill fastutil list", () -> { for (int item: base) fuList.add(item); });
        
        time("Sort Java list", () -> javaList.sort(Comparator.naturalOrder()));
        time("Sort Eclipse list", ecList::sortThis);
        time("Sort fastutil list", () -> IntArrays.quickSort(fuList.elements(), 0, numberOfItems));
    }
    
    interface Task {
        void perform();
    }

    private static void time(String title, Task task) {
        System.out.println(title);
        Timer t = new Timer();
        task.perform();
        System.out.println(t.elapsedDescription(true));
    }
    
}
