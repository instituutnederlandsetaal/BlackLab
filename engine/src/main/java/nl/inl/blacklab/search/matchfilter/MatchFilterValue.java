package nl.inl.blacklab.search.matchfilter;

import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.ForwardIndexDocument;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.MatchInfo;

/** A simple value (string, number, ...) */
public class MatchFilterValue extends MatchFilter {
    final ConstraintValue value;

    public MatchFilterValue(ConstraintValue value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((value == null) ? 0 : value.hashCode());
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
        MatchFilterValue other = (MatchFilterValue) obj;
        if (value == null) {
            if (other.value != null)
                return false;
        } else if (!value.equals(other.value))
            return false;
        return true;
    }

    @Override
    public void setHitQueryContext(HitQueryContext context) {
        // NOP
    }

    @Override
    public ConstraintValue evaluate(ForwardIndexDocument fiDoc, MatchInfo[] matchInfo) {
        return value;
    }

    @Override
    public void lookupAnnotationIndices(ForwardIndexAccessor fiAccessor) {
        // NOP
    }

    @Override
    public MatchFilter rewrite() {
        return this;
    }

    public ConstraintValue getValue() {
        return value;
    }
}
