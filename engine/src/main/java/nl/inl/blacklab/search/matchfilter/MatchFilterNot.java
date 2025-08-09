package nl.inl.blacklab.search.matchfilter;

import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.ForwardIndexDocument;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.MatchInfo;

public class MatchFilterNot extends MatchFilter {

    final MatchFilter a;

    public MatchFilterNot(MatchFilter a) {
        super();
        this.a = a;
    }

    @Override
    public String toString() {
        return "!" + a;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((a == null) ? 0 : a.hashCode());
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
        MatchFilterNot other = (MatchFilterNot) obj;
        if (a == null) {
            if (other.a != null)
                return false;
        } else if (!a.equals(other.a))
            return false;
        return true;
    }

    @Override
    public void setHitQueryContext(HitQueryContext context) {
        a.setHitQueryContext(context);
    }

    @Override
    public ConstraintValue evaluate(ForwardIndexDocument fiDoc, MatchInfo[] matchInfo) {
        return ConstraintValue.get(!a.evaluate(fiDoc, matchInfo).isTruthy());
    }

    @Override
    public void lookupAnnotationIndices(ForwardIndexAccessor fiAccessor) {
        a.lookupAnnotationIndices(fiAccessor);
    }

    @Override
    public MatchFilter withField(AnnotatedField field) {
        MatchFilter a2 = a.withField(field);
        if (a2 == a)
            return this;
        return new MatchFilterNot(a2);
    }

    @Override
    public MatchFilter forSegment(LeafReaderContext context) {
        MatchFilter a2 = a.forSegment(context);
        if (a2 == a)
            return this;
        return new MatchFilterNot(a2);
    }

    @Override
    public MatchFilter rewrite() {
        MatchFilter x = a.rewrite();
        if (x != a)
            return new MatchFilterNot(x);
        return this;
    }

    public TextPatternStruct getClause() {
        return a;
    }
}
