package nl.inl.blacklab.performance;

import nl.inl.blacklab.search.results.hits.Hit;
import nl.inl.blacklab.search.results.hits.Hits;
import nl.inl.blacklab.search.results.hits.HitsMutable;
import nl.inl.util.Timer;

/**
 * Compare performance of different HitsInternal implementations.
 *
 * Not representative of real-world usage.
 */
public class CompareHitsInternalImpls {

    public static final long ITERATIONS = 100_000_000;

    private CompareHitsInternalImpls() {
    }

    static void time(String message, Runnable r) {
        Timer t = new Timer();
        r.run();
        if (message != null)
            System.out.println(message + ": " + t.elapsed() + "ms");
    }

    static void testFill(HitsMutable hits) {
        for (int i = 0; i < ITERATIONS; i++) {
            hits.add(1, 2, 3, null);
        }
    }

    static void testIterate(Hits hits) {
        long n = -1;
        for (Hit h: hits) {
            if (h.doc() > n)
                n = h.doc();
        }
    }

    static void testIterateGet(Hits hits) {
        long n = -1;
        for (long i = 0; i < hits.size(); i++) {
            int d = hits.doc(i);
            if (d > n)
                n = d;
        }
    }

    static void test(String msg, HitsMutable hits) {
        time(msg == null ? null : msg + " FILL", () -> testFill(hits));
        time(msg == null ? null : msg + " ITERATE", () -> testIterate(hits));
        time(msg == null ? null : msg + " ITERATE-GET", () -> testIterateGet(hits));
    }

    public static void main(String[] args) {

        time("WARMUP", () -> {
            test(null, HitsMutable.create(null, null, -1, false, false));
            test(null, HitsMutable.create(null, null, -1, true,  false));
            test(null, HitsMutable.create(null, null, -1, false,  true));
            test(null, HitsMutable.create(null, null, -1, true,   true));
        });

        test("SMALL UNLOCKED", HitsMutable.create(null, null, -1, false, false));
        test("HUGE  UNLOCKED", HitsMutable.create(null, null, -1, true,  false));
        test("SMALL LOCKED  ", HitsMutable.create(null, null, -1, false,  true));
        test("HUGE  LOCKED  ", HitsMutable.create(null, null, -1, true,   true));
    }

}
