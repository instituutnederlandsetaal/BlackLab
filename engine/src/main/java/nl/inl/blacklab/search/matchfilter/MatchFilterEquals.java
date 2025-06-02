package nl.inl.blacklab.search.matchfilter;

import java.util.List;

import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.ForwardIndexDocument;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.MatchInfo;

public class MatchFilterEquals extends MatchFilter {

    private final MatchFilter a;

    private final MatchFilter b;

    private final MatchSensitivity sensitivity;

    public MatchFilterEquals(MatchFilter a, MatchFilter b, MatchSensitivity sensitivity) {
        super();
        this.a = a;
        this.b = b;
        this.sensitivity = sensitivity;
    }

    @Override
    public String toString() {
        return a + " = " + b;
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((a == null) ? 0 : a.hashCode());
        result = prime * result + ((b == null) ? 0 : b.hashCode());
        result = prime * result + ((sensitivity == null) ? 0 : sensitivity.hashCode());
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
        MatchFilterEquals other = (MatchFilterEquals) obj;
        if (a == null) {
            if (other.a != null)
                return false;
        } else if (!a.equals(other.a))
            return false;
        if (b == null) {
            if (other.b != null)
                return false;
        } else if (!b.equals(other.b))
            return false;
        return sensitivity == other.sensitivity;
    }

    @Override
    public void setHitQueryContext(HitQueryContext context) {
        a.setHitQueryContext(context);
        b.setHitQueryContext(context);
    }

    @Override
    public ConstraintValue evaluate(ForwardIndexDocument fiDoc, MatchInfo[] matchInfo) {
        ConstraintValue ra = a.evaluate(fiDoc, matchInfo);
        ConstraintValue rb = b.evaluate(fiDoc, matchInfo);
        if (ra instanceof ConstraintValueString && rb instanceof ConstraintValueString) {
            return ((ConstraintValueString) ra).stringEquals((ConstraintValueString) rb, sensitivity);
        }
        return ConstraintValue.get(ra.equals(rb));
    }

    @Override
    public void lookupAnnotationIndices(ForwardIndexAccessor fiAccessor) {
        a.lookupAnnotationIndices(fiAccessor);
        b.lookupAnnotationIndices(fiAccessor);
    }

    @Override
    public MatchFilter rewrite() {
        MatchFilter x = a.rewrite();
        MatchFilter y = b.rewrite();

        if (x instanceof MatchFilterTokenAnnotation && ((MatchFilterTokenAnnotation) x).hasAnnotation()
                && y instanceof MatchFilterString) {
            // Simple annotation to string comparison, e.g. a.word = "cow"
            // This can be done more efficiently without string comparisons
            String termString = ((MatchFilterString) y).getString();
            return ((MatchFilterTokenAnnotation) x).matchTokenString(termString, sensitivity);
        }

        if (x instanceof MatchFilterTokenAnnotation xtp && y instanceof MatchFilterTokenAnnotation ytp) {
            if (xtp.getAnnotationName().equals(ytp.getAnnotationName())) {
                // Expression of the form a.word = b.word;
                // This can be done more efficiently without string comparisons
                return xtp.matchOtherTokenSameProperty(ytp.getGroupName(), sensitivity);
            }
        }

        // Some other comparison.
        if (x != a || y != b)
            return new MatchFilterEquals(x, y, sensitivity);
        return this;
    }

    public List<MatchFilter> getClauses() {
        return List.of(a, b);
    }

    public MatchSensitivity getSensitivity() {
        return sensitivity;
    }

}
