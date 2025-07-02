package nl.inl.util;

import java.util.AbstractList;
import java.util.AbstractSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.eclipse.collections.api.iterator.IntIterator;
import org.eclipse.collections.api.list.primitive.IntList;
import org.eclipse.collections.api.set.primitive.MutableIntSet;

public class CollUtil {

    public static AbstractSet<Integer> toJavaSet(final MutableIntSet keySet) {
        return new AbstractSet<>() {
            @Override
            public Iterator<Integer> iterator() {
                final IntIterator it = keySet.intIterator();
                return new Iterator<>() {
                    @Override
                    public boolean hasNext() {
                        return it.hasNext();
                    }

                    @Override
                    public Integer next() {
                        if (!hasNext())
                            throw new NoSuchElementException();
                        return it.next();
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }

            @Override
            public int size() {
                return keySet.size();
            }

        };
    }

    public static List<Integer> toJavaList(final IntList increments) {
        return new AbstractList<>() {
            @Override
            public Integer get(int index) {
                return increments.get(index);
            }

            @Override
            public int size() {
                return increments.size();
            }
        };
    }

    /**
     * Convert a map of values to a map of lists of values.
     *
     * Each list will be length 1.
     *
     * @param map map to convert, or null
     * @return resulting map of lists, or null if input was null
     * @param <T> type of values
     */
    public static <T> Map<String, List<T>> toMapOfLists(Map<String, T> map) {
        if (map == null)
            return null;
        Map<String, List<T>> atts = new LinkedHashMap<>();
        for (Map.Entry<String, T> e : map.entrySet()) {
            atts.put(e.getKey(), Collections.singletonList(e.getValue()));
        }
        return atts;
    }

}
