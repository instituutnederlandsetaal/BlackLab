package nl.inl.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import net.jcip.annotations.NotThreadSafe;

/**
 * Performs highlighting of the contents of XML elements that we found hits in.
 * <p>
 * NOTE: this class is not thread-safe. Use a separate instance per thread.
 */
@NotThreadSafe
public class XmlHighlighter {

    /**
     * How to deal with non-well-formed snippets: by e.g. adding an open tag at the
     * beginning for an unmatched closing tag, or by removing the unmatched closing
     * tag.
     */
    public enum UnbalancedTagsStrategy {
        ADD_TAG,
        REMOVE_TAG
    }

    private enum TagType {
        EXISTING_TAG, // an existing tag
        HIGHLIGHT_START, // insert <hl> tag here
        HIGHLIGHT_END, // insert </hl> tag here
        FIX_START, // insert start tag here to fix well-formedness
        FIX_END, // insert end tag here to fix well-formedness
        REMOVE_EXISTING_TAG // remove an unbalanced tag to fix well-formedness
    }

    /**
     * Helper class for highlighting: stores a span in the original content, be it a
     * place to insert a highlight tag, or an existing tag in the original XML.
     */
    private static class TagLocation implements Comparable<TagLocation> {
        /** Counter for assigning unique id to objectNum */
        private static long n = 0;

        static synchronized long getNextUniqueId() {
            return n++;
        }

        /**
         * Whether this is an existing tag from the original content, a start highlight
         * tag to be added, or an end highlight tag to be added.
         */
        TagType type;

        /** Start character position of tag in original content */
        final int start;

        /**
         * End character position of tag in original content. NOTE: this only differs from start
         * if type == EXISTING_TAG. Highlight tags are not in the original content, so
         * there start always equals end.
         */
        final int end;

        /** Start token position, which we'll put in an attribute of the <hl/> tag for the client. */
        final int startTokenPos;

        /** End token position, which we'll put in an attribute of the <hl/> tag for the client. */
        final int endTokenPos;

        /**
         * Our matching tag (the close to this open tag, or vice versa) in
         * original content. Null indicates that this tag was unmatched
         * (which might happen if we're highlighting snippets of a document).
         */
        TagLocation matchingTag;

        /**
         * Unique id for each tag; used as a tie-breaker so sorting is always the same,
         * and end tags always follow their start tags
         */
        public long objectNum;

        /** Highlight start tags get a hit index that will be used for the n attribute
         *  (so we know which fragments form a single hit) */
        public int hitIndex;

        /**
         * For FIX_START/END tags, indicate the tag name to use when insert. For other
         * types, not used.
         */
        String name;

        public TagLocation(TagType type, int start, int end) {
            this(type, start, end, -1, -1);
        }

        public TagLocation(TagType type, int start, int end, int startTokenPos, int endTokenPos) {
            this.type = type;
            this.start = start;
            this.startTokenPos = startTokenPos;
            this.endTokenPos = endTokenPos;
            this.end = end;
            matchingTag = null; // unmatched tag (until we find its match)
            objectNum = getNextUniqueId();
        }

        @Override
        public int compareTo(TagLocation o) {
            if (this == o)
                return 0;
            int a = start, b = o.start;
            if (a == b) {
                a = end;
                b = o.end;
                if (a == b) {
                    // use the objectNum as a tie breaker so sort is always the same,
                    // and end tags always follow their start tags
                    // Note reverse sort for end tags, or we won't encounter them in the right order,
                    // messing with our ability to eliminate empty <hl> tags.
                    return type == TagType.HIGHLIGHT_END || type == TagType.FIX_END ?
                            (int) (o.objectNum - objectNum) : (int) (objectNum - o.objectNum);
                }
            }
            return a - b;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            TagLocation that = (TagLocation) o;
            return start == that.start && end == that.end && objectNum == that.objectNum && type == that.type
                    && matchingTag == that.matchingTag // don't compare objects here (infinite recursion)
                    && Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            // don't include matchingTag object in hashcode (infinite recursion)
            return Objects.hash(type, start, end, matchingTag == null ? 0L : matchingTag.objectNum, objectNum, name);
        }

        @Override
        public String toString() {
            return type + "@" + start;
        }

    }

    /** What tag name to use for highlighting */
    private static final String HIGHLIGHT_TAG_NAME = "hl";

    /**
     * The XML tag to add to the content to signal where highlighting should start.
     */
    private static final String startHighlightTagStart = "<" + HIGHLIGHT_TAG_NAME;

    /**
     * The XML tag to add to the content to signal where highlighting should end.
     */
    private static final String endHighlightTagStart = "</" + HIGHLIGHT_TAG_NAME;

    /**
     * Where the highlighted content is built - therefore, this class is not
     * thread-safe!
     */
    private StringBuilder b;

    /** Remove empty <hl></hl> tags after highlighting? */
    private boolean removeEmptyHlTags = true;

    /**
     * How to fix well-formedness problems? If true, we remove the unbalanced tags;
     * if false (the default) we add extra tags at the start or end to rebalance it.
     */
    private UnbalancedTagsStrategy unbalancedTagsStrategy = UnbalancedTagsStrategy.ADD_TAG;

    /** Currently open highlights in order of start position. */
    private final List<TagLocation> currentHighlightStarts = new ArrayList<>();

    /** Highlight tags just opened, with no other tags or text content added yet.
     * Used to prevent outputting empty highlight tags.
     */
    private final List<TagLocation> highlightsJustOpened = new ArrayList<>();

    /**
     * Given XML content and a sorted list of existing tags and highlight tags to be
     * added, add the tags to the content so the well-formedness of the XML is not
     * affected.
     * <p>
     * Also offers the option of cutting the content to a number of characters (with
     * possibly a small overshoot, because it will try to cut at a word boundary),
     * ignoring tags and maintaining well-formedness.
     *
     * @param xmlContent the XML content to highlight
     * @param tags the existing tags and highlight tags to add. This list must be
     *            sorted!
     * @param stopAfterChars after how many characters of text content to cut this
     *            fragment. -1 = no cutting.
     * @return the highlighted XML content.
     */
    private String highlightInternal(String xmlContent, List<TagLocation> tags, int stopAfterChars) {
        if (stopAfterChars < 0)
            stopAfterChars = xmlContent.length();
        int positionInContent = 0;
        b = new StringBuilder();
        int visibleCharsAdded = 0;
        boolean addVisibleChars = true; // keep adding text content until we reach the preferred length
        boolean wasCut = false;
        for (TagLocation tag : tags) {
            assert tag.start >= positionInContent; // tags should be in order and not overlap
            if (addVisibleChars) {
                String visibleChars = xmlContent.substring(positionInContent, tag.start);
                if (visibleCharsAdded + visibleChars.length() >= stopAfterChars) {
                    visibleChars = StringUtils.abbreviate(visibleChars, "", stopAfterChars - visibleCharsAdded);
                    if (visibleChars.length() < tag.start - positionInContent)
                        wasCut = true;
                    addVisibleChars = false;
                }
                if (!visibleChars.isEmpty()) {
                    b.append(visibleChars);
                    highlightsJustOpened.clear();
                    visibleCharsAdded += visibleChars.length();
                }
            } else {
                if (positionInContent < tag.start) {
                    wasCut = true;
                }
            }
            processTag(xmlContent, tag);
            positionInContent = tag.end;
        }
        if (addVisibleChars) {
            b.append(xmlContent.substring(positionInContent));
            highlightsJustOpened.clear();
        }
        final String optionalEllipsis = wasCut ? "..." : "";
        return StringUtil.trimWhitespace(b.toString()) + optionalEllipsis;
    }

    /**
     * Decide what to do based on the tag type.
     *
     * @param xmlContent the content we're highlighting
     * @param tag        the existing tag or highlight tag to add
     */
    private void processTag(String xmlContent, TagLocation tag) {
        switch (tag.type) {
        case HIGHLIGHT_START:
            startHighlight(tag);
            break;
        case HIGHLIGHT_END:
            endHighlight(tag);
            break;
        case EXISTING_TAG:
            existingTag(tag, xmlContent.substring(tag.start, tag.end));
            break;
        case FIX_START:
            existingTag(tag, "<" + tag.name + ">");
            break;
        case FIX_END:
            existingTag(tag, "</" + tag.name + ">");
            break;
        case REMOVE_EXISTING_TAG:
            // Simply don't add the tag
            break;
        }
    }

    /**
     * Add highlight tag if not already added; increment depth
     * 
     * @param tag where the tag occurs
     */
    private void startHighlight(TagLocation tag) {
        addStartHighlightTag(tag);
        currentHighlightStarts.add(tag);
    }

    /** Decrement depth; End highlight if we're at level 0.
     * <p>
     * Also prevents "empty" <hl> tags (containing only whitespace). If this situation is detected, the highlight start
     * tag previously added is removed again.
     *
     * @param endHlTag end highlight tag we're processing
     */
    private void endHighlight(TagLocation endHlTag) {
        boolean closingDeepestHighlight = currentHighlightStarts.get(currentHighlightStarts.size() - 1) == endHlTag.matchingTag;
        currentHighlightStarts.removeIf(tag -> tag.hitIndex == endHlTag.matchingTag.hitIndex); // remove the matching start tag
        if (removeEmptyHlTags && !highlightsJustOpened.isEmpty() && highlightsJustOpened.get(highlightsJustOpened.size() - 1).hitIndex == endHlTag.matchingTag.hitIndex) {
            // Don't add end tag, so we don't get empty <hl></hl> tags.
            // Instead, remove the start tag and optional whitespace just added.
            int startOfStartTag = b.lastIndexOf(startHighlightTagStart);
            if (startOfStartTag >= 0)
                b.delete(startOfStartTag, b.length());
            highlightsJustOpened.remove(highlightsJustOpened.size() - 1);
        } else {
            // End highlight. Suspend and resume any other highlights if needed.
            int matchingTagStart = endHlTag.matchingTag.start;
            List<TagLocation> suspendedHighlights = closingDeepestHighlight ? Collections.emptyList() :
                    suspendHighlightsIfNeeded(matchingTagStart);
            addEndHighlightTag();
            resumeSuspendedHighlights(suspendedHighlights);
        }
    }

    private void addStartHighlightTag(TagLocation tag) {
        b.append(startHighlightTagStart);
        if (tag != null) {
            b.append(" index=\"");
            b.append(tag.hitIndex);
            if (tag.startTokenPos >= 0) {
                b.append("\" start=\"");
                b.append(tag.startTokenPos);
                b.append("\" end=\"");
                b.append(tag.endTokenPos);
            }
            b.append("\"");
        }
        b.append(">");
        highlightsJustOpened.add(tag);
    }

    private void addEndHighlightTag() {
        b.append(endHighlightTagStart + ">");
        highlightsJustOpened.clear();
    }

    /**
     * We encountered a tag in the content. If we're inside a highlight tag, ends
     * the current highlight, add the existing tag and restart the highlighting.
     * 
     * @param tag where the tag occurs
     * @param str the existing tag encountered.
     */
    private void existingTag(TagLocation tag, String str) {
        // We should possibly suspend highlighting for this tag to maintain well-formedness.
        // Check the current highlighting spans and see if there's any that are not fully contained
        // by or fully contain the existing tag we're currently processing. If so, we must suspend
        // that highlight and any inner highlights as well.
        int matchingTagStart = tag.matchingTag == null ? -1 : tag.matchingTag.start;
        List<TagLocation> suspendedHighlights = suspendHighlightsIfNeeded(matchingTagStart);
        b.append(str);
        highlightsJustOpened.clear();
        resumeSuspendedHighlights(suspendedHighlights);
    }

    private List<TagLocation> suspendHighlightsIfNeeded(int matchingTagPosition) {
        List<TagLocation> highlightsToSuspend = new ArrayList<>();
        boolean suspendTheRest = false;
        for (int i = 0; i < currentHighlightStarts.size(); i++) {
            TagLocation hlStart = currentHighlightStarts.get(i);
            int hlEnd = hlStart.matchingTag == null ? -1 : hlStart.matchingTag.start;
            if (!suspendTheRest && hlStart.start > matchingTagPosition || hlEnd <= matchingTagPosition) {
                // This highlight isn't fully contained within or fully containing the tag, so it must be closed and
                // reopened to maintain well-formedness. All inner highlights must be suspended as well.
                suspendTheRest = true;
            }
            if (suspendTheRest) {
                highlightsToSuspend.add(hlStart);
                currentHighlightStarts.remove(i);
                i--;
            }
        }
        for (int i = highlightsToSuspend.size() - 1; i >= 0; i--) {
            addEndHighlightTag();
        }
        return highlightsToSuspend;
    }

    private void resumeSuspendedHighlights(List<TagLocation> highlightsToSuspend) {
        // Now re-open all the suspended highlights in the correct order (outer first)
        for (TagLocation hl: highlightsToSuspend) {
            addStartHighlightTag(hl);
            TagLocation e = new TagLocation(TagType.HIGHLIGHT_START, hl.end, hl.end, hl.startTokenPos, hl.endTokenPos);
            e.hitIndex = hl.hitIndex;
            e.matchingTag = hl.matchingTag;
            currentHighlightStarts.add(e);
        }
    }

    /** The start and end character position of a hit, used for highlighting the content. */
    public record HitCharSpan(int startChar, int endChar, int startTokenPos, int endTokenPos) {
        public HitCharSpan(int startChar, int endChar) {
            this(startChar, endChar, -1, -1);
        }
    }

    private static void addHitPositionsToTagList(List<TagLocation> tags, List<HitCharSpan> hitSpans, int offset,
            int length) {
        int hitIndex = 0;
        for (HitCharSpan hit: hitSpans) {
            final int a = hit.startChar() - offset;
            if (a < 0)
                continue; // outside highlighting range, or non-highlighting element (e.g. searching for example date range)
            final int b = hit.endChar() - offset;
            if (b > length)
                continue; // outside highlighting range
            assert b >= a;
            TagLocation start = new TagLocation(TagType.HIGHLIGHT_START, a, a, hit.startTokenPos, hit.endTokenPos);
            TagLocation end = new TagLocation(TagType.HIGHLIGHT_END, b, b);
            start.hitIndex = hitIndex;
            start.matchingTag = end;
            end.matchingTag = start;
            tags.add(start);
            tags.add(end);
            hitIndex++;
        }
    }

    /**
     * Given XML content, make a list of tag locations in this content.
     * <p>
     * Note that the XML content is assumed to be (part of) a well-formed XML
     * document. This way we can highlight a whole document or part of a document.
     * It's therefore okay if we encounter close tags at the start that we haven't
     * seen an open tag for, or open tags at the end that we'll never see a close
     * tag for, but if there are other tag errors (e.g. hierarchy errors such as
     * &lt;i&gt;&lt;b&gt;&lt;/i&gt;&lt;/b&gt;) the behaviour of the highlighter is
     * undefined.
     *
     * @param elementContent the XML content
     * @return the list of tag locations, each with type EXISTING_TAG.
     */
    private List<TagLocation> makeTagList(String elementContent) {
        List<TagLocation> tags = new ArrayList<>();

        // Regex for finding all XML tags and comments.
        // Group 1 indicates if this is an open or close tag
        // Group 2 is the tag name
        Pattern xmlTagsAndComments = Pattern.compile("<(?![!?])\\s*(/?)\\s*([^>\\s]+)(\\s+[^>]*)?>|<!--[\\s\\S]*?-->");
        // NOTE below is the version that actually includes CDATA as well, but this leads to a StackOverflowError on some
        // (large) documents contents requests, e.g.
        // /bls/opensonar/docs/WR-P-E-C-0000000129/contents?query=%5Bword%3D%22schip%22%5D&wordstart=7000
        //Pattern xmlTagsCommentsAndCdatas = Pattern.compile("<(?![!?])\\s*(/?)\\s*([^>\\s]+)(\\s+[^>]*)?>|<!--[\\s\\S]*?-->|<!\\[CDATA\\[([^]]|][^]])+]]>");

        Matcher matcher = xmlTagsAndComments.matcher(elementContent);
        List<TagLocation> openTagStack = new ArrayList<>(); // keep track of open tags
        int fixStartTagObjectNum = -1; // when adding start tags to fix well-formedness, number backwards (for correct sorting)
        int findFrom = 0;
        while (matcher.find(findFrom)) {
            findFrom = matcher.end();
            if (matcher.group(0).startsWith("<!")) {
                // This is a comment or CDATA section. Skip it, so we don't match something that looks like a tag inside it.
                continue;
            }
            TagLocation tagLocation = new TagLocation(TagType.EXISTING_TAG, matcher.start(), matcher.end());

            // Keep track of open tags, so we know if the tags are matched
            boolean isOpenTag = matcher.group(1).isEmpty();
            boolean isSelfClosing = isOpenTag && isSelfClosing(matcher.group());
            if (isOpenTag) {
                if (!isSelfClosing) {
                    // Open tag. Add to the stack.
                    openTagStack.add(tagLocation);
                    tagLocation.name = matcher.group(2); // remember in case there's no close tag
                } else {
                    // Self-closing tag. Don't add to stack, link to self
                    tagLocation.matchingTag = tagLocation;
                }
            } else {
                // Close tag. Did we encounter a matching open tag?
                TagLocation openTag = null;
                if (!openTagStack.isEmpty()) {
                    // Yes, this tag is matched. Remove matching tag.
                    openTag = openTagStack.remove(openTagStack.size() - 1);
                    openTag.name = null; // no longer necessary to remember tag name
                } else {
                    // Unmatched closing tag.
                    if (unbalancedTagsStrategy == UnbalancedTagsStrategy.REMOVE_TAG) {
                        // Remove it.
                        tagLocation.type = TagType.REMOVE_EXISTING_TAG;
                    } else {
                        // Insert a dummy open tag at the start
                        // of the content to maintain well-formedness
                        openTag = new TagLocation(TagType.FIX_START, 0, 0);
                        openTag.name = matcher.group(2); // we need to know what tag to insert
                        openTag.objectNum = fixStartTagObjectNum; // to fix sorting
                        fixStartTagObjectNum--;
                        tags.add(openTag);
                    }
                }
                if (openTag != null) {
                    // Link the matching tags together
                    openTag.matchingTag = tagLocation;
                    tagLocation.matchingTag = openTag;
                }
            }

            // Add tag to the tag list
            tags.add(tagLocation);
        }
        // Close any tags still open, in the correct order (for well-formedness)
        for (int i = openTagStack.size() - 1; i >= 0; i--) {
            if (unbalancedTagsStrategy == UnbalancedTagsStrategy.REMOVE_TAG) {
                // Remove the unbalanced tag
                openTagStack.get(i).type = TagType.REMOVE_EXISTING_TAG;
            } else {
                // Add a close tag at the end to fix the unbalanced tag
                TagLocation tagLocation = new TagLocation(TagType.FIX_END, elementContent.length(),
                        elementContent.length());
                tagLocation.name = openTagStack.get(i).name; // we remembered this for this case
                tags.add(tagLocation);
            }
        }
        return tags;
    }

    /**
     * Determines if a tag is a self-closing tag (ends with "/&gt;")
     * 
     * @param tag the tag
     * @return true iff it is self-closing
     */
    private static boolean isSelfClosing(String tag) {
        // Start at the second to last character (skip the '>') and look for slash.
        for (int i = tag.length() - 2; i >= 0; i--) {
            switch (tag.charAt(i)) {
            case '/':
                // Yes, self-closing tag
                return true;
            case ' ':
            case '\t':
            case '\n':
            case '\r':
                // Whitespace; continue
                break;
            default:
                // We found an attribute or the tag name before encountering a slash, so it's not self-closing.
                return false;
            }
        }
        return false;
    }

    /**
     * Highlight a string containing XML tags. The result is still well-formed XML.
     *
     * @param elementContent the string to highlight
     * @param hits where the highlighting tags should go
     * @return the highlighted string
     */
    public String highlight(String elementContent, List<HitCharSpan> hits) {
        return highlight(elementContent, hits, 0);
    }

    /**
     * Highlight part of an XML document.
     * <p>
     * You cut the XML yourself and supply the part you wish to highlight, along
     * with the offset of where you cut (so we know where the highlight tags should
     * go).
     * <p>
     * Missing tags at the beginning or end of the part will be corrected. As long
     * as you cut at tag boundaries (i.e. not within a tag), the result of this
     * method will still be well-formed XML.
     *
     * @param partialContent the (partial) XML to cut and highlight.
     * @param hits the hits to use for highlighting, or null for no highlighting
     * @param offset position of the first character in the string (i.e. what to
     *            subtract from Hit positions to highlight)
     * @return the highlighted (part of the) XML string
     */
    public String highlight(String partialContent, List<HitCharSpan> hits, int offset) {

        // Find all tags in the content and put their positions in a list
        List<TagLocation> tags = makeTagList(partialContent);

        // 2. Put the positions of our hits in the same list and sort it
        if (hits != null)
            addHitPositionsToTagList(tags, hits, offset, partialContent.length());
        tags.sort(Comparator.naturalOrder());

        // Add all the highlight tags in the list into the content,
        // taking care to mainting well-formedness around existing tags
        return highlightInternal(partialContent, tags, -1);
    }

    /**
     * Set whether or not to remove empty <hl></hl> tags at the end of highlighting
     * (which can form due to the process).
     *
     * @param c true iff empty hl tags should be removed
     */
    public void setRemoveEmptyHlTags(boolean c) {
        removeEmptyHlTags = c;
    }

    /**
     * Make a cut XML fragment well-formed.
     * <p>
     * The only requirement is that tags are intact (i.e. xmlFragment doesn't start
     * with "able cellpadding='3'&gt;" or end with "&lt;/bod".
     * <p>
     * The fragment is made well-formed by adding open tags to the beginning or
     * close tags to the end. It is therefore not a generic way of making any
     * non-well-formed document well-formed, it just works for cutting out part of a
     * well-formed document.
     *
     * @return a well-formed fragment
     */
    public String makeWellFormed(String xmlFragment) {
        return highlight(xmlFragment, null, 0);
    }

    /**
     * Set how to fix well-formedness problems.
     * 
     * @param strategy what to do when encountering unbalanced tags.
     */
    public void setUnbalancedTagsStrategy(UnbalancedTagsStrategy strategy) {
        this.unbalancedTagsStrategy = strategy;
    }

}
