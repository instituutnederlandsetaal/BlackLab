package nl.inl.blacklab.search.matchfilter;

import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.set.mutable.primitive.IntHashSet;

import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.ForwardIndexDocument;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.MatchInfo;

public class MatchFilterTokenAnnotationEqualsString extends MatchFilter {
    private final String groupName;

    private int groupIndex;

    private final String annotationName;

    private int annotIndex = -1;

    private final String compareToTermString;

    private int compareToGlobalTermId = -1;

    private MutableIntSet compareToGlobalTermIds;

    private final MatchSensitivity sensitivity;

    public MatchFilterTokenAnnotationEqualsString(String groupName, String annotationName, String termString,
                                                MatchSensitivity sensitivity) {
        this.groupName = groupName;
        this.annotationName = annotationName;
        this.compareToTermString = termString;
        this.sensitivity = sensitivity;
    }

    @Override
    public String toString() {
        return groupName + (annotationName == null ? "" : "." + annotationName) + " = " + compareToTermString;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((groupName == null) ? 0 : groupName.hashCode());
        result = prime * result + ((annotationName == null) ? 0 : annotationName.hashCode());
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
        if (annotationName == null) {
            if (other.annotationName != null)
                return false;
        } else if (!annotationName.equals(other.annotationName))
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
        if (annotIndex < 0)
            return ConstraintValue.get(tokenPosition);
        int leftTermGlobalId = fiDoc.getTokenGlobalTermId(annotIndex, tokenPosition);
        if (compareToGlobalTermId >= 0)
            return ConstraintValue.get(leftTermGlobalId == compareToGlobalTermId); // just a single term to compare to
        return ConstraintValue.get(compareToGlobalTermIds.contains(leftTermGlobalId)); // multiple terms, use set.contains()
    }

    @Override
    public void lookupAnnotationIndices(ForwardIndexAccessor fiAccessor) {
        if (annotationName != null) {
            annotIndex = fiAccessor.getAnnotationNumber(annotationName);
            compareToGlobalTermIds = new IntHashSet();
            compareToGlobalTermId = -1;
            fiAccessor.getGlobalTermNumbers(compareToGlobalTermIds, annotIndex, compareToTermString, sensitivity);
            if (compareToGlobalTermIds.size() == 1) {
                compareToGlobalTermId = compareToGlobalTermIds.intIterator().next();
            }
        }
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
