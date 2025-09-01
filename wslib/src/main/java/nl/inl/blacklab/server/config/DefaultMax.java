package nl.inl.blacklab.server.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import nl.inl.blacklab.Constants;

/**
 * Integer setting with a default and maximum value.
 * 
 * Please note that setting a max of -1 is interpreted as
 * {@link Constants#JAVA_MAX_ARRAY_SIZE} or {@link Long#MAX_VALUE},
 * effectively setting no limit.
 */
public class DefaultMax {
    
    public static DefaultMax get(long def, long max) {
        return new DefaultMax(def, max);
    }
    
    @JsonProperty("default")
    long defaultValue;

    long max;

    DefaultMax() {
        defaultValue = 0;
        max = 0;
    }

    DefaultMax(long def, long max) {
        this.defaultValue = def;
        setMax(max);
    }

    public long getDefault() {
        return defaultValue;
    }

    @SuppressWarnings("unused")
    public void setDefault(long defaultValue) {
        this.defaultValue = defaultValue;
    }

    /**
     * Get maximum value.
     *
     * If max was set to -1, return {@link Long#MAX_VALUE}.
     *
     * @return maximum value
     */
    public long getMax() {
        return max == -1 ? Long.MAX_VALUE : max;
    }

    /**
     * Get maximum value as an integer.
     *
     * If max was set to -1, or exceeds {@link Constants#JAVA_MAX_ARRAY_SIZE},
     * return that value instead.
     *
     * @return maximum value
     */
    public int getMaxInt() {
        return max == -1 || max > Constants.JAVA_MAX_ARRAY_SIZE ?
                Constants.JAVA_MAX_ARRAY_SIZE : (int)max;
    }

    public void setMax(long max) {
        this.max = max;
    }
}
