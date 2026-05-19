package nl.inl.blacklab.server.lib.requests;

import java.util.Collection;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.server.lib.QueryParams;
import nl.inl.blacklab.server.lib.WebserviceParams;

public record RequestFieldInfo(
        BlackLabIndex index,
        String fieldName,
        boolean includeCustomInfo,
        Collection<String> listValuesFor,
        long limitValues,
        String relClasses,
        boolean relSeparateSpans,
        boolean relOnlySpans) {

    public static RequestFieldInfo fromParams(QueryParams qpar) {
        return new RequestFieldInfo(
                WebserviceParams.index(qpar.getCorpusName()),
                qpar.getFieldName(),
                qpar.getIncludeCustomInfo(),
                qpar.getListValuesFor(),
                qpar.getLimitValues(),
                qpar.getRelClasses(),
                qpar.getRelSeparateSpans(),
                qpar.getRelOnlySpans()
        );
    }
}
