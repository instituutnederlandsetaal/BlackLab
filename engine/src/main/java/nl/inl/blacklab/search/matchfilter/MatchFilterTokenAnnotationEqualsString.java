package nl.inl.blacklab.search.matchfilter;

import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.forwardindex.ForwardIndexSegmentReader;
import nl.inl.blacklab.forwardindex.TermsSegment;
import nl.inl.blacklab.search.BlackLabIndexIntegrated;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.ForwardIndexDocument;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.MatchInfo;

public class MatchFilterTokenAnnotationEqualsString extends MatchFilter {
    private final String groupName;

    private int groupIndex;

    private final String annotationName;

    private final Annotation annotation;

    private int annotationIndex = -1;

    private final String compareToTermString;

    private int compareToSortPosition = -1;

    private final MatchSensitivity sensitivity;

    public MatchFilterTokenAnnotationEqualsString(String groupName, String annotationName, String termString,
                                                MatchSensitivity sensitivity) {
        this.groupName = groupName;
        this.compareToTermString = termString;
        this.sensitivity = sensitivity;
        this.annotationName = annotationName;
        this.annotation = null; // annotation will be looked up later
    }

    public MatchFilterTokenAnnotationEqualsString(String groupName, Annotation annotation, String termString,
            MatchSensitivity sensitivity) {
        this.groupName = groupName;
        this.compareToTermString = termString;
        this.sensitivity = sensitivity;
        this.annotationName = annotation.name();
        this.annotation = annotation;
    }

    public MatchFilterTokenAnnotationEqualsString(String groupName, Annotation annotation, String termString,
            MatchSensitivity sensitivity, int compareToSortPosition) {
        this(groupName, annotation, termString, sensitivity);
        this.compareToSortPosition = compareToSortPosition;
    }

    @Override
    public String toString() {
        return groupName + (annotationName() == null ? "" : "." + annotationName()) + " = " + compareToTermString;
    }

    private String annotationName() {
        return annotation != null ? annotation.name() : annotationName;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((groupName == null) ? 0 : groupName.hashCode());
        result = prime * result + ((annotationName() == null) ? 0 : annotationName().hashCode());
        result = prime * result + ((compareToTermString == null) ? 0 : compareToTermString.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        MatchFilterTokenAnnotationEqualsString other = (MatchFilterTokenAnnotationEqualsString) obj;
        if (groupName == null) {
            if (other.groupName != null)
                return false;
        } else if (!groupName.equals(other.groupName))
            return false;
        if (annotationName() == null) {
            if (other.annotationName() != null)
                return false;
        } else if (!annotationName().equals(other.annotationName()))
            return false;
        if (compareToTermString == null) {
            if (other.compareToTermString != null)
                return false;
        } else if (!compareToTermString.equals(other.compareToTermString))
            return false;
        return true;
    }

    @Override
    public void setHitQueryContext(HitQueryContext context) {
        groupIndex = context.registerMatchInfo(groupName, null);
    }

    @Override
    public ConstraintValue evaluate(ForwardIndexDocument fiDoc, MatchInfo[] matchInfo) {
        MatchInfo span = groupIndex < matchInfo.length ? matchInfo[groupIndex] : null;
        if (span == null)
            return ConstraintValue.undefined();
        int tokenPosition = span.getSpanStart();
        if (annotationIndex < 0)
            return ConstraintValue.get(tokenPosition);
        int leftTermGlobalId = fiDoc.getTokenSegmentSortPosition(annotationIndex, tokenPosition, sensitivity);
        return ConstraintValue.get(compareToSortPosition == leftTermGlobalId);
    }

    @Override
    public MatchFilter withField(AnnotatedField field) {
        Annotation annotation = field.annotation(annotationName);
        if (annotation == null)
            throw new IllegalArgumentException("Annotation '" + annotationName + "' not found in field '" + field.name() + "'.");
        MatchFilterTokenAnnotationEqualsString mf = new MatchFilterTokenAnnotationEqualsString(
                groupName, annotation, compareToTermString, sensitivity);
        mf.annotationIndex = annotationIndex;
        return mf;
    }

    @Override
    public void lookupAnnotationIndices(ForwardIndexAccessor fiAccessor) {
        if (annotationName != null) {
            annotationIndex = fiAccessor.getAnnotationIndex(annotationName);
        }
    }

    @Override
    public MatchFilter forLeafReaderContext(LeafReaderContext context) {
        // Look up the sort position for the value we're comparing to.
        ForwardIndexSegmentReader fi = BlackLabIndexIntegrated.forwardIndex(context);
        assert annotation != null;
        TermsSegment terms = fi.terms(annotation);
        int sortPosition = terms.sortPositionFor(compareToTermString, sensitivity);

        MatchFilterTokenAnnotationEqualsString mf = new MatchFilterTokenAnnotationEqualsString(
                groupName, annotation, compareToTermString, sensitivity,
                sortPosition);
        mf.annotationIndex = annotationIndex;
        return mf;
    }

    @Override
    public MatchFilter rewrite() {
        return this;
    }

    public String getCapture() {
        return groupName;
    }

    public String getAnnotation() {
        return annotationName;
    }

    public MatchSensitivity getSensitivity() {
        return sensitivity;
    }

    public String getValue() {
        return compareToTermString;
    }
}
