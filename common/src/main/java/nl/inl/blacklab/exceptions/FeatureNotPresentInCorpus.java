package nl.inl.blacklab.exceptions;

/**
 * User referred to a field, annotation or sensitivity that does not exist.
 */
public class FeatureNotPresentInCorpus extends InvalidQuery {
    public FeatureNotPresentInCorpus(String message) {
        super(message);
    }

    public FeatureNotPresentInCorpus(String message, Throwable cause) {
        super(message, cause);
    }

    public FeatureNotPresentInCorpus(Throwable cause) {
        super(cause);
    }
}
