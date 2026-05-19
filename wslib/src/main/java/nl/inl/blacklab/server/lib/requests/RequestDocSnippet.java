package nl.inl.blacklab.server.lib.requests;

import java.util.List;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.results.hitresults.ContextSize;
import nl.inl.blacklab.server.config.BLSConfig;
import nl.inl.blacklab.server.lib.QueryParams;
import nl.inl.blacklab.server.lib.WebserviceParams;

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
        boolean hasHitStartEnd = qpar.getHitStart().isPresent();
        int maxContextSize = config.getParameters().getContextSize().getMaxInt();
        int maxSnippetSize = ContextSize.maxSnippetLengthFromMaxContextSize(maxContextSize);
        BlackLabIndex index = WebserviceParams.index(qpar.getCorpusName());
        return new RequestDocSnippet(
                qpar.getDocPid(),
                WebserviceParams.getAnnotatedField(index, qpar.getFieldName()),
                WebserviceParams.getContext(qpar.getContextParam(), qpar.config()),
                hasHitStartEnd,
                hasHitStartEnd ? qpar.getHitStart().get() : qpar.getWordStart(),
                hasHitStartEnd ? qpar.getHitEnd() : qpar.getWordEnd(),
                maxSnippetSize,
                WebserviceParams.useCache(qpar.getUseCache(), qpar.debugMode()),
                qpar.getConcordanceType(),
                HitsResponseSettings.getAnnotationsToWrite(qpar)
        );
    }
}
