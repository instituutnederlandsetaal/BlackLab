package nl.inl.blacklab.search.matchfilter;

import java.util.List;

import org.apache.lucene.index.LeafReaderContext;

import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.ForwardIndexDocument;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.MatchInfo;

public class MatchFilterOr extends MatchFilter {

    final MatchFilter a;
    final MatchFilter b;

    public MatchFilterOr(MatchFilter a, MatchFilter b) {
        super();
        this.a = a;
        this.b = b;
    }

    @Override
    public String toString() {
        return a + " | " + b;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((a == null) ? 0 : a.hashCode());
        result = prime * result + ((b == null) ? 0 : b.hashCode());
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
        MatchFilterOr other = (MatchFilterOr) obj;
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
        return true;
    }

    @Override
    public void setHitQueryContext(HitQueryContext context) {
        a.setHitQueryContext(context);
        b.setHitQueryContext(context);
    }

    @Override
    public MatchFilter withField(AnnotatedField field) {
        return twoClauseRewrite(this, a, b, (MatchFilter m) -> m.withField(field), MatchFilterOr::new);
    }

    @Override
    public MatchFilter forLeafReaderContext(LeafReaderContext context) {
        return twoClauseRewrite(this, a, b, (MatchFilter m) -> m.forLeafReaderContext(context), MatchFilterOr::new);
    }

    @Override
    public ConstraintValue evaluate(ForwardIndexDocument fiDoc, MatchInfo[] matchInfo) {
        ConstraintValue ra = a.evaluate(fiDoc, matchInfo);
        if (ra.isTruthy())
            return ra;
        return b.evaluate(fiDoc, matchInfo);
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
        if (x != a || y != b)
            return new MatchFilterOr(x, y);
        return this;
    }

    public List<TextPatternStruct> getClauses() {
        return List.of(a, b);
    }
}
