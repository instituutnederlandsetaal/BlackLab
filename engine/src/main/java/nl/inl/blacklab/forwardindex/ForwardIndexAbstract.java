package nl.inl.blacklab.forwardindex;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.store.AlreadyClosedException;

import nl.inl.blacklab.exceptions.InterruptedSearch;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;

public abstract class ForwardIndexAbstract implements ForwardIndex {

    protected static final Logger logger = LogManager.getLogger(ForwardIndexAbstract.class);

    private final boolean canDoNfaMatching;

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

    private final AnnotatedField field;

    private final Map<Annotation, AnnotationForwardIndex> fis = new HashMap<>();

    /** Used to ensure that no new FIs are opened after the constructor. */
    private final boolean initialized;

    /** Ensure that we don't try to use the FI after closing it. */
    private boolean closed = false;

    protected ForwardIndexAbstract(BlackLabIndex index, AnnotatedField field) {
        this.index = index;
        this.field = field;

        // Open forward indexes
        ExecutorService executorService = index.blackLab().initializationExecutorService();
        for (Annotation annotation: field.annotations()) {
            if (!annotation.hasForwardIndex())
                continue;
            AnnotationForwardIndex afi = get(annotation);
            // Automatically initialize forward index (in the background)
            executorService.execute(() -> {
                try {
                    afi.initialize();
                } catch (AlreadyClosedException|InterruptedSearch e) {
                    // Initialization was interrupted. Ignore.
                    // This can happen if e.g. a commandline utility completes
                    // before the full initialization is done. The running threads
                    // are interrupted and the forward index remains uninitialized.
                    // If for some reason the program keeps running and tries to use
                    // the forward index, it will simply try to initialize again
                    // (running in the foreground).
                }
            });
        }

        canDoNfaMatching = fis.values().stream().allMatch(AnnotationForwardIndex::canDoNfaMatching);
        initialized = true;
    }

    @Override
    public boolean canDoNfaMatching() {
        return canDoNfaMatching;
    }

    /**
     * Close the forward index. Writes the table of contents to disk if modified.
     * (needed for ForwardIndexExternal only; can eventually be removed)
     */
    public void close() {
        synchronized (fis) {
            fis.clear();
            closed = true;
        }
    }

    @Override
    public Terms terms(Annotation annotation) {
        if (closed)
            throw new IllegalStateException("ForwardIndex was closed");
        return get(annotation).terms();
    }

    @Override
    public AnnotatedField field() {
        return field;
    }

    @Override
    public Iterator<AnnotationForwardIndex> iterator() {
        if (closed)
            throw new IllegalStateException("ForwardIndex was closed");
        synchronized (fis) {
            return fis.values().iterator();
        }
    }

    @Override
    public AnnotationForwardIndex get(Annotation annotation) {
        assert annotation != null;
        if (closed)
            throw new IllegalStateException("ForwardIndex was closed");
        if (!annotation.hasForwardIndex())
            throw new IllegalArgumentException("Annotation has no forward index, according to itself: " + annotation);
        AnnotationForwardIndex afi;
        synchronized (fis) {
            afi = fis.get(annotation);
        }
        if (afi == null)
            afi = openAnnotationForwardIndex(annotation, index);
        return afi;
    }

    protected abstract AnnotationForwardIndex openAnnotationForwardIndex(Annotation annotation, BlackLabIndex index);

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "(" + index.name() + "/fi_*)";
    }

    protected void add(Annotation annotation, AnnotationForwardIndex afi) {
        if (initialized)
            throw new IllegalStateException("All forward indexes should have been opened while initializing!");
        if (closed)
            throw new IllegalStateException("ForwardIndex was closed");
        synchronized (fis) {
            fis.put(annotation, afi);
        }
    }
}
