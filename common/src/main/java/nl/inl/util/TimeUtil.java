package nl.inl.util;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class TimeUtil {

    private TimeUtil() {
    }
    
    /**
     * Return the singular or the plural form of a noun depending on a number.
     *
     * This version of the method simply appends an "s" to form the plural. For
     * irregular plural forms, use the version that takes 3 parameters.
     *
     * @param singular the singular to 'pluralize'
     * @param number if this equals 1, no s is added
     * @return the possibly pluralized form
     */
    private static String pluralize(String singular, long number) {
        return number == 1 ? singular : singular + "s";
    }

    /**
     * Describe the elapsed time in a human-readable way.
     *
     * @param intervalMsec time in ms
     * @param reportMsec if true, also reports milliseconds
     *
     * @return human-readable string for the elapsed time.
     */
    public static String describeInterval(long intervalMsec, boolean reportMsec) {
        long msec = intervalMsec % 1000;
        long sec = intervalMsec / 1000;
        long min = sec / 60;
        sec %= 60;
        long hours = min / 60;
        min %= 60;
        StringBuilder result = new StringBuilder();
        if (hours > 0) {
            result.append(hours).append(" ").append(pluralize("hour", hours)).append(", ");
        }
        if (min > 0) {
            result.append(min).append(" ").append(pluralize("minute", min)).append(", ");
        }
        result.append(sec).append(" ").append(pluralize("second", sec));
        if (reportMsec) {
            result.append(", ").append(msec).append(" ").append(pluralize("millisecond", msec));
        }
        return result.toString();
    }

    /**
     * Describe the interval in a human-readable way.
     *
     * Doesn't report details below a second.
     *
     * @param intervalMsec time in ms
     * @return human-readable string for the interval.
     */
    public static String describeInterval(long intervalMsec) {
        return describeInterval(intervalMsec, false);
    }

    /**
     * Format the current date and time according to the SQL datetime convention.
     *
     * @return a string representation, e.g. "1980-02-01 00:00:00"
     */
    public static String timestamp() {
        // NOTE: DateFormat is not thread-safe, so we just create a new one every time.
        DateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return dateTimeFormat.format(new Date());
    }
}
