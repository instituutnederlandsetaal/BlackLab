package nl.inl.blacklab.tools.frequency;

import nl.inl.blacklab.forwardindex.GAnnotationForwardIndex;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

/**
 * Info about an annotation we're grouping on.
 */
final class AnnotInfo {
    private final GAnnotationForwardIndex annotationForwardIndex;

    private final MatchSensitivity matchSensitivity;

    public GAnnotationForwardIndex getAnnotationForwardIndex() {
        return annotationForwardIndex;
    }

    public MatchSensitivity getMatchSensitivity() {
        return matchSensitivity;
    }

    public AnnotInfo(GAnnotationForwardIndex annotationForwardIndex, MatchSensitivity matchSensitivity) {
        this.annotationForwardIndex = annotationForwardIndex;
        this.matchSensitivity = matchSensitivity;
    }
}
