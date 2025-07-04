package nl.inl.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class LimitUtil {

    private LimitUtil() {
    }

    public static <T> T limit(T value, long limitValue) {
        if (value instanceof Limitable)
            return ((Limitable<T>)value).withLimit(limitValue);
        else if (value instanceof Map)
            return (T) limitMap((Map)value, limitValue);
        else if (value instanceof List)
            return (T) limitList((List)value, limitValue);
        else
            return value;
    }

    private static <T> Map<String, T> limitMap(Map<String, T> source, long limitValue) {
        if (source.isEmpty())
            return source;
        return source.entrySet().stream()
                .limit(limitValue)
                .map(entry -> Map.entry(entry.getKey(), limit(entry.getValue(), limitValue)))
                .collect(TreeMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()),
                        Map::putAll);
    }

    private static <T> List<T> limitList(List<T> list, long maxItems) {
        if (list.isEmpty())
            return list;

        return list.stream()
                .limit(maxItems)
                .map(item -> limit(item, maxItems))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    public interface Limitable<T> {
        T withLimit(long max);
    }
}
