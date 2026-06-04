package nl.inl.blacklab.server.lib.requests;

import java.util.Collection;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.server.lib.ParamUtil;
import nl.inl.blacklab.server.lib.QueryParams;
import nl.inl.blacklab.webservice.WsParam;

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
                ParamUtil.index(qpar.getCorpusName()),
                qpar.get(WsParam.FIELD),
                qpar.getBool(WsParam.INCLUDE_CUSTOM_INFO),
                qpar.getList(WsParam.LIST_VALUES_FOR_ANNOTATIONS),
                qpar.getLong(WsParam.LIMIT_VALUES),
                qpar.get(WsParam.REL_CLASSES),
                qpar.getBool(WsParam.REL_SEPARATE_SPANS),
                qpar.getBool(WsParam.REL_ONLY_SPANS)
        );
    }
}
