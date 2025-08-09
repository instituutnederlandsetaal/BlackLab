package nl.inl.blacklab.forwardindex;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;

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

    private final AnnotatedField field;

    private final Map<Annotation, AnnotationForwardIndex> fis = new HashMap<>();

    /** Ensure that we don't try to use the FI after closing it. */
    private boolean closed = false;

    public ForwardIndexImpl(BlackLabIndex index, AnnotatedField field) {
        this.index = index;
        this.field = field;
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
    public AnnotatedField field() {
        return field;
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
        if (afi == null) {
            afi = AnnotationForwardIndexImpl.open(this, index, annotation, index.collator());
            add(annotation, afi);
        }
        return afi;
    }

    @Override
    public String toString() {
        return "ForwardIndexImpl(" + index.name() + ")";
    }

    protected void add(Annotation annotation, AnnotationForwardIndex afi) {
        if (closed)
            throw new IllegalStateException("ForwardIndex was closed");
        synchronized (fis) {
            fis.put(annotation, afi);
        }
    }
}
