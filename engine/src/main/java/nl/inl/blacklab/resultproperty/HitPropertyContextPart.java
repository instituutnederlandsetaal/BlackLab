package nl.inl.blacklab.resultproperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Hits;

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
            throw new RuntimeException("No context parts specified: " + wordSpec);
        if (parts.size() == 1)
            return parts.get(0);
        return new HitPropertyMultiple(parts);
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
     * @param confineToHit Can we only take context from the hit itself, or outside of it as well?
     */
    private record ContextPart(boolean fromHitEnd, int direction, int first, int last, boolean confineToHit) {

        private ContextPart {
            assert Math.abs(direction) == 1;
            assert first >= 0;
            assert last >= 0;
        }

        private static ContextPart forString(String param, ContextSize defaultContextSize) {
            boolean fromHitEnd = false;
            int direction = 1;
            boolean confineToHit = false;
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
                confineToHit = true;
                lastWord = defaultContextSize.maxSnippetHitLength();
                break;
            case PART_AFTER:
            case PART_RIGHT: // (old)
                fromHitEnd = true;
                lastWord = defaultContextSize.after();
                break;
            case PART_MATCH:
            default:
                confineToHit = true;
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
            return new ContextPart(fromHitEnd, direction, firstWord, lastWord, confineToHit);
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

    HitPropertyContextPart(HitPropertyContextPart prop, Hits hits, boolean invert) {
        super(prop, hits, invert, null);
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
    void deserializeParam(String param) {
        part = ContextPart.forString(param, index.defaultContextSize());
        compareInReverse = part.direction == -1;
    }

    @Override
    public List<String> serializeParts() {
        List<String> result = new ArrayList<>(super.serializeParts());
        result.add(3, part.toString()); // before field name
        return result;
    }

    @Override
    public HitProperty copyWith(Hits newHits, boolean invert) {
        return new HitPropertyContextPart(this, newHits, invert);
    }

    @Override
    public void fetchContext() {
        int smaller = Math.min(part.first, part.last);
        int larger = Math.max(part.first, part.last);
        StartEndSetter func;
        if (annotation.field() == hits.field()) {
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
                func = (int[] starts, int[] ends, int j, Hit h) -> {
                    starts[j] = h.end() + smaller;
                    ends[j] = h.end() + larger + 1;
                };
            } else {
                // From hit end backwards.
                if (part.confineToHit) {
                    func = (int[] starts, int[] ends, int j, Hit h) -> {
                        starts[j] = Math.max(h.start(), h.end() - larger);
                        ends[j] = Math.max(h.start(), h.end() - smaller + 1);
                    };
                } else {
                    func = (int[] starts, int[] ends, int j, Hit h) -> {
                        starts[j] = Math.max(0, h.end() - larger);
                        ends[j] = Math.max(0, h.end() - smaller + 1);
                    };
                }
            }
        } else {
            if (part.direction == 1) {
                // From hit start forwards.
                if (part.confineToHit) {
                    func = (int[] starts, int[] ends, int j, Hit h) -> {
                        starts[j] = Math.min(h.end(), h.start() + smaller);
                        ends[j] = Math.min(h.end(), h.start() + larger + 1);
                    };
                } else {
                    func = (int[] starts, int[] ends, int j, Hit h) -> {
                        starts[j] = h.start() + smaller;
                        ends[j] = h.start() + larger + 1;
                    };
                }
            } else {
                func = (int[] starts, int[] ends, int j, Hit h) -> {
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
                func = (int[] starts, int[] ends, int j, Hit hit) -> {
                    int[] startEnd = getForeignHitStartEnd(hit, annotation.field().name());
                    int pos = startEnd[1] == Integer.MIN_VALUE ? hit.end() : startEnd[1];
                    starts[j] = pos + smaller;
                    ends[j] = pos + larger + 1;
                };
            } else {
                // From hit end backwards.
                if (part.confineToHit) {
                    func = (int[] starts, int[] ends, int j, Hit hit) -> {
                        int[] startEnd = getForeignHitStartEnd(hit, annotation.field().name());
                        int start = startEnd[0] == Integer.MAX_VALUE ? hit.start() : startEnd[0];
                        int end = startEnd[1] == Integer.MIN_VALUE ? hit.end() : startEnd[1];
                        starts[j] = Math.max(start, end - larger);
                        ends[j] = Math.max(start, end - smaller + 1);
                    };
                } else {
                    func = (int[] starts, int[] ends, int j, Hit hit) -> {
                        int[] startEnd = getForeignHitStartEnd(hit, annotation.field().name());
                        int end = startEnd[1] == Integer.MIN_VALUE ? hit.end() : startEnd[1];
                        starts[j] = Math.max(0, end - larger);
                        ends[j] = Math.max(0, end - smaller + 1);
                    };
                }
            }
        } else {
            if (part.direction == 1) {
                // From hit start forwards.
                if (part.confineToHit) {
                    func = (int[] starts, int[] ends, int j, Hit hit) -> {
                        int[] startEnd = getForeignHitStartEnd(hit, annotation.field().name());
                        int start = startEnd[0] == Integer.MAX_VALUE ? hit.start() : startEnd[0];
                        int end = startEnd[1] == Integer.MIN_VALUE ? hit.end() : startEnd[1];
                        starts[j] = Math.min(end, start + smaller);
                        ends[j] = Math.min(end, start + larger + 1);
                    };
                } else {
                    func = (int[] starts, int[] ends, int j, Hit hit) -> {
                        int[] startEnd = getForeignHitStartEnd(hit, annotation.field().name());
                        int start = startEnd[0] == Integer.MAX_VALUE ? hit.start() : startEnd[0];
                        starts[j] = start + smaller;
                        ends[j] = start + larger + 1;
                    };
                }
            } else {
                func = (int[] starts, int[] ends, int j, Hit hit) -> {
                    int[] startEnd = getForeignHitStartEnd(hit, annotation.field().name());
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
