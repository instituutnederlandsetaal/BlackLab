package nl.inl.blacklab.server.lib.requests;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.server.lib.QueryParams;
import nl.inl.blacklab.server.lib.WebserviceParams;

public record RequestParsePattern(String bcqlQuery, String queryLanguage, TextPattern textPattern) {
    public static RequestParsePattern fromParams(QueryParams qpar) {
        BlackLabIndex index = WebserviceParams.index(qpar.getCorpusName());
        return new RequestParsePattern(
                qpar.getPattern(),
                qpar.getPattLanguage(),
                WebserviceParams.patternNoWithinContextTag(index, qpar.getPattLanguage(),
                                qpar.getPattern(), qpar.getPattGapData())
                        .orElse(null)
        );
    }
}
