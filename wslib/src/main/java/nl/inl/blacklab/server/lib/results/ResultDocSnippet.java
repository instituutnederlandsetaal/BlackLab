package nl.inl.blacklab.server.lib.results;

import java.util.List;
import java.util.Optional;

import org.apache.lucene.document.Document;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.SingleDocIdFilter;
import nl.inl.blacklab.search.extensions.XFRelations;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryFiltered;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.TextPatternFixedSpan;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.exceptions.NotFound;
import nl.inl.blacklab.server.lib.WebserviceParams;
import nl.inl.blacklab.server.util.BlsUtils;
import nl.inl.util.StringUtil;

public class ResultDocSnippet {

    private final WebserviceParams params;

    private Hits hits;

    private boolean isHit;

    private ContextSize context;

    private final boolean origContent;

    private final List<Annotation> annotsToWrite;

    ResultDocSnippet(WebserviceParams params) {
        this.params = params;

        BlackLabIndex index = params.blIndex();
        AnnotatedField field = params.getAnnotatedField();
        String docPid = params.getDocPid();
        int luceneDocId = BlsUtils.getDocIdFromPid(index, docPid);
        if (luceneDocId < 0)
            throw new NotFound("DOC_NOT_FOUND", "Document with pid '" + docPid + "' not found.");
        Document document = index.luceneDoc(luceneDocId);
        if (document == null)
            throw new InternalServerError("Couldn't fetch document with pid '" + docPid + "'.",
                    "INTERR_FETCHING_DOCUMENT_SNIPPET");

        // Make sure snippet plus surrounding context don't exceed configured allowable snippet size
        int maxContextSize = params.getSearchManager().config().getParameters().getContextSize().getMaxInt();
        int maxSnippetSize = ContextSize.maxSnippetLengthFromMaxContextSize(maxContextSize);

        int start, end;
        isHit = false;
        Optional<Integer> hitStart = params.getHitStart();
        if (hitStart.isPresent()) {
            // A hit was given, and we want some context around it
            start = hitStart.get();
            end = params.getHitEnd();
            context = params.getContext();
            isHit = true;
        } else {
            // Exact start and end positions to return were given
            start = params.getWordStart();
            end = params.getWordEnd();
            context = ContextSize.get(0, maxSnippetSize);
        }

        if (start < 0 || end < 0 || context.before() < 0 || context.after() < 0 || start > end) {
            throw new BadRequest("ILLEGAL_BOUNDARIES", "Illegal word boundaries specified. Please check parameters.");
        }

        if (context.isInlineTag()) {
            // Make sure we capture the tag so we can use its boundaries for the snippet
            TextPatternFixedSpan producer = new TextPatternFixedSpan(start, end);
            String tagNameRegex = StringUtil.escapeLuceneRegexCharacters(context.inlineTagName());
            TextPattern pattern = TextPattern.createRelationCapturingWithinQuery(producer, tagNameRegex, XFRelations.DEFAULT_CONTEXT_REL_NAME);
            QueryExecutionContext queryContext = QueryExecutionContext.get(index,
                    params.getAnnotatedField().mainAnnotation(), MatchSensitivity.SENSITIVE);
            BLSpanQuery query = pattern.translate(queryContext);
            query = new SpanQueryFiltered(query, new SingleDocIdFilter(luceneDocId));
            hits = index.search(field, params.useCache()).find(query).execute();
        }
        if (hits != null && !hits.resultsStats().waitUntil().processedAtLeast(1)) {
            // We couldn't find the tag for the context; use a context of 0 words instead
            hits = null;
            context = ContextSize.get(0, maxSnippetSize);
        }
        if (hits == null) {
            // Limit context if necessary
            // (done automatically as well, but this should ensure equal before/after parts)
            int snippetSize = end - start + context.before() + context.after();
            if (snippetSize > maxSnippetSize) {
                // Snippet too large. Shrink before and after parts to compensate.
                int overshoot = snippetSize - maxSnippetSize;
                int beforeAndAfter = Math.max(1, context.before() + context.after());
                int remainingBeforeAndAfter = beforeAndAfter - overshoot;
                float factor = (float) Math.max(0, remainingBeforeAndAfter) / beforeAndAfter;
                int newBefore = (int)(context.before() * factor);
                int newAfter = (int)(context.after() * factor);
                context = ContextSize.get(newBefore, newAfter, maxSnippetSize);
            }
            hits = Hits.singleton(QueryInfo.create(index, field), luceneDocId, start, end);
        }

        origContent = params.getConcordanceType() == ConcordanceType.CONTENT_STORE;
        annotsToWrite = WebserviceOperations.getAnnotationsToWrite(params);
    }

    public WebserviceParams getParams() {
        return params;
    }

    public Hits getHits() {
        return hits;
    }

    public boolean isHit() {
        return isHit;
    }

    public ContextSize getContext() {
        return context;
    }

    public boolean isOrigContent() {
        return origContent;
    }

    public List<Annotation> getAnnotsToWrite() {
        return annotsToWrite;
    }
}
