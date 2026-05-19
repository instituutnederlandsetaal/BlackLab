package nl.inl.blacklab.server.lib.results;

import java.util.List;

import org.apache.lucene.document.Document;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.SingleDocIdFilter;
import nl.inl.blacklab.search.extensions.XFRelations;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.hitresults.ContextSize;
import nl.inl.blacklab.search.results.hitresults.HitResults;
import nl.inl.blacklab.search.results.hits.Hits;
import nl.inl.blacklab.search.textpattern.CompleteQuery;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.TextPatternFunctionCall;
import nl.inl.blacklab.search.textpattern.TextPatternValue;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.exceptions.NotFound;
import nl.inl.blacklab.server.lib.requests.RequestDocSnippet;
import nl.inl.util.StringUtil;

public class ResultDocSnippet {

    private final String docPid;

    private HitResults hitResults;

    private ContextSize context;

    private final boolean origContent;

    private final List<Annotation> annotsToWrite;

    ResultDocSnippet(RequestDocSnippet request) {
        docPid = request.docPid();
        BlackLabIndex index = request.field().index();
        int luceneDocId = index.getDocIdFromPid(docPid);
        if (luceneDocId < 0)
            throw new NotFound("DOC_NOT_FOUND", "Document with pid '" + docPid + "' not found.");
        Document document = index.luceneDoc(luceneDocId);
        if (document == null)
            throw new InternalServerError("Couldn't fetch document with pid '" + docPid + "'.",
                    "INTERR_FETCHING_DOCUMENT_SNIPPET");

        // Make sure snippet plus surrounding context don't exceed configured allowable snippet size
        int maxSnippetSize = request.maxSnippetSize();
        if (request.hasHitStartEnd()) {
            // A hit was given, and we want some context around it
            context = request.context();
        } else {
            // Exact start and end positions to return were given
            context = ContextSize.get(0, maxSnippetSize);
        }

        int start = request.start();
        int end = request.end();
        if (start < 0 || end < 0 || context.before() < 0 || context.after() < 0 || start > end) {
            throw new BadRequest("ILLEGAL_BOUNDARIES", "Illegal word boundaries specified. Please check parameters.");
        }

        if (context.isInlineTag()) {
            // Make sure we capture the tag so we can use its boundaries for the snippet
            TextPatternFunctionCall producer = new TextPatternFunctionCall("_fixed", List.of(TextPatternValue.fromObject(start), TextPatternValue.fromObject(end)));
            String tagNameRegex = StringUtil.escapeLuceneRegexCharacters(context.inlineTagName());
            TextPattern pattern = TextPattern.createRelationCapturingWithinQuery(producer, tagNameRegex, XFRelations.DEFAULT_CONTEXT_REL_NAME);
            SingleDocIdFilter filter = new SingleDocIdFilter(luceneDocId);
            CompleteQuery completeQuery = new CompleteQuery(pattern, filter);
            hitResults = index.search(request.field(), request.useCache()).find(completeQuery).execute();
        }
        if (hitResults != null && !hitResults.resultsStats().processedAtLeast(1)) {
            // We couldn't find the tag for the context; use a context of 0 words instead
            hitResults = null;
            context = ContextSize.get(0, maxSnippetSize);
        }
        if (hitResults == null) {
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
            hitResults = HitResults.singleHit(QueryInfo.create(index, request.field()), luceneDocId, start, end);
        }

        origContent = request.concordanceType() == ConcordanceType.CONTENT_STORE;
        annotsToWrite = request.annotsToWrite();
    }

    public String docPid() {
        return docPid;
    }

    public Hits getHits() {
        return hitResults.getHits();
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
