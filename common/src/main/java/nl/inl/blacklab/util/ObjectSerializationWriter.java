package nl.inl.blacklab.util;

import java.util.LinkedHashMap;
import java.util.Map;

@FunctionalInterface
public interface ObjectSerializationWriter {
    static Map<String, Object> mapFromArgs(Object[] args) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            assert args[i] instanceof String; // key, value, key, value, ...
            map.put((String)args[i], args[i + 1]);
        }
        return map;
    }

    /**
     * @param type text pattern node type, e.g. "term", "sequence", "repeat", ...
     * @param args Sorted list of alternating argument names and values, e.g. key, value, key, value, ...
     */
    void write(String type, Object... args);
}
