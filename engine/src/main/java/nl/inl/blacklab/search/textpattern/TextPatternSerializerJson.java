package nl.inl.blacklab.search.textpattern;

import static nl.inl.blacklab.search.textpattern.TextPattern.MAX_UNLIMITED;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.RelationInfo;
import nl.inl.blacklab.search.lucene.SpanQueryExpansion;
import nl.inl.blacklab.search.lucene.SpanQueryPositionFilter;
import nl.inl.blacklab.search.lucene.SpanQueryRelations;
import nl.inl.blacklab.search.matchfilter.MatchFilter;
import nl.inl.blacklab.search.matchfilter.MatchFilterAnd;
import nl.inl.blacklab.search.matchfilter.MatchFilterCompare;
import nl.inl.blacklab.search.matchfilter.MatchFilterEquals;
import nl.inl.blacklab.search.matchfilter.MatchFilterFunctionCall;
import nl.inl.blacklab.search.matchfilter.MatchFilterImplication;
import nl.inl.blacklab.search.matchfilter.MatchFilterNot;
import nl.inl.blacklab.search.matchfilter.MatchFilterOr;
import nl.inl.blacklab.search.matchfilter.MatchFilterSameTokens;
import nl.inl.blacklab.search.matchfilter.MatchFilterString;
import nl.inl.blacklab.search.matchfilter.MatchFilterTokenAnnotation;
import nl.inl.blacklab.search.matchfilter.MatchFilterTokenAnnotationEqualsString;
import nl.inl.blacklab.search.matchfilter.TextPatternStruct;
import nl.inl.blacklab.util.ObjectSerializationWriter;

/**
 * A Jackson serializer for TextPattern.
 * <p>
 * Used to convert TextPattern to a JSON structure.
 */
public class TextPatternSerializerJson extends JsonSerializer<TextPatternStruct> {

    @Override
    public void serialize(TextPatternStruct pattern, JsonGenerator gen, SerializerProvider serializerProvider) {
        serialize(pattern, (type, args) -> {
            Map<String, Object> map = mapFromArgs(args);
            try {
                gen.writeStartObject();
                {
                    gen.writeStringField("type", type);
                    for (Map.Entry<String, Object> e: map.entrySet()) {
                        Object value = e.getValue();
                        if (value != null) {
                            gen.writeFieldName(e.getKey());
                            if (e.getKey().equals(KEY_ATTRIBUTES)) {
                                // Attributes in "tags" node. Special case because match values can now be an int range
                                // as well as (the more common) regex.
                                // (we could have made MatchValue a TextPatternStruct, but that would change
                                //  the JSON structure (each attribute value would be a JSON object with a type).
                                //  We want to keep everything as-is while also adding the int range filter option)
                                gen.writeStartObject();
                                Map<String, MatchValue> attributes = (Map<String, MatchValue>) value;
                                for (Map.Entry<String, MatchValue> attr: attributes.entrySet()) {
                                    gen.writeFieldName(attr.getKey());
                                    if (attr.getValue() instanceof MatchValueIntRange) {
                                        // Int range; serialize as an object with min and max fields
                                        MatchValueIntRange range = (MatchValueIntRange) attr.getValue();
                                        gen.writeStartObject();
                                        gen.writeNumberField(KEY_MIN, range.getMin());
                                        gen.writeNumberField(KEY_MAX, range.getMax());
                                        gen.writeEndObject();
                                    } else {
                                        // Regex; serialize as a simple string (as we have always done)
                                        gen.writeString(attr.getValue().getRegex());
                                    }
                                }
                                gen.writeEndObject();
                            } else
                                serializerProvider.defaultSerializeValue(value, gen);
                        }
                    }
                }
                gen.writeEndObject();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static Map<String, Object> mapFromArgs(Object[] args) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            assert args[i] instanceof String; // key, value, key, value, ...
            map.put((String)args[i], args[i + 1]);
        }
        return map;
    }

    interface NodeSerializer {
        void serialize(TextPatternStruct pattern, ObjectSerializationWriter writer);
    }

    private static final Map<Class<? extends TextPatternStruct>, NodeSerializer> jsonSerializers = new LinkedHashMap<>();

    // Keys used in serialization
    private static final String KEY_ADJUST = "adjust";
    private static final String KEY_ADJUST_LEADING = "adjustLeading";
    private static final String KEY_ADJUST_TRAILING = "adjustTrailing";
    private static final String KEY_ALIGNMENT = "alignment";
    private static final String KEY_ANNOTATION = "annotation";
    private static final String KEY_ARGS = "args";
    static final String KEY_ATTRIBUTES = "attributes";
    private static final String KEY_CAPTURE = "capture"; // capture, tags
    private static final String KEY_CAPTURES = "captures";
    private static final String KEY_CHILDREN = "children";
    private static final String KEY_CLAUSE = "clause";
    private static final String KEY_CLAUSES = "clauses";
    private static final String KEY_CONSTRAINT = "constraint";
    private static final String KEY_DIRECTION = "direction";
    private static final String KEY_END = "end";
    private static final String KEY_EXCLUDE = "exclude";
    private static final String KEY_FILTER = "filter";
    private static final String KEY_INCLUDE = "include";
    private static final String KEY_INVERT = "invert";
    static final String KEY_MAX = "max"; // (same)
    static final String KEY_MIN = "min"; // repeat, ngrams, anytoken
    private static final String KEY_NAME = "name"; // annotation, function
    private static final String KEY_NEGATE = "negate";
    private static final String KEY_OPERATION = "operation"; // posfilter, ngrams
    private static final String KEY_OPTIONAL = "optional"; // relation target
    private static final String KEY_PARENT = "parent";
    private static final String KEY_PRODUCER = "producer";
    private static final String KEY_REL_SPAN_MODE = "spanMode";
    private static final String KEY_REL_TYPE = "relType";
    private static final String KEY_SENSITIVITY = "sensitivity";
    private static final String KEY_SETTINGS = "settings";
    private static final String KEY_START = "start";
    private static final String KEY_TARGET_VERSION = "targetVersion";
    private static final String KEY_WHERE = "where";
    private static final String KEY_VALUE = "value"; // term, regex, etc.

    static {
        // For each node type, add a CQL serializer to the map.

        // AND
        jsonSerializers.put(TextPatternAnd.class, (pattern, writer) -> {
            writer.write(TextPattern.NT_AND, KEY_CLAUSES, ((TextPatternAnd)pattern).getClauses());
        });

        // ANDNOT
        //noinspection deprecation
//        jsonSerializers.put(TextPatternAndNot.class, (pattern, writer) -> {
//            TextPatternAndNot tp = (TextPatternAndNot) pattern;
//            writer.write(TextPattern.NT_ANDNOT, KEY_INCLUDE, tp.getInclude(), KEY_EXCLUDE, tp.getExclude());
//        });

        // Anytoken
        jsonSerializers.put(TextPatternAnyToken.class, (pattern, writer) -> {
            TextPatternAnyToken tp = (TextPatternAnyToken) pattern;
            writer.write(TextPattern.NT_ANYTOKEN, KEY_MIN, tp.getMin(), KEY_MAX, nullIfUnlimited(tp.getMax()));
        });

        // Capture
        jsonSerializers.put(TextPatternCaptureGroup.class, (pattern, writer) -> {
            TextPatternCaptureGroup tp = (TextPatternCaptureGroup) pattern;
            writer.write(TextPattern.NT_CAPTURE, KEY_CLAUSE, tp.getClause(), KEY_CAPTURE, tp.getCaptureName());
        });

        // Constrained
        jsonSerializers.put(TextPatternConstrained.class, (pattern, writer) -> {
            TextPatternConstrained tp = (TextPatternConstrained) pattern;
            writer.write(TextPattern.NT_CONSTRAINED, KEY_CLAUSE, tp.getClause(), KEY_CONSTRAINT, tp.getConstraint());
        });

        // Default value
        jsonSerializers.put(TextPatternDefaultValue.class, (pattern, writer) -> {
            writer.write(TextPattern.NT_DEFVAL);
        });

        // Lookahead/lookbehind
        jsonSerializers.put(TextPatternLook.class, (pattern, writer) -> {
            TextPatternLook tp = (TextPatternLook) pattern;
            writer.write(TextPattern.NT_LOOK,
                KEY_WHERE, tp.isLookBehind() ? "behind" : "ahead",
                    KEY_NEGATE, tp.isNegate(),
                    KEY_CLAUSE, tp.getClause());
        });

        // Expansion
        jsonSerializers.put(TextPatternExpansion.class, (pattern, writer) -> {
            TextPatternExpansion tp = (TextPatternExpansion) pattern;
            writer.write(TextPattern.NT_EXPANSION,
                    KEY_CLAUSE, tp.getClause(),
                    KEY_DIRECTION, tp.getDirection().toString(),
                    KEY_MIN, tp.getMin(),
                    KEY_MAX, nullIfUnlimited(tp.getMax()));
        });

        // FilterNGrams
        jsonSerializers.put(TextPatternFilterNGrams.class, (pattern, writer) -> {
            TextPatternFilterNGrams tp = (TextPatternFilterNGrams) pattern;
            writer.write(TextPattern.NT_FILTERNGRAMS,
                    KEY_CLAUSE, tp.getClause(),
                    KEY_OPERATION, tp.getOperation().toString(),
                    KEY_MIN, tp.getMin(),
                    KEY_MAX, nullIfUnlimited(tp.getMax()));
        });

        // FixedSpan
        jsonSerializers.put(TextPatternFixedSpan.class, (pattern, writer) -> {
            TextPatternFixedSpan tp = (TextPatternFixedSpan) pattern;
            writer.write(TextPattern.NT_FIXEDSPAN, KEY_START, tp.getStart(), KEY_END, tp.getEnd());
        });

        // IntRange
        jsonSerializers.put(TextPatternIntRange.class, (pattern, writer) -> {
            TextPatternIntRange tp = (TextPatternIntRange) pattern;
            writer.write(TextPattern.NT_INT_RANGE,
                    KEY_MIN, tp.getMin(),
                    KEY_MAX, tp.getMax(),
                    KEY_ANNOTATION, tp.getAnnotation());    // (omitted if null)
        });

        // Not
        jsonSerializers.put(TextPatternNot.class, (pattern, writer) -> {
            writer.write(TextPattern.NT_NOT, KEY_CLAUSE, ((TextPatternNot) pattern).getClause());
        });

        // Or
        jsonSerializers.put(TextPatternOr.class, (pattern, writer) -> {
            writer.write(TextPattern.NT_OR, KEY_CLAUSES, ((TextPatternOr) pattern).getClauses());
        });

        // PositionFilter
        jsonSerializers.put(TextPatternPositionFilter.class, (pattern, writer) -> {
            TextPatternPositionFilter tp = (TextPatternPositionFilter) pattern;
            writer.write(TextPattern.NT_POSFILTER,
                    KEY_PRODUCER, tp.getProducer(),
                    KEY_FILTER, tp.getFilter(),
                    KEY_OPERATION, tp.getOperation().toString(),
                    KEY_INVERT, nullIf(tp.isInvert(), false),
                    KEY_ADJUST_LEADING, nullIf(tp.getAdjustLeading(), 0),
                    KEY_ADJUST_TRAILING, nullIf(tp.getAdjustTrailing(), 0));
        });

        // Overlapping
        jsonSerializers.put(TextPatternOverlapping.class, (pattern, writer) -> {
            TextPatternOverlapping tp = (TextPatternOverlapping) pattern;
            writer.write(TextPattern.NT_OVERLAPPING,
                    KEY_CLAUSES, Arrays.asList(tp.getLeft(), tp.getRight()),
                    KEY_OPERATION, tp.getOperation());
        });

        // QueryFunction
        jsonSerializers.put(TextPatternQueryFunction.class, (pattern, writer) -> {
            TextPatternQueryFunction tp = (TextPatternQueryFunction) pattern;
            writer.write(TextPattern.NT_CALLFUNC,KEY_NAME, tp.getName(), KEY_ARGS, tp.getArgs());
        });

        // Regex
        jsonSerializers.put(TextPatternRegex.class, (pattern, writer) -> {
            TextPatternRegex tp = (TextPatternRegex) pattern;
            writer.write(TextPattern.NT_REGEX,
                    KEY_VALUE, tp.getValue(),
                    KEY_ANNOTATION, tp.getAnnotation(),    // (omitted if null)
                    KEY_SENSITIVITY, sensitivity(tp.getSensitivity())); // (omitted if null)
        });

        // Relation match
        jsonSerializers.put(TextPatternRelationMatch.class, (pattern, writer) -> {
            TextPatternRelationMatch tp = (TextPatternRelationMatch) pattern;
            writer.write(TextPattern.NT_RELATION_MATCH,
                    KEY_PARENT, tp.getParent(),
                    KEY_CHILDREN, tp.getChildren());
        });

        // Relation target
        jsonSerializers.put(RelationTarget.class, (pattern, writer) -> {
            RelationTarget tp = (RelationTarget) pattern;
            RelationOperatorInfo operatorInfo = tp.getOperatorInfo();
            writer.write(TextPattern.NT_RELATION_TARGET,
                    KEY_REL_TYPE, operatorInfo.getTypeRegex(),
                    KEY_CLAUSE, tp.getTarget(),
                    KEY_NEGATE, nullIf(operatorInfo.isNegate(), false),
                    KEY_REL_SPAN_MODE, nullIf(tp.getSpanMode().getCode(), "source"),
                    KEY_DIRECTION, nullIf(operatorInfo.getDirection().getCode(), "both"),
                    KEY_CAPTURE, nullIfEmpty(tp.getCaptureAs()),
                    KEY_TARGET_VERSION, nullIfEmpty(operatorInfo.getTargetVersion()),
                    KEY_ALIGNMENT, nullIf(operatorInfo.isAlignment(), false),
                    KEY_OPTIONAL, nullIf(operatorInfo.isOptionalMatch(), false));
        });

        // Repetition
        jsonSerializers.put(TextPatternRepetition.class, (pattern, writer) -> {
            TextPatternRepetition tp = (TextPatternRepetition) pattern;
            writer.write(TextPattern.NT_REPEAT,
                    KEY_CLAUSE, tp.getClause(),
                    KEY_MIN, tp.getMin(),
                    KEY_MAX, nullIfUnlimited(tp.getMax()));
        });

        // Sequence
        jsonSerializers.put(TextPatternSequence.class, (pattern, writer) -> {
            writer.write(TextPattern.NT_SEQUENCE, KEY_CLAUSES, ((TextPatternSequence) pattern).getClauses());
        });

        // Settings
        jsonSerializers.put(TextPatternSettings.class, (pattern, writer) -> {
            writer.write(TextPattern.NT_SETTINGS,
                    KEY_CLAUSE, ((TextPatternSettings) pattern).getClause(),
                    KEY_SETTINGS, ((TextPatternSettings) pattern).getSettings());
        });

        // Tags
        jsonSerializers.put(TextPatternTags.class, (pattern, writer) -> {
            TextPatternTags tp = (TextPatternTags) pattern;
            writer.write(TextPattern.NT_TAGS,
                    KEY_NAME, tp.getElementName(),
                    KEY_ATTRIBUTES, nullIfEmpty(tp.getAttributes()),
                    KEY_ADJUST, nullIf(tp.getAdjust().toString(), "full_tag"),
                    KEY_CAPTURE, nullIfEmpty(tp.getCaptureAs()));
        });

        // Term
        jsonSerializers.put(TextPatternTerm.class, (pattern, writer) -> {
            TextPatternTerm tp = (TextPatternTerm) pattern;
            writer.write(TextPattern.NT_TERM,
                    KEY_VALUE, tp.getValue(),
                    KEY_ANNOTATION, tp.getAnnotation(),    // (omitted if null)
                    KEY_SENSITIVITY, sensitivity(tp.getSensitivity())); // (omitted if null)
        });

        // MatchFilterAnd
        jsonSerializers.put(MatchFilterAnd.class, (pattern, writer) -> {
            MatchFilterAnd tp = (MatchFilterAnd) pattern;
            writer.write(MatchFilter.NT_AND, KEY_CLAUSES, tp.getClauses());
        });

        // MatchFilterCompare
        jsonSerializers.put(MatchFilterCompare.class, (pattern, writer) -> {
            MatchFilterCompare tp = (MatchFilterCompare) pattern;
            writer.write(MatchFilter.NT_COMPARE,
                    KEY_CLAUSES, tp.getClauses(),
                    KEY_OPERATION, tp.getOperator().toString(),
                    KEY_SENSITIVITY, sensitivity(tp.getSensitivity()));
        });

        // MatchFilterEquals
        jsonSerializers.put(MatchFilterEquals.class, (pattern, writer) -> {
            MatchFilterEquals tp = (MatchFilterEquals) pattern;
            writer.write(MatchFilter.NT_EQUALS,
                    KEY_CLAUSES, tp.getClauses(),
                    KEY_SENSITIVITY, sensitivity(tp.getSensitivity()));
        });

        // MatchFilterFunctionCall
        jsonSerializers.put(MatchFilterFunctionCall.class, (pattern, writer) -> {
            MatchFilterFunctionCall tp = (MatchFilterFunctionCall) pattern;
            writer.write(MatchFilter.NT_CALLFUNC,
                    KEY_NAME, tp.getName(),
                    KEY_CAPTURE, tp.getCapture());
        });

        // MatchFilterImplication
        jsonSerializers.put(MatchFilterImplication.class, (pattern, writer) -> {
            MatchFilterImplication tp = (MatchFilterImplication) pattern;
            writer.write(MatchFilter.NT_IMPLICATION, KEY_CLAUSES, tp.getClauses());
        });

        // MatchFilterNot
        jsonSerializers.put(MatchFilterNot.class, (pattern, writer) -> {
            MatchFilterNot tp = (MatchFilterNot) pattern;
            writer.write(MatchFilter.NT_NOT, KEY_CLAUSE, tp.getClause());
        });

        // MatchFilterOr
        jsonSerializers.put(MatchFilterOr.class, (pattern, writer) -> {
            MatchFilterOr tp = (MatchFilterOr) pattern;
            writer.write(MatchFilter.NT_OR, KEY_CLAUSES, tp.getClauses());
        });

        // MatchFilterString
        jsonSerializers.put(MatchFilterString.class, (pattern, writer) -> {
            MatchFilterString tp = (MatchFilterString) pattern;
            writer.write(MatchFilter.NT_STRING, KEY_VALUE, tp.getValue());
        });

        // MatchFilterSameTokens
        jsonSerializers.put(MatchFilterSameTokens.class, (pattern, writer) -> {
            MatchFilterSameTokens tp = (MatchFilterSameTokens) pattern;
            writer.write(MatchFilter.NT_TOKEN_ANNOTATION_EQUAL,
                    KEY_CAPTURES, tp.getCaptures(),
                    KEY_ANNOTATION, tp.getAnnotation(),
                    KEY_SENSITIVITY, sensitivity(tp.getSensitivity()));
        });

        // MatchFilterTokenAnnotation
        jsonSerializers.put(MatchFilterTokenAnnotation.class, (pattern, writer) -> {
            MatchFilterTokenAnnotation tp = (MatchFilterTokenAnnotation) pattern;
            writer.write(MatchFilter.NT_TOKEN_ANNOTATION,
                    KEY_CAPTURE, tp.getCapture(),
                    KEY_ANNOTATION, tp.getAnnotation());
        });

        // MatchFilterTokenAnnotationEqualsString
        jsonSerializers.put(MatchFilterTokenAnnotationEqualsString.class, (pattern, writer) -> {
            MatchFilterTokenAnnotationEqualsString tp = (MatchFilterTokenAnnotationEqualsString) pattern;
            writer.write(MatchFilter.NT_TOKEN_ANNOTATION_STRING,
                    KEY_CAPTURE, tp.getCapture(),
                    KEY_ANNOTATION, tp.getAnnotation(),
                    KEY_VALUE, tp.getValue(),
                    KEY_SENSITIVITY, sensitivity(tp.getSensitivity()));
        });
    }

    private static String sensitivity(MatchSensitivity sensitivity) {
        if (sensitivity == null)
            return null;
        return sensitivity.luceneFieldSuffix();
    }

    private static String nullIfEmpty(String str) {
        return str == null || str.isEmpty() ? null : str;
    }

    private static <K,V> Map<K, V> nullIfEmpty(Map<K, V> attributes) {
        return attributes.isEmpty() ? null : attributes;
    }

    private static <T> T nullIf(T max, T value) {
        return max.equals(value) ? null : max;
    }

    private static Integer nullIfUnlimited(int max) {
        return max == MAX_UNLIMITED ? null : max;
    }

    public static void serialize(TextPatternStruct pattern, ObjectSerializationWriter writer) {
        NodeSerializer serializer = jsonSerializers.get(pattern.getClass());
        if (serializer == null)
            throw new UnsupportedOperationException("Unable to serialize TextPattern type: " + pattern.getClass().getName());
        serializer.serialize(pattern, writer);
    }

    public static TextPatternStruct deserialize(String nodeType, Map<String, Object> args) {
        switch (nodeType) {
        case TextPattern.NT_AND:
            return new TextPatternAnd((List<TextPattern>) args.get(KEY_CLAUSES));
        case TextPattern.NT_ANDNOT:
            return new TextPatternAndNot(
                    (List<TextPattern>) args.get(KEY_INCLUDE),
                    (List<TextPattern>) args.get(KEY_EXCLUDE));
        case TextPattern.NT_ANYTOKEN:
            return new TextPatternAnyToken((int)args.get(KEY_MIN), (int)args.getOrDefault(KEY_MAX, MAX_UNLIMITED));
        case TextPattern.NT_CAPTURE:
            return new TextPatternCaptureGroup(
                    (TextPattern) args.get(KEY_CLAUSE),
                    (String) args.get(KEY_CAPTURE));
        case TextPattern.NT_CONSTRAINED:
            return new TextPatternConstrained(
                    (TextPattern) args.get(KEY_CLAUSE),
                    (MatchFilter) args.get(KEY_CONSTRAINT));
        case TextPattern.NT_DEFVAL:
            return TextPatternDefaultValue.get();
        case TextPattern.NT_LOOK:
            return new TextPatternLook(
                    (TextPattern) args.get(KEY_CLAUSE),
                    !((boolean) args.getOrDefault(KEY_WHERE, "ahead").equals("behind")),
                    (boolean)args.getOrDefault(KEY_NEGATE, false));
        case TextPattern.NT_EXPANSION:
            return new TextPatternExpansion(
                    (TextPattern) args.get(KEY_CLAUSE),
                    (SpanQueryExpansion.Direction) args.get(KEY_DIRECTION),
                    (int) args.get(KEY_MIN),
                    (int) args.getOrDefault(KEY_MAX, MAX_UNLIMITED));
        case TextPattern.NT_FILTERNGRAMS:
            return new TextPatternFilterNGrams(
                    (TextPattern) args.get(KEY_CLAUSE),
                    SpanQueryPositionFilter.Operation.fromStringValue((String)args.get(KEY_OPERATION)),
                    (int) args.get(KEY_MIN),
                    (int) args.getOrDefault(KEY_MAX, MAX_UNLIMITED));
        case TextPattern.NT_FIXEDSPAN:
            return new TextPatternFixedSpan(
                    (int) args.get(KEY_START),
                    (int) args.get(KEY_END));
        case TextPattern.NT_FUZZY:
            throw new UnsupportedOperationException("Cannot deserialize deprecated TextPatternFuzzy");
        case TextPattern.NT_INT_RANGE:
            return new TextPatternIntRange(
                    (int) args.get(KEY_MIN),
                    (int) args.get(KEY_MAX),
                    (String) args.get(KEY_ANNOTATION));
        case TextPattern.NT_NOT:
            return new TextPatternNot((TextPattern) args.get(KEY_CLAUSE));
        case TextPattern.NT_OR:
            return new TextPatternOr((List<TextPattern>) args.get(KEY_CLAUSES));
        case TextPattern.NT_POSFILTER:
            return new TextPatternPositionFilter(
                    (TextPattern) args.get(KEY_PRODUCER),
                    (TextPattern) args.get(KEY_FILTER),
                    SpanQueryPositionFilter.Operation.fromStringValue((String)args.get(KEY_OPERATION)),
                    (boolean) args.getOrDefault(KEY_INVERT, false),
                    (int) args.getOrDefault(KEY_ADJUST_LEADING, 0),
                    (int) args.getOrDefault(KEY_ADJUST_TRAILING, 0));
        case TextPattern.NT_OVERLAPPING: {
            List<TextPattern> clauses = (List<TextPattern>)args.get(KEY_CLAUSES);
            return new TextPatternOverlapping(
                    clauses.get(0),
                    clauses.get(1),
                    (String) args.get(KEY_OPERATION)
            );
        }
        case TextPattern.NT_PREFIX:
            return new TextPatternPrefix(
                    (String) args.get(KEY_VALUE),
                    (String) args.get(KEY_ANNOTATION),
                    optArgSensitivity(args));
        case TextPattern.NT_CALLFUNC:
            return new TextPatternQueryFunction(
                    (String) args.get(KEY_NAME),
                    (List<TextPattern>) args.get(KEY_ARGS));
        case TextPattern.NT_REGEX:
            return new TextPatternRegex(
                    (String) args.get(KEY_VALUE),
                    (String) args.get(KEY_ANNOTATION),
                    optArgSensitivity(args));
        case TextPattern.NT_RELATION_MATCH:
            return new TextPatternRelationMatch(
                    (TextPattern) args.get(KEY_PARENT),
                    (List<RelationTarget>) args.get(KEY_CHILDREN));
        case TextPattern.NT_RELATION_TARGET:
            RelationOperatorInfo relOpInfo = new RelationOperatorInfo(
                    (String) args.get(KEY_REL_TYPE),
                    SpanQueryRelations.Direction.fromCode((String)args.getOrDefault(KEY_DIRECTION, "both")),
                    (String) args.get(KEY_TARGET_VERSION),
                    (boolean) args.getOrDefault(KEY_NEGATE, false),
                    (boolean) args.getOrDefault(KEY_ALIGNMENT, false),
                    (boolean) args.getOrDefault(KEY_OPTIONAL, false));
            return new RelationTarget(
                    relOpInfo,
                    (TextPattern) args.get(KEY_CLAUSE),
                    RelationInfo.SpanMode.fromCode((String)args.getOrDefault(KEY_REL_SPAN_MODE, "source")),
                    (String) args.get(KEY_CAPTURE));
        case TextPattern.NT_REPEAT:
            return TextPatternRepetition.get(
                    (TextPattern) args.get(KEY_CLAUSE),
                    (int) args.get(KEY_MIN),
                    (int) args.getOrDefault(KEY_MAX, MAX_UNLIMITED));
        case TextPattern.NT_SENSITIVITY:
            throw new UnsupportedOperationException("Cannot deserialize deprecated TextPatternSensitive");
        case TextPattern.NT_SEQUENCE:
            return new TextPatternSequence((List<TextPattern>) args.get(KEY_CLAUSES));
        case TextPattern.NT_TAGS:
            return new TextPatternTags(
                    (String) args.get(KEY_NAME),
                     (Map<String, MatchValue>)args.get(KEY_ATTRIBUTES),
                    optArgAdjust(args),
                    (String) args.get(KEY_CAPTURE));
        case TextPattern.NT_TERM:
            return new TextPatternTerm(
                    (String) args.get(KEY_VALUE),
                    (String) args.get(KEY_ANNOTATION),
                    optArgSensitivity(args));
        case TextPattern.NT_WILDCARD:
            throw new UnsupportedOperationException("Cannot deserialize deprecated TextPatternWildcard");
        case MatchFilter.NT_AND: {
            List<MatchFilter> clauses = (List<MatchFilter>) args.get(KEY_CLAUSES);
            assert clauses.size() == 2;
            return new MatchFilterAnd(clauses.get(0), clauses.get(1));
            }
        case MatchFilter.NT_COMPARE: {
            List<MatchFilter> clauses = (List<MatchFilter>) args.get(KEY_CLAUSES);
            assert clauses.size() == 2;
            return new MatchFilterCompare(
                    clauses.get(0), clauses.get(1),
                    MatchFilterCompare.Operator.fromSymbol((String) args.get(KEY_OPERATION)),
                    optArgSensitivity(args));
            }
        case MatchFilter.NT_EQUALS: {
            List<MatchFilter> clauses = (List<MatchFilter>) args.get(KEY_CLAUSES);
            assert clauses.size() == 2;
            return new MatchFilterEquals(
                    clauses.get(0), clauses.get(1),
                    optArgSensitivity(args));
            }
        case MatchFilter.NT_CALLFUNC:
            return new MatchFilterFunctionCall(
                    (String) args.get(KEY_NAME),
                    (String) args.get(KEY_CAPTURE));
        case MatchFilter.NT_IMPLICATION: {
            List<MatchFilter> clauses = (List<MatchFilter>) args.get(KEY_CLAUSES);
            assert clauses.size() == 2;
            return new MatchFilterImplication(clauses.get(0), clauses.get(1));
            }
        case MatchFilter.NT_NOT:
            return new MatchFilterNot((MatchFilter) args.get(KEY_CLAUSE));
        case MatchFilter.NT_OR: {
            List<MatchFilter> clauses = (List<MatchFilter>) args.get(KEY_CLAUSES);
            assert clauses.size() == 2;
            return new MatchFilterOr(clauses.get(0), clauses.get(1));
            }
        case MatchFilter.NT_STRING:
            return new MatchFilterString((String) args.get(KEY_VALUE));
        case MatchFilter.NT_TOKEN_ANNOTATION_EQUAL: {
            List<String> captures = (List<String>) args.get(KEY_CAPTURES);
            assert captures.size() == 2;
            return new MatchFilterSameTokens(
                    captures.get(0),
                    captures.get(1),
                    (String) args.get(KEY_ANNOTATION),
                    optArgSensitivity(args));
            }
        case MatchFilter.NT_TOKEN_ANNOTATION:
            return new MatchFilterTokenAnnotation(
                    (String) args.get(KEY_CAPTURE),
                    (String) args.get(KEY_ANNOTATION));
        case MatchFilter.NT_TOKEN_ANNOTATION_STRING:
            return new MatchFilterTokenAnnotationEqualsString(
                    (String) args.get(KEY_CAPTURE),
                    (String) args.get(KEY_ANNOTATION),
                    (String) args.get(KEY_VALUE),
                    optArgSensitivity(args));
        default:
            throw new UnsupportedOperationException("Unable to deserialize TextPattern type: " + nodeType);
        }
    }

    private static TextPatternTags.Adjust optArgAdjust(Map<String, Object> args) {
        String adjust = (String) args.get(KEY_ADJUST);
        return adjust == null ? null : TextPatternTags.Adjust.fromString(adjust);
    }

    private static MatchSensitivity optArgSensitivity(Map<String, Object> args) {
        String sensitivity = (String) args.get(KEY_SENSITIVITY);
        return sensitivity == null ? null : MatchSensitivity.fromLuceneFieldSuffix(sensitivity);
    }
}
