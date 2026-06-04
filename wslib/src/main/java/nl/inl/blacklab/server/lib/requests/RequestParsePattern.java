package nl.inl.blacklab.server.lib.requests;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.server.lib.ParamUtil;
import nl.inl.blacklab.server.lib.QueryParams;
import nl.inl.blacklab.webservice.WsParam;

public record RequestParsePattern(String bcqlQuery, String queryLanguage, TextPattern textPattern) {
    public static RequestParsePattern fromParams(QueryParams qpar) {
        BlackLabIndex index = ParamUtil.index(qpar.getCorpusName());
        return new RequestParsePattern(
                qpar.get(WsParam.PATTERN),
                qpar.get(WsParam.PATTERN_LANGUAGE),
                ParamUtil.patternNoWithinContextTag(index, qpar.get(WsParam.PATTERN_LANGUAGE),
                                qpar.get(WsParam.PATTERN), qpar.get(WsParam.PATTERN_GAP_DATA))
                        .orElse(null)
        );
    }
}
