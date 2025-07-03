package nl.inl.blacklab.exceptions;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.searches.SearchCacheEntry;

/**
 * Thrown in response to InterruptedException to indicate that a thread was interrupted.
 *
 * E.g. BlackLab Search aborts searches that run for too long, causing this exception
 * to be thrown.
 */
public class InterruptedSearch extends RuntimeException {

    private static final String DEFAULT_MESSAGE = "Search was interrupted";

    public static InterruptedSearch cancelled() {
        return new InterruptedSearch(DEFAULT_MESSAGE + " (cancelled)");
    }

    private SearchCacheEntry<?> cacheEntry;

    public InterruptedSearch(Throwable e) {
        super(DEFAULT_MESSAGE, e);
    }

    public InterruptedSearch(String message) {
        super(message);
    }

    public InterruptedSearch(String message, Throwable e) {
        super(message, e);
    }

    public void setCacheEntry(SearchCacheEntry<?> cacheEntry) {
        this.cacheEntry = cacheEntry;
    }

    public SearchCacheEntry<?> getCacheEntry() {
        return cacheEntry;
    }

    public String getReason() {
        return cacheEntry == null ? "" : cacheEntry.getReason();
    }

    @Override
    public String getMessage() {
        String message = super.getMessage();
        String reason = getReason();
        if (StringUtils.isEmpty(message)) {
            if (!reason.isEmpty())
                return reason;
            return null;
        } else {
            if (!reason.isEmpty())
                return message + " (" + reason + ")";
            return message;
        }
    }

}
