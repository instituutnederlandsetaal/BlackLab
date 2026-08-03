package nl.inl.util;

/**
 * Simple class for measuring elapsed time.
 */
public class Timer {
    private long start;

    public Timer() {
        reset();
    }

    public long elapsed() {
        return System.currentTimeMillis() - start;
    }

    public void reset() {
        start = System.currentTimeMillis();
    }

    /**
     * Describe the elapsed time in a human-readable way.
     * 
     * Does not report milliseconds.
     *
     * @return human-readable string for the elapsed time.
     */
    public String elapsedDescription() {
        return elapsedDescription(false);
    }

    /**
     * Describe the elapsed time in a human-readable way.
     *
     * @param reportMsec if true, also reports milliseconds
     *
     * @return human-readable string for the elapsed time.
     */
    public String elapsedDescription(boolean reportMsec) {
        return TimeUtil.describeInterval(elapsed(), reportMsec);
    }

    /**
     * Describe the interval in a human-readable way.
     *
     * Doesn't report details below a second.
     *
     * @param interval time in ms
     * @return human-readable string for the interval.
     */
    public static String describeInterval(long interval) {
        return TimeUtil.describeInterval(interval, false);
    }
}
