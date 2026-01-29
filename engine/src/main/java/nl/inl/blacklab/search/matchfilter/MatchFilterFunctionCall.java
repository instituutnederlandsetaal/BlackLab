package nl.inl.blacklab.search.matchfilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.plugins.QueryFunction;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.ForwardIndexDocument;
import nl.inl.blacklab.search.lucene.HitQueryContext;
import nl.inl.blacklab.search.lucene.MatchInfo;

public class MatchFilterFunctionCall extends MatchFilter {

    private final List<MatchFilter> parameters;

    private final QueryFunction func;

    public MatchFilterFunctionCall(QueryFunction func, List<MatchFilter> parameters) {
        this.func = func;
        this.parameters = parameters;
    }

    @Override
    public String toString() {
        return func.getName() + "(" + StringUtils.join(parameters, ", ") + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        MatchFilterFunctionCall that = (MatchFilterFunctionCall) o;
        return Objects.equals(parameters, that.parameters)
                && Objects.equals(func, that.func);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameters, func);
    }

    @Override
    public void setHitQueryContext(HitQueryContext context) {
        for (MatchFilter p: parameters) {
            p.setHitQueryContext(context);
        }
    }

    @Override
    public ConstraintValue evaluate(ForwardIndexDocument fiDoc, MatchInfo[] matchInfo) {
        List<Object> evaluatedParams = parameters.stream()
                .map(p -> p.evaluate(fiDoc, matchInfo))
                .map(ConstraintValue::getValue)
                .toList();
        return (ConstraintValue)func.apply(null, evaluatedParams);
    }

    @Override
    public void lookupAnnotationIndices(ForwardIndexAccessor fiAccessor) {
        for (MatchFilter p: parameters) {
            p.lookupAnnotationIndices(fiAccessor);
        }
    }

    @Override
    public MatchFilter rewrite() {
        List<MatchFilter> rewritten = new ArrayList<>();
        boolean changed = false;
        for (MatchFilter p: parameters) {
            MatchFilter r = p.rewrite();
            if (r != p)
                changed = true;
            rewritten.add(r);
        }
        return changed ? new MatchFilterFunctionCall(func, rewritten) : this;
    }
}
