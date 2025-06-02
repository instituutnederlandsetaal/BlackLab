package nl.inl.blacklab.search.indexmetadata;

import java.util.Collections;
import java.util.Map;

/**
 * Interface for getting custom metadata properties (not used by BlackLab itself)
 */
public interface CustomProps {

    CustomProps NONE = new CustomProps() {
        @Override
        public Object get(String key) {
            return null;
        }

        @Override
        public Map<String, Object> asMap() {
            return Collections.emptyMap();
        }
    };

    default <T> T get(String key, T defaultValue) {
        T x = (T)get(key);
        return x == null ? defaultValue : x;
    }

    Object get(String key);

    Map<String, Object> asMap();

    default boolean containsKey(String key) {
        return get(key) != null;
    }
}
