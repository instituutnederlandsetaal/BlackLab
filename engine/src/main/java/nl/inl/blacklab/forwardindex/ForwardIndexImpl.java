package nl.inl.blacklab.forwardindex;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;

/**
 * Global forward index implementation.
 */
public class ForwardIndexImpl implements ForwardIndex {

    protected static final Logger logger = LogManager.getLogger(ForwardIndexImpl.class);

    /** Check that the requested snippet can be taken from a document of this length.
     * @param docLength length of the document
     * @param snippetStart start position of the snippet
     * @param snippetEnd length of the snippet
     */
    public static void validateSnippetParameters(int docLength, int snippetStart, int snippetEnd) {
        if (snippetStart < 0 || snippetEnd < 0) {
            throw new IllegalArgumentException("Illegal values, start = " + snippetStart + ", end = "
                    + snippetEnd);
        }
        if (snippetStart > docLength || snippetEnd > docLength) {
            throw new IllegalArgumentException("Value(s) out of range, start = " + snippetStart
                    + ", end = " + snippetEnd + ", content length = " + docLength);
        }
        if (snippetStart > snippetEnd) {
            throw new IllegalArgumentException(
                    "Tried to read negative length snippet (from " + snippetStart
                            + " to " + snippetEnd + ")");
        }
    }

    private final BlackLabIndex index;

    private final Map<Annotation, AnnotationForwardIndex> fis = new HashMap<>();

    public ForwardIndexImpl(BlackLabIndex index, AnnotatedField field) {
        this.index = index;

        // Open forward indexes
        ExecutorService executorService = index.blackLab().initializationExecutorService();
        for (Annotation annotation: field.annotations()) {
            if (!annotation.hasForwardIndex())
                continue;
            AnnotationForwardIndexGlobal afi = AnnotationForwardIndexGlobal.open(index, annotation);
            fis.put(annotation, afi);
            // Automatically initialize global terms (in the background)
            executorService.execute(() -> {
                try {
                    // Retrieve global terms, initializing if necessary
                    afi.terms();
                } catch (RuntimeException e) {
                    // Initialization was interrupted. Ignore.
                    // This can happen if e.g. a commandline utility completes
                    // before the full initialization is done. The running threads
                    // are interrupted and the forward index remains uninitialized.
                    // If for some reason the program keeps running and tries to use
                    // the forward index, it will simply try to initialize again
                    // (running in the foreground).
                    logger.error(e);
                }
            });
        }
    }

    @Override
    public AnnotationForwardIndex get(Annotation annotation) {
        assert annotation != null;
        AnnotationForwardIndex afi = fis.get(annotation);
        if (afi == null) {
            if (!annotation.hasForwardIndex())
                throw new IllegalArgumentException("Annotation has no forward index, according to itself: " + annotation);
            throw new IllegalArgumentException("AnnotationForwardIndex not found for: " + annotation);
        }
        return afi;
    }

    @Override
    public String toString() {
        return "ForwardIndexImpl(" + index.name() + ")";
    }

}
