package nl.inl.blacklab.search.matchfilter;

import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.ForwardIndexDocument;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.MatchInfo;

public class MatchFilterString extends MatchFilter {
    final ConstraintValueString string;

    public MatchFilterString(String string) {
        this(new ConstraintValueString(string));
    }

    public MatchFilterString(ConstraintValueString string) {
        this.string = string;
    }

    public String getString() {
        return string.getValue();
    }

    @Override
    public String toString() {
        return "\"" + string + "\"";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((string == null) ? 0 : string.hashCode());
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
        MatchFilterString other = (MatchFilterString) obj;
        if (string == null) {
            if (other.string != null)
                return false;
        } else if (!string.equals(other.string))
            return false;
        return true;
    }

    @Override
    public void setHitQueryContext(HitQueryContext context) {
        // NOP
    }

    @Override
    public ConstraintValue evaluate(ForwardIndexDocument fiDoc, MatchInfo[] matchInfo) {
        return string;
    }

    @Override
    public void lookupAnnotationIndices(ForwardIndexAccessor fiAccessor) {
        // NOP
    }

    @Override
    public MatchFilter rewrite() {
        return this;
    }

    public String getValue() {
        return string.getValue();
    }
}
