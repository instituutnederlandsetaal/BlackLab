package nl.inl.blacklab.resultproperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.NonNull;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.SpanQueryPositionFilter;
import nl.inl.blacklab.search.results.hitresults.ContextSize;
import nl.inl.blacklab.search.results.hits.EphemeralHit;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.TextPatternAnyToken;
import nl.inl.blacklab.search.textpattern.TextPatternLook;
import nl.inl.blacklab.search.textpattern.TextPatternPositionFilter;
import nl.inl.blacklab.search.textpattern.TextPatternSequence;

/**
 * A hit property for sorting on a number of tokens before a hit.
 */
public class HitPropertyContextPart extends HitPropertyContextBase {

    public static final String ID = "ctx";

    public static final char PART_BEFORE = 'B';

    public static final char PART_AFTER = 'A';

    public static final char PART_MATCH_FROM_END = 'E';

    public static final char PART_MATCH = 'H';

    @Deprecated
    public static final char PART_LEFT = 'L';

    @Deprecated
    public static final char PART_RIGHT = 'R';

    static HitPropertyContextPart deserializeProp(BlackLabIndex index, AnnotatedField field, List<String> infos) {
        DeserializeInfos i = deserializeInfos(index, field, infos);
        return new HitPropertyContextPart(index, i.annotation, i.sensitivity, i.extraParam(0));
    }

    static HitProperty deserializePropContextWords(BlackLabIndex index, AnnotatedField field, List<String> infos) {
        DeserializeInfos i = deserializeInfos(index, field, infos);
        return contextWords(index, i.annotation, i.sensitivity, i.extraParam(0, "H1-"));
    }

    public static HitProperty contextWords(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity, String wordSpec) {
        List<HitProperty> parts = new ArrayList<>();
        for (String partSpec: wordSpec.split("\\s*;\\s*")) {
            parts.add(new HitPropertyContextPart(index, annotation, sensitivity, partSpec));
        }
        if (parts.isEmpty())
            throw new InvalidQuery("No context parts specified: " + wordSpec);
        if (parts.size() == 1)
            return parts.get(0);
        return new HitPropertyMultiple(parts);
    }

    @Override
    public boolean canRefineQuery() {
        return true;
    }

    @Override
    @NonNull RefiningQuery refineQuery(RefiningQuery original, TextPattern propTextPattern) {
        ContextPart contextPart = getContextPart();
        int skipTokens = contextPart.first();
        TextPattern tp;
        if (contextPart.direction() < 0) {
            // for forward direction, first=0 is the first possible token to look at (i.e. first token in hit)
            // for backwards direction, first=1 is the first possible token to look at (i.e. firt token before the hit)
            // We decrement skipTokens to account for this.
            skipTokens -= 1;
            if (skipTokens > 0)
                propTextPattern = new TextPatternSequence(propTextPattern, new TextPatternAnyToken(skipTokens));
            if (contextPart.fromHitEnd()) {
                // From hit end backwards (confined to hit)
                tp = new TextPatternPositionFilter(original.pattern(),
                        propTextPattern, SpanQueryPositionFilter.Operation.CONTAINING_AT_END);
            } else {
                // From hit start backwards (using lookbehind)
                TextPatternLook lookbehind = new TextPatternLook(propTextPattern, true, false);
                tp = new TextPatternSequence(lookbehind, original.pattern());
            }
        } else {
            propTextPattern = new TextPatternSequence(new TextPatternAnyToken(skipTokens), propTextPattern);
            if (contextPart.fromHitEnd()) {
                // From hit end forwards (using lookahead)
                TextPatternLook lookahead = new TextPatternLook(propTextPattern, false, false);
                tp = new TextPatternSequence(original.pattern(), lookahead);
            } else {
                // From hit start forwards (confined to hit)
                tp = new TextPatternPositionFilter(original.pattern(),
                        propTextPattern, SpanQueryPositionFilter.Operation.CONTAINING_AT_START);
            }
        }
        return original.withPattern(tp);
    }

    public ContextPart getContextPart() {
        return part;
    }

    public boolean isHitText() {
        if (!part.fromHitEnd && part.direction > 0 || part.fromHitEnd && part.direction < 0) {
            if (part.first == 0 && part.last == ContextSize.MAX_HIT_SIZE)
                return true;
        }
        return false;
    }

    public HitProperty asHitText() {
        return isHitText() ? new HitPropertyHitText(index, annotation, sensitivity) : null;
    }

    /**
     * A stretch of words from the (surroundings of) the matched text.
     *
     * @param fromHitEnd   Do we start counting from the end of the hit instead of the start?
     *                     This determines what token corresponds to index 0: if false, the first
     *                     token of the hit. If true, the first token AFTER the hit.
     * @param direction    Direction: 1 = forward, -1 = backward.
     * @param first        What's the first token we're interested in?
     * @param last         What's the last token we're interested in?
     */
    public record ContextPart(boolean fromHitEnd, int direction, int first, int last) {

        public ContextPart {
            assert Math.abs(direction) == 1;
            assert first >= 0;
            assert last >= 0;
        }

        public static ContextPart forString(String param, ContextSize defaultContextSize) {
            boolean fromHitEnd = false;
            int direction = 1;
            int lastWord;
            switch (param.charAt(0)) {
            case PART_BEFORE:
            case PART_LEFT: // (old)
                direction = -1;
                lastWord = defaultContextSize.before();
                break;
            case PART_MATCH_FROM_END:
                fromHitEnd = true;
                direction = -1;
                lastWord = defaultContextSize.maxSnippetHitLength();
                break;
            case PART_AFTER:
            case PART_RIGHT: // (old)
                fromHitEnd = true;
                lastWord = defaultContextSize.after();
                break;
            case PART_MATCH:
            default:
                lastWord = defaultContextSize.maxSnippetHitLength();
                break;
            }
            int firstWord = 0;
            if (param.length() > 1) {
                if (param.contains("-")) {
                    // Two numbers, or a number followed by a dash ("until end of part")
                    String[] numbers = param.substring(1).split("-");
                    try {
                        firstWord = Integer.parseInt(numbers[0]) - 1;
                        if (numbers.length > 1)
                            lastWord = Integer.parseInt(numbers[1]) - 1;
                    } catch (NumberFormatException e) {
                        // ignore and accept the defaults
                    }
                } else {
                    // Single number: single word
                    firstWord = lastWord = Integer.parseInt(param.substring(1)) - 1;
                }
            }
            if (direction == -1) {
                // We want to start left of the hit or from the last token inside the hit
                firstWord++;
                lastWord++;
            }
            return new ContextPart(fromHitEnd, direction, firstWord, lastWord);
        }

        @Override
        public String toString() {
            char anchor = fromHitEnd ? (direction == 1 ? PART_AFTER : PART_MATCH_FROM_END) :
                    (direction == 1 ? PART_MATCH : PART_BEFORE);
            int from = first + (direction == 1 ? 1 : 0); // (1-based)
            int to = last + (direction == 1 ? 1 : 0);
            if (from == to)
                return "" + anchor + from;
            return "" + anchor + from + "-" + (to == -1 ? "" : to);
        }

        /**
         * When we get the fragment of context, do we compare it from the start to the end (normal, false) or the
         * end to the start (in reverse, true)?
         *
         * @return true if we need to start comparing from the end of the context fragment, false otherwise
         */
        public boolean compareInReverse() {
            return direction == 1 ? first > last : first < last;
        }
    }

    /** Description of the context to use (starting point, direction, start/end index) */
    private ContextPart part;

    HitPropertyContextPart(HitPropertyContextPart prop, PropContext context, boolean invert) {
        super(prop, context, invert, null);
        this.part = prop.part;
    }

    public HitPropertyContextPart(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity, String partSpec) {
        this(index, annotation, sensitivity, ContextPart.forString(partSpec, index.defaultContextSize()));
    }

    public HitPropertyContextPart(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity, ContextPart part) {
        super("context part", ID, index, annotation, sensitivity, false);
        this.part = part;
        this.compareInReverse = part != null && part.compareInReverse();
    }

    @Override
    public List<String> serializeParts() {
        List<String> result = new ArrayList<>(super.serializeParts());
        result.add(3, part.toString()); // before field name
        return result;
    }

    @Override
    public HitProperty copyWith(PropContext context, boolean invert) {
        return new HitPropertyContextPart(this, context, invert);
    }

    @Override
    public void fetchContext() {
        int smaller = Math.min(part.first, part.last);
        int larger = Math.max(part.first, part.last);
        StartEndSetter func;
        if (annotation.field() == context.hits().field()) {
            // Regular hit; use start and end offsets from the hit itself
            func = fetchContextRegular(smaller, larger);
        } else {
            // We must be searching a parallel corpus and grouping/sorting on one of the target fields.
            // Determine start and end using matchInfo instead.
            func = fetchContextParallel(smaller, larger);
        }
        fetchContext(func);
    }

    private StartEndSetter fetchContextRegular(int smaller, int larger) {
        StartEndSetter func;
        if (part.fromHitEnd) {
            if (part.direction == 1) {
                // From hit end forwards.
                func = (int[] starts, int[] ends, int j, EphemeralHit h) -> {
                    starts[j] = h.end() + smaller;
                    ends[j] = h.end() + larger + 1;
                };
            } else {
                // From hit end backwards.
                func = (int[] starts, int[] ends, int j, EphemeralHit h) -> {
                    starts[j] = Math.max(h.start(), h.end() - larger);
                    ends[j] = Math.max(h.start(), h.end() - smaller + 1);
                };
            }
        } else {
            if (part.direction == 1) {
                // From hit start forwards.
                func = (int[] starts, int[] ends, int j, EphemeralHit h) -> {
                    starts[j] = Math.min(h.end(), h.start() + smaller);
                    ends[j] = Math.min(h.end(), h.start() + larger + 1);
                };
            } else {
                // From hit start backwards.
                func = (int[] starts, int[] ends, int j, EphemeralHit h) -> {
                    starts[j] = Math.max(0, h.start() - larger);
                    ends[j] = Math.max(0, h.start() - smaller + 1);
                };
            }
        }
        return func;
    }

    private StartEndSetter fetchContextParallel(int smaller, int larger) {
        StartEndSetter func;
        if (part.fromHitEnd) {
            if (part.direction == 1) {
                // From hit end forwards.
                func = (int[] starts, int[] ends, int j, EphemeralHit hit) -> {
                    int[] startEnd = getForeignHitStartEnd(hit, annotation.field());
                    int pos = startEnd[1] == Integer.MIN_VALUE ? hit.end() : startEnd[1];
                    starts[j] = pos + smaller;
                    ends[j] = pos + larger + 1;
                };
            } else {
                // From hit end backwards.
                func = (int[] starts, int[] ends, int j, EphemeralHit hit) -> {
                    int[] startEnd = getForeignHitStartEnd(hit, annotation.field());
                    int start = startEnd[0] == Integer.MAX_VALUE ? hit.start() : startEnd[0];
                    int end = startEnd[1] == Integer.MIN_VALUE ? hit.end() : startEnd[1];
                    starts[j] = Math.max(start, end - larger);
                    ends[j] = Math.max(start, end - smaller + 1);
                };
            }
        } else {
            if (part.direction == 1) {
                // From hit start forwards.
                func = (int[] starts, int[] ends, int j, EphemeralHit hit) -> {
                    int[] startEnd = getForeignHitStartEnd(hit, annotation.field());
                    int start = startEnd[0] == Integer.MAX_VALUE ? hit.start() : startEnd[0];
                    int end = startEnd[1] == Integer.MIN_VALUE ? hit.end() : startEnd[1];
                    starts[j] = Math.min(end, start + smaller);
                    ends[j] = Math.min(end, start + larger + 1);
                };
            } else {
                // From hit start backwards.
                func = (int[] starts, int[] ends, int j, EphemeralHit hit) -> {
                    int[] startEnd = getForeignHitStartEnd(hit, annotation.field());
                    int start = startEnd[0] == Integer.MAX_VALUE ? hit.start() : startEnd[0];
                    starts[j] = Math.max(0, start - larger);
                    ends[j] = Math.max(0, start - smaller + 1);
                };
            }
        }
        return func;
    }

    @Override
    public boolean isDocPropOrHitText() {
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        if (!super.equals(o))
            return false;
        HitPropertyContextPart that = (HitPropertyContextPart) o;
        return part.equals(that.part);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), part);
    }
}
