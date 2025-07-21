package nl.inl.blacklab.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.DocIdSetIterator;

import nl.inl.blacklab.Constants;
import nl.inl.blacklab.exceptions.BlackLabException;
import nl.inl.blacklab.exceptions.InvalidIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Field;
import nl.inl.blacklab.search.results.EphemeralHit;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.ResultsStats;
import nl.inl.util.XmlHighlighter;
import nl.inl.util.XmlHighlighter.HitCharSpan;

/**
 * Several utility methods related to documents and highlighting.
 */
public class DocUtil {

    private DocUtil() {
    }

    /**
     * Given token start/end positions, get the corresponding character offsets.
     *
     * The field in question must have character offsets stored.
     *
     * @param index our index
     * @param docId document id
     * @param field annotated or metadata field we want offsets for
     * @param startsOfWords token positions we want the starting character offsets for
     * @param endsOfWords token positions we want the ending character offsets for
     * @param fillInDefaultsIfNotFound if some positions could not be found, fill in defaults
     *                                 instead of throwing an exception?
     */
    public static void characterOffsets(BlackLabIndex index, int docId, Field field, int[] startsOfWords, int[] endsOfWords, boolean fillInDefaultsIfNotFound) {
        if (startsOfWords.length == 0)
            return; // nothing to do
        try {
            // Determine lowest and highest word position we'd like to know something about.
            // This saves a bit of time for large result sets.
            int minP = -1, maxP = -1;
            for (int startsOfWord : startsOfWords) {
                if (startsOfWord < minP || minP == -1)
                    minP = startsOfWord;
                if (startsOfWord > maxP)
                    maxP = startsOfWord;
            }
            for (int endsOfWord : endsOfWords) {
                if (endsOfWord < minP || minP == -1)
                    minP = endsOfWord;
                if (endsOfWord > maxP)
                    maxP = endsOfWord;
            }
            if (minP < 0 || maxP < 0)
                throw new InvalidIndex("Can't determine min and max positions");

            String fieldPropName = field.offsetsField();

            org.apache.lucene.index.Terms terms = index.reader().getTermVector(docId, fieldPropName);
            if (terms == null)
                throw new IllegalArgumentException("Field " + fieldPropName + " in doc " + docId + " has no term vector");
            if (!terms.hasPositions())
                throw new IllegalArgumentException(
                        "Field " + fieldPropName + " in doc " + docId + " has no character position information");

            //int lowestPos = -1, highestPos = -1;
            int lowestPosFirstChar = -1, highestPosLastChar = -1;
            int total = startsOfWords.length + endsOfWords.length;
            boolean[] done = new boolean[total]; // NOTE: array is automatically initialized to zeroes!
            int found = 0;

            // Iterate over terms
            TermsEnum termsEnum = terms.iterator();
            while (termsEnum.next() != null) {
                PostingsEnum dpe = termsEnum.postings(null, PostingsEnum.POSITIONS);

                // Iterate over docs containing this term (NOTE: should be only one doc!)
                while (dpe.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                    // Iterate over positions of this term in this doc
                    int positionsRead = 0;
                    int numberOfPositions = dpe.freq();
                    while (positionsRead < numberOfPositions) {
                        int position = dpe.nextPosition();
                        if (position == -1)
                            break;
                        positionsRead++;

                        // Keep track of the lowest and highest char pos, so
                        // we can fill in the character positions we didn't find
                        int startOffset = dpe.startOffset();
                        if (startOffset < lowestPosFirstChar || lowestPosFirstChar == -1) {
                            lowestPosFirstChar = startOffset;
                        }
                        int endOffset = dpe.endOffset();
                        if (endOffset > highestPosLastChar) {
                            highestPosLastChar = endOffset;
                        }

                        // We've calculated the min and max word positions in advance, so
                        // we know we can skip this position if it's outside the range we're interested in.
                        // (Saves a little time for large result sets)
                        if (position < minP || position > maxP) {
                            continue;
                        }

                        for (int m = 0; m < startsOfWords.length; m++) {
                            if (!done[m] && position == startsOfWords[m]) {
                                done[m] = true;
                                startsOfWords[m] = startOffset;
                                found++;
                            }
                        }
                        for (int m = 0; m < endsOfWords.length; m++) {
                            if (!done[startsOfWords.length + m] && position == endsOfWords[m]) {
                                done[startsOfWords.length + m] = true;
                                endsOfWords[m] = endOffset;
                                found++;
                            }
                        }

                        // NOTE: we might be tempted to break here if found == total,
                        // but that would foul up our calculation of highestPosLastChar and
                        // lowestPosFirstChar.
                    }
                }

            }
            if (found < total) {
                if (!fillInDefaultsIfNotFound)
                    throw new InvalidIndex("Could not find all character offsets!");

                if (lowestPosFirstChar < 0 || highestPosLastChar < 0)
                    throw new InvalidIndex("Could not find default char positions!");

                for (int m = 0; m < startsOfWords.length; m++) {
                    if (!done[m])
                        startsOfWords[m] = lowestPosFirstChar;
                }
                for (int m = 0; m < endsOfWords.length; m++) {
                    if (!done[startsOfWords.length + m])
                        endsOfWords[m] = highestPosLastChar;
                }
            }

        } catch (IOException e) {
            throw BlackLabException.wrapRuntime(e);
        }
    }

    private static List<HitCharSpan> getCharacterOffsets(BlackLabIndex index, int docId, Hits hits) {
        if (hits.size() > Constants.JAVA_MAX_ARRAY_SIZE)
            throw new UnsupportedOperationException("Cannot handle more than " + Constants.JAVA_MAX_ARRAY_SIZE + " hits in a single doc");
        int[] starts = new int[(int)hits.size()];
        int[] ends = new int[(int)hits.size()];
        Iterator<EphemeralHit> hitsIt = hits.ephemeralIterator();
        for (int i = 0; i < starts.length; i++) {
            EphemeralHit hit = hitsIt.next(); // hits.get(i);
            starts[i] = hit.start();
            ends[i] = hit.end() - 1; // end actually points to the first word not in the hit, so
                                   // subtract one
        }

        characterOffsets(index, docId, hits.field(), starts, ends, true);

        List<HitCharSpan> hitSpans = new ArrayList<>(starts.length);
        for (int i = 0; i < starts.length; i++) {
            hitSpans.add(new HitCharSpan(starts[i], ends[i]));
        }
        return hitSpans;
    }

    /**
     * Convert start/end word positions to char positions.
     *
     * @param index our index
     * @param docId document id
     * @param field field to use
     * @param startAtWord where to start getting the content (-1 for start of
     *            document, 0 for first word)
     * @param endAtWord where to end getting the content (-1 for end of document)
     * @return the start and end char position as a two element int array (with any
     *         -1's preserved)
     */
    private static int[] startEndWordToCharPos(BlackLabIndex index, int docId, Field field, int startAtWord, int endAtWord) {
        if (startAtWord == -1 && endAtWord == -1) {
            // No need to translate anything
            return new int[] { -1, -1 };
        }

        // Translate word pos to char pos and retrieve content
        // NOTE: this boolean stuff is a bit iffy, but is necessary because
        // getCharacterOffsets doesn't handle -1 to mean start/end of doc.
        // We should probably fix that some time.
        boolean startAtStartOfDoc = startAtWord == -1;
        boolean endAtEndOfDoc = endAtWord == -1;
        int[] starts = { startAtStartOfDoc ? 0 : startAtWord };
        int[] ends = { endAtEndOfDoc ? starts[0] : endAtWord };
        characterOffsets(index, docId, field, starts, ends, true);
        if (startAtStartOfDoc)
            starts[0] = -1;
        if (endAtEndOfDoc)
            ends[0] = -1;
        return new int[] { starts[0], ends[0] };
    }

    /**
     * Get part of a field's contents.
     *
     * The field may be an annotated field with a content store, in which case
     * that is used, or a metadata field where the value is stored.
     *
     * @param index our index
     * @param docId document id
     * @param d Lucene document (or null if not yet loaded)
     * @param field field to get contents for
     * @param startAtChar first character of contents to return, or -1 for start of contents
     * @param endAtChar character after last character of contents to return , or -1 for end of contents
     * @return (part) of the contents
     */
    public static String contentsByCharPos(BlackLabIndex index, int docId, Document d, Field field, int startAtChar, int endAtChar) {
        try {
            if (!field.hasContentStore()) {
                String fieldName = field.contentsFieldName();
                if (d == null)
                    d = index.reader().document(docId, Set.of(fieldName));
                return d.get(fieldName).substring(startAtChar, endAtChar);
            } else {
                return index.contentAccessor(field).getSubstringsFromDocument(docId, new int[] { startAtChar }, new int[] { endAtChar })[0];
            }
        } catch (IOException e) {
            throw new InvalidIndex(e);
        }
    }

    /**
     * Highlight hits in (part of) a field's contents.
     *
     * @param index our index
     * @param docId document id
     * @param hits hits to highlight
     * @param startAtWord starting token position in contents
     * @param endAtWord ending token position in contents (first token not to be returned)
     * @return (part of) the content with highlighting.
     */
    public static String highlightContent(BlackLabIndex index, int docId, Hits hits, int startAtWord, int endAtWord) {
        // Convert word positions to char positions
        int lastWord = endAtWord < 0 ? endAtWord : endAtWord - 1; // if whole content, don't subtract one
        AnnotatedField field = hits.field();
        int[] startEndCharPos = startEndWordToCharPos(index, docId, field, startAtWord, lastWord);

        // Get content by char positions
        int startAtChar = startEndCharPos[0];
        int endAtChar = startEndCharPos[1];
        String content = contentsByCharPos(index, docId, null, field, startAtChar, endAtChar);
        int highlightFromOffset = startAtChar < 0 ? 0 : startAtChar;
        boolean fixUnbalancedTags = startAtWord != -1 || endAtWord != -1; // fix required if part of content
        return highlightContent(index, docId, hits, fixUnbalancedTags, highlightFromOffset, content);
    }

    private static String highlightContent(BlackLabIndex index, int docId, Hits hits, boolean fixUnbalancedTags,
            int highlightFromOffset, String content) {
        // Do we have anything to highlight, or do we have an XML fragment that needs balancing?
        ResultsStats hitsStats = hits.hitsStats();
        if (hitsStats.processedAtLeast(1) || fixUnbalancedTags) {
            // Find the character offsets for the hits and highlight
            List<HitCharSpan> hitSpans = null;
            if (hitsStats.processedAtLeast(1)) // if hits == null, we still want the highlighter to make it well-formed
                hitSpans = getCharacterOffsets(index, docId, hits);
            XmlHighlighter hl = new XmlHighlighter();
            hl.setUnbalancedTagsStrategy(index.defaultUnbalancedTagsStrategy());
            content = hl.highlight(content, hitSpans, highlightFromOffset);
        }
        return content;
    }

    /**
     * Highlight entire document (version).
     *
     * This takes parallel corpora into account, where a single contents store for the entire
     * input file containing all the versions exists, and each version stores a start and end offset.
     *
     * @param index our index
     * @param contentsField field to get contents for
     * @param docId document id
     * @param hits hits to highlight
     * @return document with highlighting
     */
    public static String highlightDocument(BlackLabIndex index, AnnotatedField contentsField, int docId, Hits hits) {
        String contents = index.contentAccessor(contentsField).getDocumentContents(docId);
        return highlightContent(index, docId, hits, false, 0, contents);
    }

    private static String[] getSubstringsFromDocument(BlackLabIndex index,
            int docId, Document d, AnnotatedField field, int[] starts, int[] ends) {
        try {
            ContentAccessor contentAccessor = index.contentAccessor(field);
            if (contentAccessor == null) {
                // No special content accessor set; assume a non-annotated stored field
                String[] content;
                String fieldName = field.contentsFieldName();
                if (d == null)
                    d = index.reader().document(docId, Set.of(fieldName));
                String fieldContent = d.get(fieldName);
                content = new String[starts.length];
                for (int i = 0; i < starts.length; i++) {
                    content[i] = fieldContent.substring(starts[i], ends[i]);
                }
                return content;
            } else {
                // Content accessor set. Use it to retrieve the content.
                return contentAccessor.getSubstringsFromDocument(docId, starts, ends);
            }
        } catch (IOException e) {
            throw new InvalidIndex(e);
        }
    }

    /**
     * Use the content store instead of the forward index to make concordances.
     *
     * This ensures the concordances contain the full original content, not what could
     * be reconstructed from the forward index. This is significantly slower though.
     *
     * @param index our index
     * @param docId document id
     * @param field field to make concordances for
     * @param startsOfWords token position for match starts
     * @param endsOfWords token position for match ends
     * @param hl highlighter to use
     * @return concordances
     */
    public static List<Concordance> makeConcordancesFromContentStore(BlackLabIndex index, int docId,
            AnnotatedField field, int[] startsOfWords, int[] endsOfWords, XmlHighlighter hl) {
        // Determine starts and ends
        int n = startsOfWords.length / 2;
        int[] starts = new int[n];
        int[] ends = new int[n];
        for (int i = 0, j = 0; i < startsOfWords.length; i += 2, j++) {
            starts[j] = startsOfWords[i];
            ends[j] = endsOfWords[i + 1];
        }

        // Retrieve 'em all
        String[] content = getSubstringsFromDocument(index, docId, null, field, starts, ends);

        // Cut 'em up
        List<Concordance> rv = new ArrayList<>();
        for (int i = 0, j = 0; i < startsOfWords.length; i += 2, j++) {
            // Put the concordance in the Hit object
            int absLeft = startsOfWords[i];
            int absRight = endsOfWords[i + 1];
            int relHitLeft = startsOfWords[i + 1] - absLeft;
            int relHitRight = endsOfWords[i] - absLeft;
            String currentContent = content[j];

            // Determine context and build concordance.
            // Note that hit text may be empty for hits of length zero,
            // such as a search for open tags (which have a location but zero length,
            // like a search for a word has a length 1)
            String hitText = relHitRight < relHitLeft ? ""
                    : currentContent.substring(relHitLeft,
                            relHitRight);
            String leftContext = currentContent.substring(0, relHitLeft);
            String rightContext = currentContent.substring(relHitRight, absRight - absLeft);

            // Make each fragment well-formed
            hitText = hl.makeWellFormed(hitText);
            leftContext = hl.makeWellFormed(leftContext);
            rightContext = hl.makeWellFormed(rightContext);

            rv.add(new Concordance(new String[] { leftContext, hitText, rightContext }));
        }
        return rv;
    }

    /**
     * Get the contents of a document.
     *
     * @param index our index
     * @param docId document id
     * @param d Lucene document if available, otherwise null
     * @return contents
     */
    public static String contents(BlackLabIndex index, AnnotatedField field, int docId, Document d) {
        if (field == null)
            field = index.mainAnnotatedField();
        try {
            if (!field.hasContentStore()) {
                // Regular stored field
                String fieldName = field.contentsFieldName();
                if (d == null)
                    d = index.reader().document(docId, Set.of(fieldName));
                // No special content accessor set; assume a stored field
                String content = d.get(fieldName);
                if (content == null)
                    throw new IllegalArgumentException("Field not found: " + field.name());
                return content;
            } else {
                //int[] startEnd = startEndWordToCharPos(index, docId, field, -1, -1);
                return index.contentAccessor(field).getDocumentContents(docId);
            }
        } catch (IOException e) {
            throw new InvalidIndex(e);
        }
    }
}
