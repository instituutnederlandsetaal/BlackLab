package nl.inl.blacklab.tools.frequency;

import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

/**
 * Info about an annotation we're grouping on.
 */
record AnnotInfo(Annotation annotation, MatchSensitivity matchSensitivity) {
}
