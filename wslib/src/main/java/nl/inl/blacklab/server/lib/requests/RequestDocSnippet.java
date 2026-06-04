package nl.inl.blacklab.server.lib.requests;

import java.util.List;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.results.hitresults.ContextSize;
import nl.inl.blacklab.server.config.BLSConfig;
import nl.inl.blacklab.server.lib.ParamUtil;
import nl.inl.blacklab.server.lib.QueryParams;
import nl.inl.blacklab.webservice.WsParam;

public record RequestDocSnippet(
        String docPid,
        AnnotatedField field,
        ContextSize context,
        boolean hasHitStartEnd,
        int start,
        int end,
        int maxSnippetSize,
        boolean useCache,
        ConcordanceType concordanceType,
        List<Annotation> annotsToWrite
) {
    public static RequestDocSnippet fromParams(QueryParams qpar) {
        BLSConfig config = qpar.config();
        boolean hasHitStartEnd = qpar.optInteger(WsParam.HIT_START).isPresent();
        int maxContextSize = config.getParameters().getContextSize().getMaxInt();
        int maxSnippetSize = ContextSize.maxSnippetLengthFromMaxContextSize(maxContextSize);
        BlackLabIndex index = ParamUtil.index(qpar.getCorpusName());
        return new RequestDocSnippet(
                qpar.get(WsParam.DOC_PID),
                ParamUtil.getAnnotatedField(index, qpar.get(WsParam.FIELD)),
                ParamUtil.getContext(qpar),
                hasHitStartEnd,
                hasHitStartEnd ? qpar.optInteger(WsParam.HIT_START).get() : qpar.getInt(WsParam.WORD_START),
                hasHitStartEnd ? qpar.getInt(WsParam.HIT_END) : qpar.getInt(WsParam.WORD_END),
                maxSnippetSize,
                ParamUtil.useCache(qpar.getBool(WsParam.USE_CACHE), qpar.debugMode()),
                ParamUtil.getConcordanceType(qpar.get(WsParam.USE_CONTENT)),
                HitsResponseSettings.getAnnotationsToWrite(qpar)
        );
    }
}
